#!/usr/bin/env python3
"""按上海时间分段重算 digital_coil 普通表的 cell_code。

表范围来自同目录的审核清单；只通过 TDengine WebSocket 执行单表、单窗口查询和写入。
连接串从环境变量读取，日志不输出连接信息或业务行数据。
"""

from __future__ import annotations

import argparse
import os
import re
import sys
import time
from datetime import datetime, timedelta, timezone
from pathlib import Path


SHANGHAI = timezone(timedelta(hours=8))
TABLE_LIST = Path(__file__).with_name("digital_coil_cell_code_tables.md")
TABLE_PATTERN = re.compile(r"^- `([a-z][a-z0-9_]+)`$", re.MULTILINE)
UNIT_PREFIXES = frozenset(("cp1", "cbl1", "dcl1", "fcl1", "zrm1", "csl1"))
MILLISECOND = 1


def parse_time(value: str) -> int:
    """将上海本地时间转为 Unix 毫秒；边界必须精确到毫秒。"""
    try:
        parsed = datetime.fromisoformat(value)
    except ValueError as error:
        raise argparse.ArgumentTypeError("时间格式应为 YYYY-MM-DD HH:MM:SS[.mmm]") from error
    if parsed.tzinfo is not None or parsed.microsecond % 1000:
        raise argparse.ArgumentTypeError("请提供上海本地无时区时间，最多精确到毫秒")
    return int(parsed.replace(tzinfo=SHANGHAI).timestamp() * 1000)


def format_time(value: int) -> str:
    """日志统一使用上海本地时间，避免客户端时区改变边界含义。"""
    return datetime.fromtimestamp(value / 1000, SHANGHAI).strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]


def load_tables() -> dict[str, str]:
    """从审核清单筛选六类表；机组代码取第一个下划线前的表名前缀。"""
    names = TABLE_PATTERN.findall(TABLE_LIST.read_text(encoding="utf-8"))
    if len(names) != 93 or len(set(names)) != len(names):
        raise ValueError("表清单不是预期的 93 张唯一普通表，请重新核对")
    selected = [name for name in names if name.split("_", 1)[0] in UNIT_PREFIXES]
    if len(selected) != 92:
        raise ValueError("六类机组表不是预期的 92 张，请重新核对")
    return {name: name.split("_", 1)[0].upper() for name in selected}


def expected_code(unit: str) -> str:
    """构造与旧 SQL 一致的道次表达式；负数及四位以上道次保留原数字文本。"""
    return (
        f"CONCAT('{unit}', CASE "
        "WHEN pass_no IS NULL THEN '001' "
        "WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16))) "
        "WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16))) "
        "ELSE CAST(pass_no AS VARCHAR(16)) END)"
    )


def window_sql(table: str, unit: str, start: int, end: int) -> tuple[str, str]:
    """只选目标值不同的记录；相同时间戳仅写 ts 与 cell_code。"""
    source = f"digital_coil.`{table}`"
    code = expected_code(unit)
    condition = (
        f"ts >= {start} AND ts < {end} "
        f"AND (cell_code IS NULL OR cell_code <> {code})"
    )
    count = f"SELECT COUNT(*) FROM {source} WHERE {condition}"
    insert = (
        f"INSERT INTO {source} (ts, cell_code) "
        f"SELECT ts, {code} FROM {source} WHERE {condition}"
    )
    return count, insert


def execute(connection, sql: str):
    """每次操作使用独立游标，避免前一个结果占用连接。"""
    cursor = connection.cursor()
    try:
        cursor.execute(sql)
        return cursor.fetchone() if sql.startswith("SELECT") else None
    finally:
        cursor.close()


def validate_table(connection, table: str) -> bool:
    """写入前检查所需列；缺少 coil_no 或 pass_no 的表不参与回填。"""
    cursor = connection.cursor()
    try:
        cursor.execute(f"DESCRIBE digital_coil.`{table}`")
        columns = {str(row[0]).lower(): (str(row[1]).upper(), str(row[3]).upper() if len(row) > 3 and row[3] else "")
                   for row in cursor.fetchall()}
    finally:
        cursor.close()
    if "TIMESTAMP" not in columns.get("ts", ("",))[0]:
        raise ValueError(f"{table}: ts 不是 TIMESTAMP")
    if "coil_no" not in columns:
        print(f"{table} 跳过：缺少 coil_no", flush=True)
        return False
    coil_type, coil_note = columns["coil_no"]
    if coil_type not in ("VARCHAR", "BINARY", "NCHAR") or coil_note == "TAG":
        print(f"{table} 跳过：coil_no 不是普通字符串列", flush=True)
        return False
    pass_type, pass_note = columns.get("pass_no", ("", ""))
    if pass_type not in ("INT", "INT UNSIGNED") or pass_note == "TAG":
        print(f"{table} 跳过：缺少普通 INT 类型的 pass_no", flush=True)
        return False
    kind, note = columns.get("cell_code", ("", ""))
    if kind not in ("VARCHAR", "BINARY", "NCHAR") or note == "TAG":
        raise ValueError(f"{table}: cell_code 缺失、类型不符或为 TAG")
    return True


def process_window(connection, table: str, unit: str, start: int, end: int,
                   max_rows: int, pause: float, apply: bool) -> int:
    """超限窗口二分到毫秒；任一窗口失败立即中止，不自动重试不明状态的写入。"""
    count_sql, insert_sql = window_sql(table, unit, start, end)
    count = int(execute(connection, count_sql)[0])
    if count > max_rows:
        if end - start <= MILLISECOND:
            raise ValueError(f"{table} {format_time(start)}: 单毫秒记录超过上限")
        middle = start + (end - start) // 2
        return (process_window(connection, table, unit, start, middle, max_rows, pause, apply)
                + process_window(connection, table, unit, middle, end, max_rows, pause, apply))
    print(f"{table} [{format_time(start)}, {format_time(end)}) 待更新 {count}", flush=True)
    if count and apply:
        started = time.monotonic()
        execute(connection, insert_sql)
        remaining = int(execute(connection, count_sql)[0])
        if remaining:
            raise RuntimeError(f"{table} [{format_time(start)}, {format_time(end)}) 仍有 {remaining} 行未更新")
        print(f"{table} [{format_time(start)}, {format_time(end)}) 写入并复查完成，"
              f"耗时 {time.monotonic() - started:.1f} 秒", flush=True)
        if pause:
            time.sleep(pause)
    return count


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--table", action="append", help="六类机组清单内表名；可重复指定，不指定则处理全部 92 张")
    parser.add_argument("--start", type=parse_time, help="上海时间起点；不填则逐表读取最早 ts")
    parser.add_argument("--end", type=parse_time, required=True, help="固定截止时间，上海时间，左闭右开")
    parser.add_argument("--window-hours", type=int, default=24, help="初始时间窗口小时数，默认 24")
    parser.add_argument("--max-rows", type=int, default=10000, help="单条写入最多涉及的行数，默认 10000")
    parser.add_argument("--pause", type=float, default=1, help="写入窗口之间等待秒数，默认 1")
    parser.add_argument("--apply", action="store_true", help="实际写入；默认只计数预览")
    args = parser.parse_args()
    if args.window_hours <= 0 or args.max_rows <= 0 or args.pause < 0:
        parser.error("窗口、行数必须为正，等待秒数不能为负")
    if args.start is not None and args.start >= args.end:
        parser.error("起点必须早于截止时间")
    tables = load_tables()
    selected = args.table or list(tables)
    if any(name not in tables for name in selected):
        parser.error("--table 必须来自六类机组的 92 张表清单")
    if len(set(selected)) != len(selected):
        parser.error("--table 不能重复")
    dsn = os.environ.get("TD_DSN")
    if not dsn:
        parser.error("请通过 TD_DSN 环境变量提供 TDengine WebSocket 连接串")
    import taosws  # 延迟导入，离线校验参数和单元测试不依赖数据库驱动。

    connection = taosws.connect(dsn)
    try:
        precision = execute(connection, "SELECT `precision` FROM information_schema.ins_databases WHERE name = 'digital_coil'")
        if not precision or str(precision[0]).lower() != "ms":
            raise ValueError("digital_coil 必须使用毫秒精度")
        print(f"模式={'写入' if args.apply else '预览'}，固定截止={format_time(args.end)}，表数={len(selected)}", flush=True)
        step = args.window_hours * 3600 * 1000
        for table in selected:
            if not validate_table(connection, table):
                continue
            if args.start is None:
                first = execute(connection, f"SELECT CAST(ts AS BIGINT) FROM digital_coil.`{table}` ORDER BY ts ASC LIMIT 1")
                start = int(first[0]) if first else args.end
            else:
                start = args.start
            total = 0
            while start < args.end:
                end = min(start + step, args.end)
                total += process_window(connection, table, tables[table], start, end,
                                        args.max_rows, args.pause, args.apply)
                start = end
            print(f"{table} 完成，{'更新' if args.apply else '预计更新'} {total} 行", flush=True)
    finally:
        connection.close()
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (ValueError, RuntimeError) as error:
        print(f"已停止: {error}", file=sys.stderr)
        sys.exit(1)
    except Exception as error:
        # 驱动异常可能包含 DSN，仅输出异常类型；详细信息可在数据库监控中查询。
        print(f"已停止，异常类型: {type(error).__name__}", file=sys.stderr)
        sys.exit(1)
