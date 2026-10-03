#!/usr/bin/env python3
"""从 TDengine 过程跟踪历史生成逐次生产记录，并在复核后写入 PostgreSQL。

扫描阶段只读取远端数据库，使用本地 SQLite 聚合跨表、跨时间窗口的结果；
写入阶段只读取扫描产物和 PostgreSQL，不会重新推断生产序号。
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
import sqlite3
import sys
import unicodedata
from collections import defaultdict
from contextlib import closing
from datetime import datetime, timedelta, timezone
from pathlib import Path
from zoneinfo import ZoneInfo

SHANGHAI = ZoneInfo("Asia/Shanghai")
DATABASE = "digital_coil"
SOURCE_COLUMNS = ("in_mat_prod_no", "repeat_prod_no")
IDENTIFIER = re.compile(r"^[a-z][a-z0-9_]*$")
INVALID_PREFIX = re.compile(r"^request\s+color\b", re.IGNORECASE)
USER_ID = 1831666618627928065
ROOT = Path(__file__).resolve().parents[3]

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


def table_columns(connection, database: str) -> dict[str, dict[str, tuple[str, str]]]:
    # 调用方只传固定数据库名，避免动态拼接任意 SQL 文本。
    if database not in (DATABASE, "cube"):
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
    """CAST(ts AS BIGINT) 的单位取决于数据库精度，只支持已核对的毫秒库。"""
    sql = (
        "SELECT name, `precision` FROM information_schema.ins_databases "
        "WHERE name IN ('digital_coil', 'cube')"
    )
    precisions = {str(name): str(precision) for name, precision in query(connection, sql)}
    if precisions != {DATABASE: "ms", "cube": "ms"}:
        raise ValueError(f"TDengine 时间精度必须均为 ms: {precisions}")


def discover_process_tables(connection) -> list[tuple[str, str, str]]:
    """只接纳具备 ts、coil_no 和唯一序号列的普通过程表。"""
    found = table_columns(connection, DATABASE)
    result = []
    for name, columns in sorted(found.items()):
        if "_process_" not in name or columns.get("ts", (None,))[0] != "NORMAL_TABLE":
            continue
        if "coil_no" not in columns:
            raise ValueError(f"过程表缺少 coil_no: {name}")
        numbers = [column for column in SOURCE_COLUMNS if column in columns]
        if len(numbers) != 1:
            raise ValueError(f"过程表序号列必须恰有一个: {name}, 实际={numbers}")
        if columns[numbers[0]][1].upper() not in ("INT", "INT UNSIGNED"):
            raise ValueError(f"过程表序号列不是 INT: {name}.{numbers[0]}")
        unit = name.split("_", 1)[0].upper()
        if unit not in CUBE_TABLES:
            raise ValueError(f"过程表机组未配置 Cube 来源: {name}")
        result.append((name, unit, numbers[0]))
    if not result:
        raise ValueError("未发现可用的过程跟踪普通表")
    return result


def cube_sources(connection, units: set[str]) -> list[tuple[str, str, list[str]]]:
    """卷号字段从现有 status 配置读取，Cube 表名与列由元数据校验。"""
    metadata = table_columns(connection, "cube")
    sources = []
    for unit in sorted(units):
        config_path = ROOT / "docs" / "config" / unit / "status.json"
        config = json.loads(config_path.read_text(encoding="utf-8"))
        fields = [
            point["coil_no"]["name"]
            for point in config["tracking"]["points"]
            if point["code"].lower().startswith("por")
        ]
        table = CUBE_TABLES[unit]
        columns = metadata.get(table, {})
        if not fields or columns.get("ts", (None,))[0] != "CHILD_TABLE":
            raise ValueError(f"Cube 子表或 por 配置无效: {unit}/{table}")
        missing = set(fields) - set(columns)
        if missing:
            raise ValueError(f"Cube 子表缺少 por 卷号列: {table}/{sorted(missing)}")
        sources.append((unit, table, fields))
    return sources


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
        CREATE TABLE por_event (
            unit TEXT NOT NULL, coil TEXT NOT NULL, device TEXT NOT NULL,
            first_ts TEXT NOT NULL,
            PRIMARY KEY (unit, device, first_ts)
        );
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


def td_bounds(connection, database: str, table: str) -> datetime | None:
    if database not in (DATABASE, "cube"):
        raise ValueError(f"未允许读取的 TDengine 数据库: {database}")
    sql = (
        f"SELECT CAST(FIRST(ts) AS BIGINT), CAST(LAST(ts) AS BIGINT) "
        f"FROM {database}.{quote_td(table)}"
    )
    rows = list(query(connection, sql))
    row = rows[0] if rows else None
    return None if row is None or row[0] is None else local_time(row[0])


def scan_process(connection, stage: sqlite3.Connection, tables, cutoff: datetime, days: int):
    for table, unit, column in tables:
        first = td_bounds(connection, DATABASE, table)
        if first is None:
            continue
        for start, end in windows(first, cutoff, days):
            sql = (
                f"SELECT coil_no, {quote_td(column)}, "
                f"CAST(FIRST(ts) AS BIGINT), CAST(LAST(ts) AS BIGINT) "
                f"FROM {DATABASE}.{quote_td(table)} "
                f"WHERE ts >= {epoch_ms(start)} "
                f"AND ts < {epoch_ms(end)} "
                f"AND coil_no IS NOT NULL AND {quote_td(column)} IS NOT NULL "
                f"GROUP BY coil_no, {quote_td(column)}"
            )
            for raw_coil, raw_seq, first_ts, last_ts in query(connection, sql):
                coil = normalize_coil(raw_coil)
                if coil is None:
                    continue
                try:
                    seq = int(raw_seq)
                except (TypeError, ValueError):
                    seq = 0
                if seq <= 0 or str(seq) != str(raw_seq).strip():
                    stage.execute(
                        "INSERT INTO anomaly VALUES (?, ?, ?, ?)",
                        (unit, coil, "invalid_sequence", f"{table}: {raw_seq!r}"),
                    )
                    continue
                earliest, latest = timestamp_text(first_ts), timestamp_text(last_ts)
                stage.execute(
                    """INSERT INTO evidence VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT(unit, coil, seq) DO UPDATE SET
                    first_ts = min(first_ts, excluded.first_ts),
                    last_ts = max(last_ts, excluded.last_ts)""",
                    (unit, coil, seq, earliest, latest),
                )
                stage.execute(
                    "INSERT OR IGNORE INTO source VALUES (?, ?, ?, ?)",
                    (unit, coil, seq, table),
                )
            stage.commit()
        print(f"已扫描过程表 {table}", file=sys.stderr)


def scan_por(connection, stage: sqlite3.Connection, sources, cutoff: datetime, days: int):
    """保留每台 por 设备的卷号切换，仅用来提示疑似漏次。"""
    for unit, table, fields in sources:
        first = td_bounds(connection, "cube", table)
        if first is None:
            continue
        previous = {field: None for field in fields}
        for start, end in windows(first, cutoff, days):
            columns = ", ".join(quote_td(field) for field in fields)
            sql = (
                f"SELECT CAST(ts AS BIGINT), {columns} FROM cube.{quote_td(table)} "
                f"WHERE ts >= {epoch_ms(start)} "
                f"AND ts < {epoch_ms(end)} ORDER BY ts"
            )
            for row in query(connection, sql):
                instant = timestamp_text(row[0])
                for field, raw in zip(fields, row[1:]):
                    coil = normalize_coil(raw)
                    if coil is not None and coil != previous[field]:
                        stage.execute(
                            "INSERT OR IGNORE INTO por_event VALUES (?, ?, ?, ?)",
                            (unit, coil, field, instant),
                        )
                    previous[field] = coil
            stage.commit()
        print(f"已扫描 por 原始表 {table}", file=sys.stderr)


def write_csv(path: Path, header: list[str], rows):
    with path.open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.writer(stream)
        writer.writerow(header)
        writer.writerows(rows)


def finalize(stage: sqlite3.Connection, output: Path, cutoff: datetime, tables):
    """检测序号缺口及时间倒置，生成可复核的固定候选文件。"""
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
        # 相邻序号的过程区间重叠，说明不同工艺段可能报告了冲突的序号。
        spans = list(stage.execute(
            "SELECT seq, first_ts, last_ts FROM evidence "
            "WHERE unit=? AND coil=? ORDER BY seq", key
        ))
        for earlier, later in zip(spans, spans[1:]):
            if earlier[2] >= later[1]:
                blocked.add(key)
                issues.append((*key, "sequence_overlap",
                               f"{earlier[0]} ends {earlier[2]}, {later[0]} begins {later[1]}"))
    # 原始点位反复出现同一卷只能提示，不能改变明确的过程序号。
    for unit, coil, events in stage.execute(
        "SELECT unit, coil, COUNT(*) FROM por_event GROUP BY unit, coil"
    ):
        observed = len(grouped.get((unit, coil), []))
        if events > max(observed, 1):
            issues.append((unit, coil, "suspected_missing_por", f"por_events={events}, process_sequences={observed}"))
    write_csv(output / "anomalies.csv", ["unit_code", "in_mat_no", "reason", "detail"], issues)
    candidates = []
    for (unit, coil), values in sorted(grouped.items()):
        if (unit, coil) in blocked:
            continue
        for seq, instant in values:
            candidates.append((unit, coil, seq, instant))
    write_csv(
        output / "candidates.csv",
        ["unit_code", "in_mat_no", "in_mat_repeat_prod_no", "create_time"],
        candidates,
    )
    manifest = {
        "cutoff_shanghai": timestamp_text(cutoff),
        "process_tables": [table for table, _, _ in tables],
        "candidate_count": len(candidates),
        "blocked_coils": len(blocked),
        "anomaly_count": len(issues),
        "candidate_sha256": hashlib.sha256((output / "candidates.csv").read_bytes()).hexdigest(),
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
    content = (output / "candidates.csv").read_bytes()
    if hashlib.sha256(content).hexdigest() != manifest["candidate_sha256"]:
        raise ValueError("候选文件校验失败，请重新扫描")
    with (output / "candidates.csv").open(encoding="utf-8-sig", newline="") as stream:
        rows = list(csv.DictReader(stream))
    if len(rows) != manifest["candidate_count"]:
        raise ValueError("候选数量与清单不一致")
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
    parser.add_argument("--window-days", type=int, default=1, help="扫描窗口天数，默认 1")
    options = parser.parse_args(argv)
    if options.apply:
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
    with closing(taosws.connect(TD_DSN)) as connection:
        validate_precision(connection)
        tables = discover_process_tables(connection)
        sources = cube_sources(connection, {unit for _, unit, _ in tables})
        stage = create_stage(options.output_dir / "stage.sqlite")
        try:
            scan_process(connection, stage, tables, cutoff, options.window_days)
            scan_por(connection, stage, sources, cutoff, options.window_days)
            manifest = finalize(stage, options.output_dir, cutoff, tables)
        finally:
            stage.close()
    print(json.dumps(manifest, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
