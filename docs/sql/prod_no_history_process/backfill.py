#!/usr/bin/env python3
"""从 TDengine 过程跟踪历史生成逐次生产记录，并在复核后写入 PostgreSQL。

扫描阶段只读取 digital_coil 中六张指定过程表，使用本地 SQLite 聚合跨时间窗口的结果；
写入阶段只读取扫描产物和 PostgreSQL，不会重新推断生产序号。
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
import re
import sqlite3
import sys
import threading
import time
import unicodedata
from collections import defaultdict
from contextlib import closing, contextmanager
from datetime import datetime, timedelta, timezone
from pathlib import Path
from zoneinfo import ZoneInfo

SHANGHAI = ZoneInfo("Asia/Shanghai")
DATABASE = "digital_coil"
PREVIEW_VERSION = 3
SEGMENT_GAP_SECONDS = 3600
PROGRESS_INTERVAL_SECONDS = 30
SOURCE_COLUMNS = ("in_mat_prod_no", "repeat_prod_no")
IDENTIFIER = re.compile(r"^[a-z][a-z0-9_]*$")
INVALID_PREFIX = re.compile(r"^request\s+color\b", re.IGNORECASE)
USER_ID = 1831666618627928065

# 每台机组只使用现场指定的一张过程表作为序号证据；不扫描其他工艺段。
PROCESS_TABLES = {
    "CBL1": "cbl1_process_default",
    "CP1": "cp1_process_nof",
    "CSL1": "csl1_process_default",
    "DCL1": "dcl1_process_sf3",
    "FCL1": "fcl1_process_ctf",
    "ZRM1": "zrm1_process_default",
}

# 本地运行前填写连接信息；生产账号和密码不得提交到 Git。
# 扫描阶段只使用 TD_DSN，--apply 阶段只使用 PG_DSN。
TD_DSN = "ws://<user>:<password>@<host>:6041"
PG_DSN = "postgresql://<user>:<password>@<host>:5432/<database>"

# 这些对象由当前 status 配置及 Cube 元数据核对得出；启动时还会逐列验证。
# 只读子表，避免同一超表含多个子表时重复统计。
CUBE_TABLES = {
    "CBL1": "cbl1_plc_whole_tracking_sub",
    "CP1": "cp1_plc5_sg_whole_tracking_sub",
    "CSL1": "csl1_plc_reroll_tracking_sub",
    "DCL1": "dcl1_plc5_sg_whole_tracking_sub",
    "FCL1": "fcl1_plc5_sg_whole_tracking_sub",
    "ZRM1": "zrm1_plc_mim_public_sub",
}


def normalize_coil(value: object) -> str | None:
    """与 StatusTrackingAlgorithmImpl 的卷号过滤规则保持一致。"""
    if value is None:
        return None
    coil = str(value)
    while coil and (coil[0].isspace() or unicodedata.category(coil[0]) in ("Cf", "Cc")):
        coil = coil[1:]
    while coil and (coil[-1].isspace() or unicodedata.category(coil[-1]) in ("Cf", "Cc")):
        coil = coil[:-1]
    if not coil or INVALID_PREFIX.match(coil) or set(coil) == {"."}:
        return None
    return coil


def local_time(value: object) -> datetime:
    """统一为上海本地无时区时间，以匹配目标表 timestamp(6)。"""
    if isinstance(value, int):
        # TDengine CAST(ts AS BIGINT) 返回数据库毫秒精度的 Unix 时间。
        seconds, milliseconds = divmod(value, 1000)
        parsed = datetime.fromtimestamp(seconds, tz=timezone.utc).replace(
            microsecond=milliseconds * 1000
        )
    elif isinstance(value, datetime):
        parsed = value
    else:
        parsed = datetime.fromisoformat(str(value).replace("Z", "+00:00"))
    if parsed.tzinfo is not None:
        parsed = parsed.astimezone(SHANGHAI).replace(tzinfo=None)
    return parsed


def timestamp_text(value: object) -> str:
    return local_time(value).isoformat(sep=" ", timespec="microseconds")


def epoch_ms(value: datetime) -> int:
    """以上海时区解释本地边界，生成不受客户端显示时区影响的查询条件。"""
    aware = value.replace(tzinfo=SHANGHAI)
    return int(aware.timestamp() * 1000)


def quote_td(identifier: str) -> str:
    """仅允许元数据中符合预期的标识符进入 TDengine SQL。"""
    if not IDENTIFIER.fullmatch(identifier):
        raise ValueError(f"TDengine 标识符无效: {identifier!r}")
    return f"`{identifier}`"


def query(connection, sql: str, size: int = 1000):
    """逐批消费查询结果，不把整张时序表载入内存。"""
    cursor = connection.cursor()
    try:
        cursor.execute(sql)
        while True:
            batch = cursor.fetchmany(size)
            if not batch:
                break
            yield from batch
    finally:
        cursor.close()


def log_progress(message: str):
    """将不含连接信息和业务值的扫描状态立即写到标准错误。"""
    now = datetime.now(SHANGHAI).strftime("%Y-%m-%d %H:%M:%S")
    print(f"[{now}] {message}", file=sys.stderr, flush=True)


@contextmanager
def progress_heartbeat(label: str, status: dict[str, object],
                       interval_seconds: float = PROGRESS_INTERVAL_SECONDS):
    """查询阻塞时仍定期报告当前窗口和阶段；退出时停止后台计时线程。"""
    started = time.monotonic()
    stopped = threading.Event()

    def report_while_waiting():
        while not stopped.wait(interval_seconds):
            log_progress(f"{label} 仍在运行，耗时 {time.monotonic() - started:.0f} 秒；"
                         f"阶段={status['phase']}，停采段={status['sessions_done']}/"
                         f"{status['sessions_total']}")

    worker = threading.Thread(target=report_while_waiting, daemon=True)
    log_progress(f"{label} 开始")
    worker.start()
    try:
        yield
    except Exception as error:
        log_progress(f"{label} 失败，耗时 {time.monotonic() - started:.1f} 秒；"
                     f"阶段={status['phase']}，异常类型={type(error).__name__}")
        raise
    else:
        log_progress(f"{label} 完成，耗时 {time.monotonic() - started:.1f} 秒；"
                     f"停采段={status['sessions_done']}，卷号段={status['state_windows']}，"
                     f"序号细分查询={status['sequence_queries']}，采样数={status['samples']}")
    finally:
        stopped.set()
        worker.join(timeout=1)


def table_columns(connection, database: str) -> dict[str, dict[str, tuple[str, str]]]:
    # 只允许读取固定的过程数据库，避免动态拼接任意 SQL 文本。
    if database != DATABASE:
        raise ValueError(f"未允许读取的 TDengine 数据库: {database}")
    sql = (
        "SELECT table_name, table_type, col_name, col_type "
        "FROM information_schema.ins_columns "
        f"WHERE db_name = '{database}'"
    )
    found: dict[str, dict[str, tuple[str, str]]] = defaultdict(dict)
    for name, kind, column, column_type in query(connection, sql):
        found[str(name)][str(column)] = (str(kind), str(column_type))
    return found


def validate_precision(connection):
    """CAST(ts AS BIGINT) 的单位取决于数据库精度，只支持毫秒库。"""
    sql = (
        "SELECT name, `precision` FROM information_schema.ins_databases "
        "WHERE name = 'digital_coil'"
    )
    precisions = {str(name): str(precision) for name, precision in query(connection, sql)}
    if precisions != {DATABASE: "ms"}:
        raise ValueError(f"TDengine digital_coil 时间精度必须为 ms: {precisions}")


def configured_process_tables(connection) -> list[tuple[str, str, str]]:
    """仅校验并返回各机组指定的过程表，不从元数据扩展扫描范围。"""
    found = table_columns(connection, DATABASE)
    result = []
    for unit, name in PROCESS_TABLES.items():
        columns = found.get(name, {})
        if columns.get("ts", (None,))[0] != "NORMAL_TABLE":
            raise ValueError(f"过程表不存在或不是普通表: {unit}/{name}")
        if "coil_no" not in columns:
            raise ValueError(f"过程表缺少 coil_no: {name}")
        numbers = [column for column in SOURCE_COLUMNS if column in columns]
        if len(numbers) != 1:
            raise ValueError(f"过程表序号列必须恰有一个: {name}, 实际={numbers}")
        if columns[numbers[0]][1].upper() not in ("INT", "INT UNSIGNED"):
            raise ValueError(f"过程表序号列不是 INT: {name}.{numbers[0]}")
        result.append((name, unit, numbers[0]))
    return result


def create_stage(path: Path) -> sqlite3.Connection:
    if path.exists():
        path.unlink()
    database = sqlite3.connect(path)
    database.executescript(
        """
        CREATE TABLE evidence (
            unit TEXT NOT NULL, coil TEXT NOT NULL, seq INTEGER NOT NULL,
            first_ts TEXT NOT NULL, last_ts TEXT NOT NULL,
            PRIMARY KEY (unit, coil, seq)
        );
        CREATE TABLE source (
            unit TEXT NOT NULL, coil TEXT NOT NULL, seq INTEGER NOT NULL,
            table_name TEXT NOT NULL,
            PRIMARY KEY (unit, coil, seq, table_name)
        );
        CREATE TABLE segment (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            unit TEXT NOT NULL, coil TEXT NOT NULL, seq INTEGER NOT NULL,
            start_ts TEXT NOT NULL, end_ts TEXT NOT NULL,
            sample_count INTEGER NOT NULL, table_name TEXT NOT NULL
        );
        CREATE INDEX idx_segment_key ON segment(unit, coil, seq, start_ts);
        CREATE TABLE anomaly (
            unit TEXT NOT NULL, coil TEXT NOT NULL, reason TEXT NOT NULL,
            detail TEXT NOT NULL
        );
        """
    )
    return database


def windows(start: datetime, end: datetime, days: int):
    """生成无重叠的左闭右开时间窗口。"""
    current = start
    while current < end:
        following = min(current + timedelta(days=days), end)
        yield current, following
        current = following


def parse_shanghai_start(value: str) -> datetime:
    """解析用户提供的上海时间扫描起点；允许日期或无时区的本地时间。"""
    try:
        parsed = datetime.fromisoformat(value)
    except ValueError as error:
        raise argparse.ArgumentTypeError("--start 应为 YYYY-MM-DD 或 YYYY-MM-DD HH:MM:SS") from error
    if parsed.tzinfo is not None:
        raise argparse.ArgumentTypeError("--start 请填写不带时区的上海本地时间")
    return parsed


def td_bounds(connection, database: str, table: str) -> datetime | None:
    if database != DATABASE:
        raise ValueError(f"未允许读取的 TDengine 数据库: {database}")
    sql = (
        f"SELECT MIN(CAST(ts AS BIGINT)), MAX(CAST(ts AS BIGINT)) "
        f"FROM {database}.{quote_td(table)}"
    )
    rows = list(query(connection, sql))
    row = rows[0] if rows else None
    return None if row is None or row[0] is None else local_time(row[0])


def record_process_segment(stage: sqlite3.Connection, table: str, unit: str,
                           raw_coil, raw_seq, first_ms, last_ms, sample_count,
                           previous_segment):
    """记录连续采样段；仅相邻同键且间隔不超过一小时的窗口边界可合并。"""
    coil = normalize_coil(raw_coil)
    if coil is None:
        return None
    try:
        seq = int(raw_seq)
    except (TypeError, ValueError):
        seq = 0
    if seq <= 0 or str(seq) != str(raw_seq).strip():
        stage.execute("INSERT INTO anomaly VALUES (?, ?, ?, ?)",
                      (unit, coil, "invalid_sequence", f"{table}: {raw_seq!r}"))
        return None
    first, last = timestamp_text(first_ms), timestamp_text(last_ms)
    same_key = previous_segment is not None and previous_segment[1:3] == (coil, seq)
    gap_seconds = None
    if same_key:
        gap_seconds = (datetime.fromisoformat(first) -
                       datetime.fromisoformat(previous_segment[3])).total_seconds()
    if same_key and 0 <= gap_seconds <= SEGMENT_GAP_SECONDS:
        stage.execute("UPDATE segment SET end_ts=?, sample_count=sample_count+? WHERE id=?",
                      (last, int(sample_count), previous_segment[0]))
        current_segment = (previous_segment[0], coil, seq, last)
    else:
        cursor = stage.execute(
            "INSERT INTO segment(unit, coil, seq, start_ts, end_ts, sample_count, table_name) "
            "VALUES (?, ?, ?, ?, ?, ?, ?)",
            (unit, coil, seq, first, last, int(sample_count), table))
        current_segment = (cursor.lastrowid, coil, seq, last)
    stage.execute(
        """INSERT INTO evidence VALUES (?, ?, ?, ?, ?)
        ON CONFLICT(unit, coil, seq) DO UPDATE SET
        first_ts = min(first_ts, excluded.first_ts),
        last_ts = max(last_ts, excluded.last_ts)""",
        (unit, coil, seq, first, last))
    stage.execute("INSERT OR IGNORE INTO source VALUES (?, ?, ?, ?)",
                  (unit, coil, seq, table))
    return current_segment


def scan_process(connection, stage: sqlite3.Connection, tables, cutoff: datetime, days: int,
                 start_at: datetime | None = None):
    """由 TDengine 按停采间隔、卷号和必要时的序号聚合，避免传输逐点采样。"""
    log_progress(f"开始扫描 {len(tables)} 张过程表，截止上海时间 {cutoff:%Y-%m-%d %H:%M:%S}")
    for table_index, (table, unit, column) in enumerate(tables, start=1):
        # 指定起点时直接按范围查询，避免再对整张表求最早时间。
        if start_at is None:
            log_progress(f"[{table_index}/{len(tables)}] {unit}/{table} 查询最早采样时间")
        first = start_at if start_at is not None else td_bounds(connection, DATABASE, table)
        if first is None:
            log_progress(f"[{table_index}/{len(tables)}] {unit}/{table} 无采样数据，跳过")
            continue
        window_count = max(0, math.ceil((cutoff - first).total_seconds() / (days * 86400)))
        log_progress(f"[{table_index}/{len(tables)}] {unit}/{table} 时间范围 "
                     f"{first:%Y-%m-%d %H:%M:%S} 至 {cutoff:%Y-%m-%d %H:%M:%S}，"
                     f"共 {window_count} 个窗口")
        previous_segment = None
        for window_index, (start, end) in enumerate(windows(first, cutoff, days), start=1):
            label = (f"[{table_index}/{len(tables)}] {unit}/{table} "
                     f"窗口 {window_index}/{window_count} "
                     f"[{start:%Y-%m-%d %H:%M:%S}, {end:%Y-%m-%d %H:%M:%S})")
            status = {"phase": "查询停采段", "sessions_total": "?", "sessions_done": 0,
                      "state_windows": 0, "sequence_queries": 0, "samples": 0}
            source = f"{DATABASE}.{quote_td(table)}"
            sequence = quote_td(column)
            conditions = (f"ts >= {epoch_ms(start)} AND ts < {epoch_ms(end)} "
                          f"AND coil_no IS NOT NULL AND {sequence} IS NOT NULL")
            session_sql = (
                "SELECT CAST(_wstart AS BIGINT), CAST(_wend AS BIGINT), COUNT(*) "
                f"FROM {source} WHERE {conditions} "
                f"SESSION(ts, {SEGMENT_GAP_SECONDS}s) ORDER BY _wstart"
            )
            with progress_heartbeat(label, status):
                # 先按停采间隔分段，避免同一状态窗口内部的长时间无数据被合并。
                sessions = list(query(connection, session_sql))
                status["sessions_total"] = len(sessions)
                for session_index, (session_first, session_last, session_count) in enumerate(sessions, start=1):
                    status["phase"] = "查询卷号段"
                    state_sql = (
                        "SELECT CAST(_wstart AS BIGINT), CAST(_wend AS BIGINT), "
                        f"COUNT(*), FIRST(coil_no), MIN({sequence}), MAX({sequence}) "
                        f"FROM {source} WHERE ts >= {int(session_first)} "
                        f"AND ts <= {int(session_last)} AND coil_no IS NOT NULL "
                        f"AND {sequence} IS NOT NULL STATE_WINDOW(coil_no) ORDER BY _wstart"
                    )
                    # 关闭游标后再执行细分查询；聚合采样数用于发现窗口边界遗漏。
                    state_rows = list(query(connection, state_sql))
                    if sum(int(row[2]) for row in state_rows) != int(session_count):
                        raise ValueError(f"过程表停采段采样数不一致: {table}")
                    status["state_windows"] += len(state_rows)
                    status["samples"] += int(session_count)
                    for first_ms, last_ms, count, coil, min_seq, max_seq in state_rows:
                        if min_seq == max_seq:
                            previous_segment = record_process_segment(
                                stage, table, unit, coil, min_seq, first_ms, last_ms,
                                count, previous_segment)
                            continue
                        # TDengine 3.3 不支持双字段状态窗口，仅对混合序号的卷号段细分。
                        status["phase"] = "查询序号段"
                        status["sequence_queries"] += 1
                        detail_sql = (
                            "SELECT CAST(_wstart AS BIGINT), CAST(_wend AS BIGINT), "
                            f"COUNT(*), FIRST(coil_no), FIRST({sequence}) "
                            f"FROM {source} WHERE ts >= {int(first_ms)} AND ts <= {int(last_ms)} "
                            f"AND coil_no IS NOT NULL AND {sequence} IS NOT NULL "
                            f"STATE_WINDOW({sequence}) ORDER BY _wstart"
                        )
                        detail_count = 0
                        for part_first, part_last, part_count, part_coil, part_seq in query(connection, detail_sql):
                            detail_count += int(part_count)
                            if part_coil != coil:
                                raise ValueError(f"过程表卷号段细分不一致: {table}")
                            previous_segment = record_process_segment(
                                stage, table, unit, part_coil, part_seq, part_first,
                                part_last, part_count, previous_segment)
                        if detail_count != int(count):
                            raise ValueError(f"过程表序号段采样数不一致: {table}/{coil}")
                    status["sessions_done"] = session_index
                status["phase"] = "保存本地聚合"
                stage.commit()
        log_progress(f"[{table_index}/{len(tables)}] {unit}/{table} 扫描完成")


def write_csv(path: Path, header: list[str], rows):
    with path.open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.writer(stream)
        writer.writerow(header)
        writer.writerows(rows)


def finalize(stage: sqlite3.Connection, output: Path, cutoff: datetime, tables,
             start_at: datetime | None = None):
    """检测序号冲突，导出每个合成结果的连续采样区间及候选。"""
    blocked: set[tuple[str, str]] = set()
    for unit, coil, reason, detail in stage.execute("SELECT * FROM anomaly"):
        blocked.add((unit, coil))
    grouped: dict[tuple[str, str], list[tuple[int, str]]] = defaultdict(list)
    for unit, coil, seq, first_ts in stage.execute(
        "SELECT unit, coil, seq, first_ts FROM evidence ORDER BY unit, coil, seq"
    ):
        grouped[(unit, coil)].append((seq, first_ts))
    issues = list(stage.execute("SELECT * FROM anomaly"))
    for key, values in grouped.items():
        seqs = [seq for seq, _ in values]
        if seqs != list(range(1, max(seqs) + 1)):
            blocked.add(key)
            issues.append((*key, "sequence_gap", json.dumps(seqs)))
        times = [instant for _, instant in values]
        if times != sorted(times):
            blocked.add(key)
            issues.append((*key, "sequence_time_order", json.dumps(values, ensure_ascii=False)))
        # 相邻序号的采样区间重叠，说明同一过程表报告了冲突的序号。
        spans = list(stage.execute(
            "SELECT seq, first_ts, last_ts FROM evidence "
            "WHERE unit=? AND coil=? ORDER BY seq", key
        ))
        for earlier, later in zip(spans, spans[1:]):
            if earlier[2] >= later[1]:
                blocked.add(key)
                issues.append((*key, "sequence_overlap",
                               f"{earlier[0]} ends {earlier[2]}, {later[0]} begins {later[1]}"))
    segment_counts = defaultdict(int)
    segment_rows = []
    for unit, coil, seq, first, last, samples, table in stage.execute(
        "SELECT unit, coil, seq, start_ts, end_ts, sample_count, table_name "
        "FROM segment ORDER BY unit, coil, seq, start_ts"
    ):
        key = (unit, coil, seq)
        segment_counts[key] += 1
        segment_rows.append((unit, coil, seq, segment_counts[key], first, last, samples, table))
    if any((unit, coil, seq) not in segment_counts
           for (unit, coil), values in grouped.items() for seq, _ in values):
        raise ValueError("存在缺少时间分段的过程候选")
    write_csv(output / "segments.csv",
              ["unit_code", "in_mat_no", "in_mat_repeat_prod_no", "segment_no",
               "start_ts", "end_ts", "sample_count", "source_table"], segment_rows)
    write_csv(output / "anomalies.csv", ["unit_code", "in_mat_no", "reason", "detail"], issues)
    candidates = []
    for (unit, coil), values in sorted(grouped.items()):
        if (unit, coil) in blocked:
            continue
        for seq, instant in values:
            candidates.append((unit, coil, seq, instant, segment_counts[(unit, coil, seq)]))
    write_csv(
        output / "candidates.csv",
        ["unit_code", "in_mat_no", "in_mat_repeat_prod_no", "create_time", "segment_count"],
        candidates,
    )
    manifest = {
        "preview_version": PREVIEW_VERSION,
        "start_shanghai": timestamp_text(start_at) if start_at is not None else None,
        "cutoff_shanghai": timestamp_text(cutoff),
        "process_tables": [table for table, _, _ in tables],
        "candidate_count": len(candidates),
        "segment_count": len(segment_rows),
        "blocked_coils": len(blocked),
        "anomaly_count": len(issues),
        "candidate_sha256": hashlib.sha256((output / "candidates.csv").read_bytes()).hexdigest(),
        "segments_sha256": hashlib.sha256((output / "segments.csv").read_bytes()).hexdigest(),
    }
    (output / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    return manifest


def record_id(unit: str, coil: str, seq: int) -> int:
    """负数稳定 ID 与在线生成的正数雪花 ID 分离；碰撞时写入失败。"""
    key = f"{unit}\0{coil}\0{seq}".encode("utf-8")
    number = int.from_bytes(hashlib.sha256(key).digest()[:8], "big") & ((1 << 63) - 1)
    return -(number or 1)


def pg_table_exists(connection, name: str) -> bool:
    return connection.execute("SELECT to_regclass(%s)", (f"public.{name}",)).fetchone()[0] is not None


def load_candidates(output: Path):
    manifest = json.loads((output / "manifest.json").read_text(encoding="utf-8"))
    if manifest.get("preview_version") != PREVIEW_VERSION:
        raise ValueError("预览产物版本不匹配，请使用当前 process 脚本重新扫描")
    content = (output / "candidates.csv").read_bytes()
    if hashlib.sha256(content).hexdigest() != manifest["candidate_sha256"]:
        raise ValueError("候选文件校验失败，请重新扫描")
    with (output / "candidates.csv").open(encoding="utf-8-sig", newline="") as stream:
        rows = list(csv.DictReader(stream))
    if len(rows) != manifest["candidate_count"]:
        raise ValueError("候选数量与清单不一致")
    segment_content = (output / "segments.csv").read_bytes()
    if hashlib.sha256(segment_content).hexdigest() != manifest["segments_sha256"]:
        raise ValueError("时间分段文件校验失败，请重新扫描")
    with (output / "segments.csv").open(encoding="utf-8-sig", newline="") as stream:
        if sum(1 for _ in csv.DictReader(stream)) != manifest["segment_count"]:
            raise ValueError("时间分段数量与清单不一致")
    return manifest, rows


def apply(output: Path, connection):
    """按卷事务写入；任何冲突保持旧数据并记录异常。"""
    manifest, rows = load_candidates(output)
    if not pg_table_exists(connection, "qm_dc_repeat_prod_no_log"):
        raise ValueError("目标库缺少 public.qm_dc_repeat_prod_no_log")
    has_old = pg_table_exists(connection, "qm_dc_product_no")
    if has_old:
        old_columns = connection.execute(
            "SELECT column_name FROM information_schema.columns "
            "WHERE table_schema='public' AND table_name='qm_dc_product_no'"
        ).fetchall()
        if "in_mat_product_no" not in {row[0] for row in old_columns}:
            raise ValueError("旧计数表缺少 in_mat_product_no")
    cutoff = local_time(manifest["cutoff_shanghai"])
    grouped = defaultdict(list)
    for row in rows:
        grouped[(row["unit_code"], row["in_mat_no"])].append(
            (int(row["in_mat_repeat_prod_no"]), local_time(row["create_time"]))
        )
    results = []
    for (unit, coil), observed in sorted(grouped.items()):
        if unit != unit.upper() or normalize_coil(coil) != coil:
            results.append((unit, coil, "blocked", "业务键无效"))
            continue
        try:
            with connection.transaction():
                existing = connection.execute(
                    """SELECT id, in_mat_repeat_prod_no, create_time, deleted
                    FROM public.qm_dc_repeat_prod_no_log
                    WHERE unit_code=%s AND in_mat_no=%s ORDER BY in_mat_repeat_prod_no
                    FOR UPDATE""",
                    (unit, coil),
                ).fetchall()
                existing_numbers = {int(row[1]) for row in existing}
                candidate_numbers = {seq for seq, _ in observed}
                combined = existing_numbers | candidate_numbers
                if candidate_numbers != set(range(1, max(candidate_numbers) + 1)):
                    raise ValueError("候选文件序号不连续")
                if combined != set(range(1, max(combined) + 1)):
                    raise ValueError("目标表与候选合并后序号不连续")
                if any(row[3] != 0 for row in existing):
                    raise ValueError("目标表存在 deleted 非 0 的记录")
                # 目标表在扫描截止后可能新增在线记录；这些行应保留。
                if any(row[1] > max(candidate_numbers) and
                       (row[2] is None or row[2] < cutoff) for row in existing):
                    raise ValueError("目标表已有比候选更大的历史序号")
                if has_old:
                    old = connection.execute(
                        """SELECT in_mat_product_no FROM public.qm_dc_product_no
                        WHERE upper(unit_code)=%s AND in_mat_no=%s""",
                        (unit, coil),
                    ).fetchall()
                    if len(old) > 1 or (old and old[0][0] is not None
                                        and int(old[0][0]) > max(combined)):
                        raise ValueError("旧计数表最大序号高于明确历史")
                inserted = 0
                for seq, instant in sorted(observed):
                    if seq in existing_numbers:
                        continue
                    identifier = record_id(unit, coil, seq)
                    collision = connection.execute(
                        "SELECT unit_code, in_mat_no, in_mat_repeat_prod_no "
                        "FROM public.qm_dc_repeat_prod_no_log WHERE id=%s",
                        (identifier,),
                    ).fetchone()
                    if collision is not None:
                        raise ValueError(f"回填 ID 冲突: {identifier}")
                    connection.execute(
                        """INSERT INTO public.qm_dc_repeat_prod_no_log
                        (id, unit_code, in_mat_no, in_mat_repeat_prod_no,
                         deleted, create_time, create_user)
                        VALUES (%s, %s, %s, %s, 0, %s, %s)""",
                        (identifier, unit, coil, seq, instant, USER_ID),
                    )
                    inserted += 1
                results.append((unit, coil, "inserted", str(inserted)))
        except Exception as error:
            results.append((unit, coil, "blocked", str(error)))
    write_csv(output / "apply_results.csv", ["unit_code", "in_mat_no", "status", "detail"], results)
    return results


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", type=Path, required=True, help="本地预览产物目录")
    parser.add_argument("--apply", action="store_true", help="读取已有预览产物，写入 PostgreSQL")
    parser.add_argument("--start", type=parse_shanghai_start,
                        help="扫描起点（上海时间），如 2026-09-03 00:00:00；默认各表最早时间")
    parser.add_argument("--window-days", type=int, default=1, help="扫描窗口天数，默认 1")
    options = parser.parse_args(argv)
    if options.apply:
        if options.start is not None:
            parser.error("--apply 只读取已有预览文件，不接受 --start")
        if not PG_DSN or "<" in PG_DSN or ">" in PG_DSN:
            parser.error("请先在脚本顶部填写 PG_DSN")
        import psycopg
        # 自动提交模式下，connection.transaction() 才是独立的单卷事务。
        with psycopg.connect(PG_DSN, autocommit=True) as connection:
            results = apply(options.output_dir, connection)
        print(json.dumps({"inserted_coils": sum(r[2] == "inserted" for r in results),
                          "blocked_coils": sum(r[2] == "blocked" for r in results)},
                         ensure_ascii=False))
        return 1 if any(r[2] == "blocked" for r in results) else 0
    if options.window_days < 1:
        parser.error("--window-days 必须大于 0")
    if not TD_DSN or "<" in TD_DSN or ">" in TD_DSN:
        parser.error("请先在脚本顶部填写 TD_DSN")
    if options.output_dir.exists() and any(options.output_dir.iterdir()):
        parser.error("预览输出目录必须为空，避免覆盖已复核产物")
    options.output_dir.mkdir(parents=True, exist_ok=True)
    import taosws
    cutoff = datetime.now(SHANGHAI).replace(tzinfo=None)
    if options.start is not None and options.start >= cutoff:
        parser.error("--start 必须早于当前上海时间")
    log_progress("连接 TDengine，校验 digital_coil 时间精度和六张过程表")
    with closing(taosws.connect(TD_DSN)) as connection:
        validate_precision(connection)
        tables = configured_process_tables(connection)
        log_progress("连接与表结构校验完成，开始查询历史")
        stage = create_stage(options.output_dir / "stage.sqlite")
        try:
            scan_process(connection, stage, tables, cutoff, options.window_days, options.start)
            log_progress("所有过程表扫描完成，生成候选、时间分段和异常文件")
            manifest = finalize(stage, options.output_dir, cutoff, tables, options.start)
        finally:
            stage.close()
    log_progress(f"预览文件生成完成：候选 {manifest['candidate_count']} 条，"
                 f"时间分段 {manifest['segment_count']} 条，异常 {manifest['anomaly_count']} 条")
    print(json.dumps(manifest, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
