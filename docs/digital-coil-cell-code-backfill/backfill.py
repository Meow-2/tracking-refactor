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
import threading
import time
from contextlib import contextmanager
from datetime import datetime, timedelta, timezone
from pathlib import Path


SHANGHAI = timezone(timedelta(hours=8))
TABLE_LIST = Path(__file__).with_name("digital_coil_cell_code_tables.md")
TABLE_PATTERN = re.compile(r"^- `([a-z][a-z0-9_]+)`$", re.MULTILINE)
UNIT_PREFIXES = frozenset(("cp1", "cbl1", "dcl1", "fcl1", "zrm1", "csl1"))
MILLISECOND = 1
PROGRESS_INTERVAL_SECONDS = 30


def log_progress(message: str):
    """立即输出上海时间和执行位置；消息不得包含连接串或业务字段值。"""
    now = datetime.now(SHANGHAI).strftime("%Y-%m-%d %H:%M:%S")
    print(f"[{now}] {message}", flush=True)


def set_phase(status: dict, phase: str):
    """以单次赋值更新心跳快照，避免读到一半更新的阶段描述。"""
    status["current"] = (phase, time.monotonic())


@contextmanager
def progress_heartbeat(label: str, status: dict,
                       interval_seconds: float = PROGRESS_INTERVAL_SECONDS):
    """单表运行期间定期报告所处阶段，查询阻塞时仍能判断进程是否存活。"""
    started = time.monotonic()
    stopped = threading.Event()

    def report_while_waiting():
        while not stopped.wait(interval_seconds):
            phase, phase_started = status["current"]
            log_progress(f"{label} 仍在运行：{phase}；当前阶段 {time.monotonic() - phase_started:.0f} 秒，"
                         f"本表累计 {time.monotonic() - started:.0f} 秒")

    worker = threading.Thread(target=report_while_waiting, daemon=True)
    worker.start()
    try:
        yield
    except Exception as error:
        phase, _ = status["current"]
        log_progress(f"{label} 失败：{phase}；本表累计 {time.monotonic() - started:.1f} 秒，"
                     f"异常类型={type(error).__name__}")
        raise
    finally:
        stopped.set()
        worker.join(timeout=1)


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
        log_progress(f"{table} 跳过：缺少 coil_no")
        return False
    coil_type, coil_note = columns["coil_no"]
    if coil_type not in ("VARCHAR", "BINARY", "NCHAR") or coil_note == "TAG":
        log_progress(f"{table} 跳过：coil_no 不是普通字符串列")
        return False
    pass_type, pass_note = columns.get("pass_no", ("", ""))
    if pass_type not in ("INT", "INT UNSIGNED") or pass_note == "TAG":
        log_progress(f"{table} 跳过：缺少普通 INT 类型的 pass_no")
        return False
    kind, note = columns.get("cell_code", ("", ""))
    if kind not in ("VARCHAR", "BINARY", "NCHAR") or note == "TAG":
        raise ValueError(f"{table}: cell_code 缺失、类型不符或为 TAG")
    return True


def process_window(connection, table: str, unit: str, start: int, end: int,
                   max_rows: int, pause: float, apply: bool,
                   status: dict | None = None, window_label: str = "") -> int:
    """超限窗口二分到毫秒；任一窗口失败立即中止，不自动重试不明状态的写入。"""
    count_sql, insert_sql = window_sql(table, unit, start, end)
    range_label = f"[{format_time(start)}, {format_time(end)})"
    phase_label = f"{window_label} {range_label}".strip()
    if status is not None:
        set_phase(status, f"{phase_label} 统计待更新行数")
    started = time.monotonic()
    count = int(execute(connection, count_sql)[0])
    count_elapsed = time.monotonic() - started
    if count > max_rows:
        if end - start <= MILLISECOND:
            raise ValueError(f"{table} {format_time(start)}: 单毫秒记录超过上限")
        middle = start + (end - start) // 2
        log_progress(f"{table} {phase_label} 待更新 {count} 行，超过上限 {max_rows}；"
                     f"计数耗时 {count_elapsed:.1f} 秒，按 {format_time(middle)} 二分")
        return (process_window(connection, table, unit, start, middle, max_rows, pause, apply,
                               status, window_label)
                + process_window(connection, table, unit, middle, end, max_rows, pause, apply,
                                 status, window_label))
    log_progress(f"{table} {phase_label} 待更新 {count} 行，计数耗时 {count_elapsed:.1f} 秒"
                 + ("；空段跳过" if count == 0 else "；准备写入" if apply else "；预览不写入"))
    if count and apply:
        if status is not None:
            set_phase(status, f"{phase_label} 写入 {count} 行")
        started = time.monotonic()
        execute(connection, insert_sql)
        write_elapsed = time.monotonic() - started
        if status is not None:
            set_phase(status, f"{phase_label} 复查写入结果")
        remaining = int(execute(connection, count_sql)[0])
        if remaining:
            raise RuntimeError(f"{table} [{format_time(start)}, {format_time(end)}) 仍有 {remaining} 行未更新")
        log_progress(f"{table} {phase_label} 写入 {count} 行完成；写入耗时 {write_elapsed:.1f} 秒，"
                     f"复查耗时 {time.monotonic() - started - write_elapsed:.1f} 秒，剩余 0 行")
        if pause:
            if status is not None:
                set_phase(status, f"{phase_label} 写入间隔等待 {pause:g} 秒")
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
    parser.add_argument("--progress-seconds", type=float, default=PROGRESS_INTERVAL_SECONDS,
                        help="长时间无窗口结果时的心跳间隔秒数，默认 30")
    parser.add_argument("--apply", action="store_true", help="实际写入；默认只计数预览")
    args = parser.parse_args()
    if args.window_hours <= 0 or args.max_rows <= 0 or args.pause < 0 or args.progress_seconds <= 0:
        parser.error("窗口、行数和心跳间隔必须为正，等待秒数不能为负")
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

    run_started = time.monotonic()
    log_progress(f"开始连接 TDengine；模式={'写入' if args.apply else '预览'}，"
                 f"固定截止={format_time(args.end)}，计划检查 {len(selected)} 张表，"
                 f"初始窗口={args.window_hours} 小时，单批上限={args.max_rows} 行")
    connection = taosws.connect(dsn)
    try:
        precision = execute(connection, "SELECT `precision` FROM information_schema.ins_databases WHERE name = 'digital_coil'")
        if not precision or str(precision[0]).lower() != "ms":
            raise ValueError("digital_coil 必须使用毫秒精度")
        log_progress("连接成功；digital_coil 毫秒精度校验通过")
        step = args.window_hours * 3600 * 1000
        skipped_tables = 0
        processed_tables = 0
        total_rows = 0
        for table_index, table in enumerate(selected, 1):
            table_label = f"[{table_index}/{len(selected)}] {table}"
            table_started = time.monotonic()
            status = {"current": ("校验表结构", table_started)}
            log_progress(f"{table_label} 开始；校验表结构")
            with progress_heartbeat(table_label, status, args.progress_seconds):
                if not validate_table(connection, table):
                    skipped_tables += 1
                    log_progress(f"{table_label} 已跳过；耗时 {time.monotonic() - table_started:.1f} 秒")
                    continue
                if args.start is None:
                    set_phase(status, "查询最早时间戳")
                    first = execute(connection, f"SELECT CAST(ts AS BIGINT) FROM digital_coil.`{table}` ORDER BY ts ASC LIMIT 1")
                    start = int(first[0]) if first else args.end
                else:
                    start = args.start
                table_start = start
                window_count = max(0, (args.end - start + step - 1) // step)
                log_progress(f"{table_label} 时间范围 [{format_time(start)}, {format_time(args.end)})；"
                             f"初始窗口 {window_count} 个")
                table_rows = 0
                for window_index in range(1, window_count + 1):
                    end = min(start + step, args.end)
                    window_label = f"窗口 {window_index}/{window_count}"
                    window_started = time.monotonic()
                    log_progress(f"{table_label} {window_label} 开始 [{format_time(start)}, {format_time(end)})")
                    table_rows += process_window(connection, table, tables[table], start, end,
                                                 args.max_rows, args.pause, args.apply,
                                                 status, window_label)
                    progress = (end - table_start) * 100 / (args.end - table_start)
                    set_phase(status, f"{window_label} 已完成")
                    log_progress(f"{table_label} {window_label} 完成；时间范围进度 {progress:.1f}%，"
                                 f"本表累计{'已更新' if args.apply else '预计更新'} {table_rows} 行，窗口耗时 "
                                 f"{time.monotonic() - window_started:.1f} 秒")
                    start = end
                processed_tables += 1
                total_rows += table_rows
                log_progress(f"{table_label} 完成；{'已更新' if args.apply else '预计更新'} "
                             f"{table_rows} 行，耗时 {time.monotonic() - table_started:.1f} 秒")
        log_progress(f"全部完成：处理 {processed_tables} 张，跳过 {skipped_tables} 张，"
                     f"{'已更新' if args.apply else '预计更新'} {total_rows} 行，"
                     f"总耗时 {time.monotonic() - run_started:.1f} 秒")
    finally:
        connection.close()
    return 0


if __name__ == "__main__":
    # Windows 的 Python 默认控制台编码可能与终端不一致；统一以 UTF-8 输出中文进度。
    for output in (sys.stdout, sys.stderr):
        if hasattr(output, "reconfigure"):
            output.reconfigure(encoding="utf-8")
    try:
        sys.exit(main())
    except (ValueError, RuntimeError) as error:
        print(f"已停止: {error}", file=sys.stderr)
        sys.exit(1)
    except Exception as error:
        # 驱动异常可能包含 DSN，仅输出异常类型；详细信息可在数据库监控中查询。
        print(f"已停止，异常类型: {type(error).__name__}", file=sys.stderr)
        sys.exit(1)
