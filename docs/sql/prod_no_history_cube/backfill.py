#!/usr/bin/env python3
"""以 Cube POR 卷号的稳定切换重建上卷历史，预览后补入 PostgreSQL。

本脚本只读取 TDengine cube；扫描与写入分开执行。窗口边界保留设备状态，
但时间范围之前的历史完整性无法由点位自身证明，写入须显式确认覆盖范围。
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
import sys
import unicodedata
from collections import defaultdict
from contextlib import closing
from datetime import datetime, timedelta, timezone
from pathlib import Path
from zoneinfo import ZoneInfo

SHANGHAI = ZoneInfo("Asia/Shanghai")
ROOT = Path(__file__).resolve().parents[3]
TABLES = {
    "CBL1": "cbl1_plc_whole_tracking_sub",
    "CP1": "cp1_plc5_sg_whole_tracking_sub",
    "CSL1": "csl1_plc_reroll_tracking_sub",
    "DCL1": "dcl1_plc5_sg_whole_tracking_sub",
    "FCL1": "fcl1_plc5_sg_whole_tracking_sub",
    "ZRM1": "zrm1_plc_mim_public_sub",
}
# 在此填写连接信息；账号仅需 cube 查询权限，PG 账号仅需目标表查询与插入权限。
# 不要将实际密码提交到 Git；运行前在本机副本中填写。
TDENGINE_DSN = "ws://root:taosdata@172.16.203.12:6041"
POSTGRES_DSN = "postgresql://postgres:postgres@127.0.0.1:5432/aygg_tracking"
IDENTIFIER = re.compile(r"^[a-z][a-z0-9_]*$")
INVALID_PREFIX = re.compile(r"^request\s+color\b", re.IGNORECASE)


def normalize_coil(value):
    """沿用 status 算法的首尾不可见字符及占位卷号过滤口径。"""
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


def identifier(value):
    """配置字段和对象名只能进入受限的 TDengine 标识符位置。"""
    if not IDENTIFIER.fullmatch(value):
        raise ValueError(f"无效 TDengine 标识符: {value!r}")
    return f"`{value}`"


def epoch_ms(local):
    """将上海本地时间转为毫秒时间戳，避免客户端时区改变查询边界。"""
    return int(local.replace(tzinfo=SHANGHAI).timestamp() * 1000)


def local_time(milliseconds):
    """TDengine 毫秒时间戳转为 PG timestamp(6) 使用的上海本地时间。"""
    seconds, remainder = divmod(int(milliseconds), 1000)
    utc = datetime.fromtimestamp(seconds, timezone.utc).replace(microsecond=remainder * 1000)
    return utc.astimezone(SHANGHAI).replace(tzinfo=None)


def query(connection, sql):
    """分批读取时序数据，避免全量历史占满内存。"""
    cursor = connection.cursor()
    try:
        cursor.execute(sql)
        while True:
            batch = cursor.fetchmany(1000)
            if not batch:
                break
            yield from batch
    finally:
        cursor.close()


def point_fields(unit):
    """从当前 status 配置提取 POR 卷号，避免手工维护字段列表。"""
    path = ROOT / "docs" / "config" / unit / "status.json"
    config = json.loads(path.read_text(encoding="utf-8"))
    fields = {point["code"]: point["coil_no"]["name"]
              for point in config["tracking"]["points"]
              if point["code"].lower().startswith("por")}
    if not fields:
        raise ValueError(f"{unit} 无 POR 卷号配置")
    for field in fields.values():
        identifier(field)
    return fields


def validate_source(connection, unit, fields):
    """验证毫秒精度、Cube 子表及字段，配置不符时停止扫描。"""
    precision = list(query(connection, "SELECT `precision` FROM information_schema.ins_databases WHERE name='cube'"))
    if precision != [("ms",)]:
        raise ValueError(f"cube 时间精度不是 ms: {precision}")
    table = TABLES[unit]
    columns = {str(name): str(kind) for name, kind in query(connection,
        "SELECT col_name, table_type FROM information_schema.ins_columns "
        f"WHERE db_name='cube' AND table_name='{table}'")}
    if columns.get("ts") != "CHILD_TABLE" or not set(fields.values()) <= columns.keys():
        raise ValueError(f"{unit} Cube 子表或 POR 卷号列与配置不符: {table}")


def windows(start, end, days):
    """逐段生成左闭右开查询区间，调用方在段间保持设备状态。"""
    current = start
    while current < end:
        following = min(current + timedelta(days=days), end)
        yield current, following
        current = following


def stable_runs(runs, minimum_samples, minimum_seconds):
    """过滤短暂抖动；无效值不制造虚假的重新上卷。"""
    # 仅稳定的新卷号能隔开相邻生产事件；短暂抖动并回原卷应合并。
    accepted = []
    unstable = []
    for coil, first, last, count in runs:
        if count < minimum_samples or (last - first).total_seconds() < minimum_seconds:
            unstable.append((coil, first, count))
            continue
        if accepted and accepted[-1][0] == coil:
            accepted[-1] = (coil, accepted[-1][1], last, accepted[-1][3] + count)
        else:
            accepted.append((coil, first, last, count))
    return accepted, unstable


def infer_events(device_runs, merge_seconds):
    """跨 POR 同卷近同时上报合并一次；其余按全机组时间顺序编号。"""
    by_coil = defaultdict(list)
    for device, runs in device_runs.items():
        for coil, first, last, count in runs:
            by_coil[coil].append((first, device, last, count))
    events = []
    overlaps = []
    for coil, observations in by_coil.items():
        merged = []
        for first, device, last, count in sorted(observations):
            if merged and device not in merged[-1][2] and (
                    (first - merged[-1][0]).total_seconds() <= merge_seconds
                    or first <= merged[-1][1]):
                merged[-1][2].add(device)
                merged[-1][1] = max(merged[-1][1], last)
                overlaps.append((coil, first, device))
            else:
                merged.append([first, last, {device}])
        for sequence, (first, last, devices) in enumerate(merged, 1):
            events.append((coil, sequence, first, last, ",".join(sorted(devices))))
    return sorted(events, key=lambda row: (row[2], row[0])), overlaps


def scan(connection, unit, start, end, days, minimum_samples, minimum_seconds, merge_seconds):
    """在 TDengine 聚合卷号段与采样会话，再重建上卷事件。"""
    print(f"[{unit}] 正在校验 Cube 表和字段", file=sys.stderr, flush=True)
    fields = point_fields(unit)
    validate_source(connection, unit, fields)
    raw_runs = {device: [] for device in fields}
    valid_segments = {device: [] for device in fields}
    sessions = {device: [] for device in fields}
    anomalies = []
    table = identifier(TABLES[unit])
    for left, right in windows(start, end, days):
        print(f"[{unit}] 开始查询 {left} 至 {right}（上海时间）", file=sys.stderr, flush=True)
        segment_count = 0
        for device, field_name in fields.items():
            field = identifier(field_name)
            bounds = (f"ts >= {epoch_ms(left)} AND ts < {epoch_ms(right)} "
                      f"AND {field} IS NOT NULL")
            # 服务端按原始卷号连续状态聚合，仍保留短暂占位值段供后续识别抖动。
            state_sql = (
                "SELECT CAST(_wstart AS BIGINT), CAST(_wend AS BIGINT), "
                f"COUNT(*), FIRST({field}) FROM cube.{table} WHERE {bounds} "
                f"STATE_WINDOW({field}) ORDER BY _wstart"
            )
            for first_ms, last_ms, count, raw_coil in query(connection, state_sql):
                segment_count += 1
                coil = normalize_coil(raw_coil)
                if coil is None:
                    continue
                first, last = local_time(first_ms), local_time(last_ms)
                valid_segments[device].append((first, last, coil))
                runs = raw_runs[device]
                if runs and (first - runs[-1][2]).total_seconds() > 3600:
                    detail = f"{device} {runs[-1][2]} -> {first}"
                    anomalies.extend(((runs[-1][0], "sample_gap", detail),
                                      (coil, "sample_gap", detail)))
                if not runs or runs[-1][0] != coil:
                    runs.append([coil, first, last, int(count)])
                else:
                    runs[-1][2] = last
                    runs[-1][3] += int(count)
            # 同一卷号段内也可能有停采；SESSION 找出相邻采样超过一小时的空洞。
            session_sql = (
                "SELECT CAST(_wstart AS BIGINT), CAST(_wend AS BIGINT), COUNT(*) "
                f"FROM cube.{table} WHERE {bounds} SESSION(ts, 3600s) ORDER BY _wstart"
            )
            sessions[device].extend((local_time(first_ms), local_time(last_ms))
                                    for first_ms, last_ms, _ in query(connection, session_sql))
        print(f"[{unit}] 完成查询 {left} 至 {right}，卷号段 {segment_count} 个",
              file=sys.stderr, flush=True)
    for device, intervals in sessions.items():
        segments = valid_segments[device]
        for (_, previous_end), (next_start, _) in zip(intervals, intervals[1:]):
            if (next_start - previous_end).total_seconds() <= 3600:
                continue
            before = next((coil for first, _, coil in reversed(segments)
                           if first <= previous_end), None)
            after = next((coil for _, last, coil in segments
                          if last >= next_start), None)
            detail = f"{device} {previous_end} -> {next_start}"
            for coil in {before, after} - {None}:
                anomalies.append((coil, "sample_gap", detail))
    device_runs = {}
    for device, rows in raw_runs.items():
        runs, unstable = stable_runs(rows, minimum_samples, minimum_seconds)
        device_runs[device] = runs
        unstable_firsts = {first for _, first, _ in unstable}
        for index, (coil, first, _, count) in enumerate(rows):
            if first not in unstable_firsts:
                continue
            detail = f"{device} {first.isoformat()} samples={count}"
            # 抖动 B 夹在 A 的两段之间时，A 是否重新上卷也不能确定。
            for affected in {coil, rows[index - 1][0] if index else coil,
                             rows[index + 1][0] if index + 1 < len(rows) else coil}:
                anomalies.append((affected, "unstable_neighbor", detail))
    events, overlaps = infer_events(device_runs, merge_seconds)
    anomalies.extend((coil, "cross_por_overlap", f"{device} {first.isoformat()}")
                     for coil, first, device in overlaps)
    return events, anomalies


def write_preview(output, all_events, all_anomalies, start, end, options):
    """输出候选和异常；异常卷不进入可写入候选。"""
    output.mkdir(parents=True, exist_ok=False)
    blocked = {(unit, coil) for unit, coil, _, _ in all_anomalies}
    with (output / "events.csv").open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.writer(stream)
        writer.writerow(["unit_code", "in_mat_no", "in_mat_repeat_prod_no", "create_time", "last_seen", "por_devices", "status"])
        for unit, coil, sequence, first, last, devices in all_events:
            writer.writerow([unit, coil, sequence, first, last, devices,
                             "review" if (unit, coil) in blocked else "candidate"])
    with (output / "anomalies.csv").open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.writer(stream)
        writer.writerow(["unit_code", "in_mat_no", "reason", "detail"])
        writer.writerows(all_anomalies)
    digest = hashlib.sha256((output / "events.csv").read_bytes()).hexdigest()
    manifest = {"start_shanghai": str(start), "end_shanghai": str(end),
                "units": options.units, "minimum_samples": options.min_samples,
                "minimum_seconds": options.min_seconds, "merge_seconds": options.merge_seconds,
                "event_count": len(all_events), "blocked_coils": len(blocked),
                "events_sha256": digest}
    (output / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return manifest


def record_id(unit, coil, sequence):
    """使用负数确定性 ID，与在线正数雪花 ID 区分。"""
    key = f"POR\0{unit}\0{coil}\0{sequence}".encode("utf-8")
    return -(int.from_bytes(hashlib.sha256(key).digest()[:8], "big") & ((1 << 63) - 1) or 1)


def apply_preview(output, connection):
    """按卷事务补入缺失业务键；既有键的时间冲突必须人工复核。"""
    manifest = json.loads((output / "manifest.json").read_text(encoding="utf-8"))
    if hashlib.sha256((output / "events.csv").read_bytes()).hexdigest() != manifest["events_sha256"]:
        raise ValueError("events.csv 与预览清单哈希不一致")
    if connection.execute("SELECT to_regclass('public.qm_dc_repeat_prod_no_log')").fetchone()[0] is None:
        raise ValueError("目标库缺少 public.qm_dc_repeat_prod_no_log")
    grouped = defaultdict(list)
    with (output / "events.csv").open(encoding="utf-8-sig", newline="") as stream:
        for row in csv.DictReader(stream):
            if row["status"] == "candidate":
                grouped[(row["unit_code"], row["in_mat_no"])].append(row)
    results = []
    for (unit, coil), rows in sorted(grouped.items()):
        try:
            with connection.transaction():
                existing = connection.execute(
                    "SELECT in_mat_repeat_prod_no, create_time, deleted FROM public.qm_dc_repeat_prod_no_log "
                    "WHERE unit_code=%s AND in_mat_no=%s ORDER BY in_mat_repeat_prod_no FOR UPDATE",
                    (unit, coil)).fetchall()
                known = {int(seq): (created, deleted) for seq, created, deleted in existing}
                sequences = {int(row["in_mat_repeat_prod_no"]) for row in rows}
                combined = sequences | known.keys()
                if unit != unit.upper() or normalize_coil(coil) != coil or len(rows) != len(sequences):
                    raise ValueError("候选业务键或序号无效")
                if sequences != set(range(1, max(sequences) + 1)) or combined != set(range(1, max(combined) + 1)):
                    raise ValueError("序号不从 1 连续递增")
                if any(deleted != 0 for _, deleted in known.values()):
                    raise ValueError("已有逻辑删除记录")
                inserted = 0
                for row in rows:
                    sequence = int(row["in_mat_repeat_prod_no"])
                    instant = datetime.fromisoformat(row["create_time"])
                    if sequence in known:
                        old_time = known[sequence][0]
                        if old_time is not None and abs((old_time - instant).total_seconds()) > 3600:
                            raise ValueError(f"序号 {sequence} 与既有时间冲突")
                        continue
                    key = record_id(unit, coil, sequence)
                    if connection.execute("SELECT 1 FROM public.qm_dc_repeat_prod_no_log WHERE id=%s", (key,)).fetchone():
                        raise ValueError(f"回填 ID 冲突: {key}")
                    connection.execute(
                        "INSERT INTO public.qm_dc_repeat_prod_no_log "
                        "(id, unit_code, in_mat_no, in_mat_repeat_prod_no, deleted, create_time) "
                        "VALUES (%s, %s, %s, %s, 0, %s)", (key, unit, coil, sequence, instant))
                    inserted += 1
                results.append((unit, coil, "inserted", inserted))
        except Exception as error:
            results.append((unit, coil, "blocked", str(error)))
    with (output / "apply_results.csv").open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.writer(stream)
        writer.writerow(["unit_code", "in_mat_no", "status", "detail"])
        writer.writerows(results)
    return results


def parse_time(text):
    """命令行时间只接受上海本地无时区格式，避免混用 UTC。"""
    try:
        value = datetime.fromisoformat(text)
    except ValueError as error:
        raise argparse.ArgumentTypeError("时间格式应为 YYYY-MM-DD HH:MM:SS") from error
    if value.tzinfo is not None:
        raise argparse.ArgumentTypeError("请输入无时区的上海本地时间")
    return value


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--start", type=parse_time, help="上海时间，含")
    parser.add_argument("--end", type=parse_time, help="上海时间，不含")
    parser.add_argument("--units", nargs="+", choices=sorted(TABLES), default=sorted(TABLES))
    parser.add_argument("--window-days", type=int, default=1)
    parser.add_argument("--min-samples", type=int, default=3)
    parser.add_argument("--min-seconds", type=int, default=10)
    parser.add_argument("--merge-seconds", type=int, default=300)
    parser.add_argument("--apply", action="store_true", help="使用已复核的预览结果写入 PG")
    parser.add_argument("--accept-coverage", action="store_true", help="确认起点以前不存在需计数的历史")
    options = parser.parse_args(argv)
    if options.apply:
        if not options.accept_coverage:
            parser.error("写入前必须用 --accept-coverage 确认历史覆盖范围")
        if "<" in POSTGRES_DSN:
            parser.error("请先在脚本顶部填写 POSTGRES_DSN")
        import psycopg
        with psycopg.connect(POSTGRES_DSN, autocommit=True) as connection:
            results = apply_preview(options.output_dir, connection)
        print(json.dumps({"inserted": sum(int(row[3]) for row in results if row[2] == "inserted"),
                          "blocked_coils": sum(row[2] == "blocked" for row in results)}, ensure_ascii=False))
        return int(any(row[2] == "blocked" for row in results))
    if not options.start or not options.end or options.start >= options.end:
        parser.error("扫描需要 --start 和更晚的 --end")
    if min(options.window_days, options.min_samples) < 1 or min(options.min_seconds, options.merge_seconds) < 0:
        parser.error("窗口/样本数必须为正，秒数不可为负")
    if options.output_dir.exists():
        parser.error("输出目录必须不存在，以免覆盖已复核结果")
    if "<" in TDENGINE_DSN:
        parser.error("请先在脚本顶部填写 TDENGINE_DSN")
    import taosws
    all_events, all_anomalies = [], []
    print("正在连接 TDengine 并扫描 Cube 数据", file=sys.stderr, flush=True)
    with closing(taosws.connect(TDENGINE_DSN)) as connection:
        print("TDengine 已连接", file=sys.stderr, flush=True)
        for unit in options.units:
            events, anomalies = scan(connection, unit, options.start, options.end,
                                      options.window_days, options.min_samples,
                                      options.min_seconds, options.merge_seconds)
            all_events.extend((unit, *event) for event in events)
            all_anomalies.extend((unit, *anomaly) for anomaly in anomalies)
            print(f"已扫描 {unit}: 事件 {len(events)}，异常 {len(anomalies)}", file=sys.stderr, flush=True)
    print(json.dumps(write_preview(options.output_dir, all_events, all_anomalies,
                                   options.start, options.end, options), ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
