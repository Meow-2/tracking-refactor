#!/usr/bin/env python3
"""按上海时间分段将 digital_coil 普通表中 repeat_prod_no 的 NULL 补为 1。

自动发现含目标列的普通表，不依赖旧列、卷号或道次；已有非空值保持不变。
沿用已有回填脚本的 WebSocket、按固定时间窗口直接写入和心跳机制，写入完成后直接汇总，不额外扫描空值。
连接串在文件顶部配置，不输出连接信息或业务行值。
"""

from __future__ import annotations

import argparse
import re
import sys
import threading
import time
from contextlib import contextmanager
from datetime import datetime, timedelta, timezone


SHANGHAI = timezone(timedelta(hours=8))
# SQL 中的表名只能来自元数据，并须通过简单标识符校验。
IDENTIFIER = re.compile(r"^[a-z][a-z0-9_]*$")
PROGRESS_INTERVAL_SECONDS = 30
# 本地运行前填写 WebSocket 连接串；含敏感凭据，禁止输出日志或提交真实值。
TD_DSN = "ws://<user>:<password>@<host>:6041"


def quote(identifier: str) -> str:
    """校验表名后再拼接 SQL；不接受库名、表达式或转义字符。"""
    if not IDENTIFIER.fullmatch(identifier):
        raise ValueError(f"不支持的 TDengine 表名: {identifier!r}")
    return f"`{identifier}`"


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


def day_start(epoch_ms: int) -> int:
    """返回该毫秒时刻所在上海自然日的零点，用于对齐每日窗口边界。"""
    midnight = datetime.fromtimestamp(epoch_ms / 1000, SHANGHAI).replace(
        hour=0, minute=0, second=0, microsecond=0)
    return int(midnight.timestamp() * 1000)


def hour_start(epoch_ms: int) -> int:
    """返回该时刻所在上海小时的整点，使缩小窗口后的首段不跨过整点边界。"""
    hour = datetime.fromtimestamp(epoch_ms / 1000, SHANGHAI).replace(
        minute=0, second=0, microsecond=0)
    return int(hour.timestamp() * 1000)


def discover_tables(connection) -> list[str]:
    """按名称排序发现含目标列的普通表；超表和子表不在处理范围内。"""
    rows = execute(connection, (
        "SELECT table_name, table_type, col_type FROM information_schema.ins_columns "
        "WHERE db_name = 'digital_coil' AND col_name = 'repeat_prod_no'"
    ), all_rows=True)
    tables = set()
    for table, table_type, column_type in rows:
        if str(table_type).upper() != "NORMAL_TABLE":
            continue
        name = str(table)
        quote(name)
        if str(column_type).upper() != "INT":
            raise ValueError(f"{name}: repeat_prod_no 必须为 INT")
        tables.add(name)
    return sorted(tables)


def window_sql(table: str, start: int, end: int) -> tuple[str, str]:
    """仅回写同一时间戳的目标列；NULL 以外的值（含 0）均保留。"""
    source = f"digital_coil.{quote(table)}"
    condition = f"ts >= {start} AND ts < {end} AND repeat_prod_no IS NULL"
    count = f"SELECT COUNT(*) FROM {source} WHERE {condition}"
    insert = (f"INSERT INTO {source} (ts, repeat_prod_no) "
              f"SELECT ts, 1 FROM {source} WHERE {condition}")
    return count, insert


def execute(connection, sql: str, all_rows: bool = False):
    """每次查询使用独立游标；元数据读取全量，写入不读取结果。"""
    cursor = connection.cursor()
    try:
        cursor.execute(sql)
        if all_rows:
            return cursor.fetchall()
        return cursor.fetchone() if sql.startswith("SELECT") else None
    finally:
        cursor.close()


def validate_table(connection, table: str) -> None:
    """逐表确认毫秒时间戳及普通 INT 目标列，结构不符时立即停止。"""
    rows = execute(connection, f"DESCRIBE digital_coil.{quote(table)}", all_rows=True)
    columns = {str(row[0]).lower(): (str(row[1]).upper(),
               str(row[3]).upper() if len(row) > 3 and row[3] else "") for row in rows}
    if columns.get("ts") != ("TIMESTAMP", ""):
        raise ValueError(f"{table}: ts 必须为普通 TIMESTAMP 列")
    if columns.get("repeat_prod_no") != ("INT", ""):
        raise ValueError(f"{table}: repeat_prod_no 必须为普通 INT 列")


def process_window(connection, table: str, start: int, end: int,
                   pause: float, apply: bool,
                   status: dict | None = None, window_label: str = "") -> int | None:
    """一个窗口直接执行一条写入 SQL，不预读时间戳、不计数、不逐批复查。

    预览仅执行 COUNT 并返回待补行数；写入返回 None，避免把未知影响行数记为零。
    窗口覆盖左闭右开毫秒范围，数据量由时间跨度控制，不限制行数。
    写入异常立即向上抛出，调用方不会继续后续窗口；可从原窗口起点重跑。
    """
    count_sql, insert_sql = window_sql(table, start, end)
    phase_label = f"{window_label} [{format_time(start)}, {format_time(end)})".strip()
    if status is not None:
        set_phase(status, f"{phase_label} " + ("直接写入" if apply else "预览计数"))
    started = time.monotonic()
    if not apply:
        count = int(execute(connection, count_sql)[0])
        log_progress(f"{table} {phase_label} 预计填充 {count} 行；计数耗时 {time.monotonic() - started:.1f} 秒")
        return count
    execute(connection, insert_sql)
    log_progress(f"{table} {phase_label} 写入完成；耗时 {time.monotonic() - started:.1f} 秒")
    if pause:
        if status is not None:
            set_phase(status, f"{phase_label} 写入间隔等待 {pause:g} 秒")
        time.sleep(pause)
    return None


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--table", action="append", help="指定普通表；可重复，不指定则处理所有含目标列的普通表")
    parser.add_argument("--start", type=parse_time, help="上海时间起点；不填则逐表读取最早 ts")
    parser.add_argument("--resume-table", help="从按名称排序的发现结果中继续，包含该表；不能与 --table 同用")
    parser.add_argument("--resume-start", type=parse_time,
                        help="仅指定继续表的上海时间起点；不填则从该表最早 ts 重查")
    parser.add_argument("--end", type=parse_time, required=True, help="固定截止时间，上海时间，左闭右开")
    windows = parser.add_mutually_exclusive_group()
    windows.add_argument("--window-hours", type=int, help="窗口小时数，按上海整点划分，默认 2")
    windows.add_argument("--window-days", type=int, help="可选按上海自然日划分；不能与 --window-hours 同用")
    parser.add_argument("--max-rows", type=int, help="兼容旧命令；已不限制行数，请使用 --window-hours 控制窗口")
    parser.add_argument("--pause", type=float, default=0, help="写入窗口之间等待秒数，默认 0")
    parser.add_argument("--progress-seconds", type=float, default=PROGRESS_INTERVAL_SECONDS,
                        help="长时间无窗口结果时的心跳间隔秒数，默认 30")
    parser.add_argument("--apply", action="store_true", help="实际写入；默认只计数预览")
    args = parser.parse_args(argv)
    window_hours = args.window_days * 24 if args.window_days is not None else (
        args.window_hours if args.window_hours is not None else 2)
    if window_hours <= 0 or (args.max_rows is not None and args.max_rows <= 0) or args.pause < 0 or args.progress_seconds <= 0:
        parser.error("窗口、行数和心跳间隔必须为正，等待秒数不能为负")
    if args.start is not None and args.start >= args.end:
        parser.error("起点必须早于截止时间")
    if args.resume_start is not None and args.resume_start >= args.end:
        parser.error("继续起点必须早于截止时间")
    if args.resume_start is not None and args.resume_table is None:
        parser.error("--resume-start 必须与 --resume-table 同用")
    if args.resume_table is not None and (args.table or args.start is not None):
        parser.error("--resume-table 不能与 --table 或 --start 同用")
    if not TD_DSN or "<" in TD_DSN or ">" in TD_DSN:
        parser.error("请先在脚本顶部填写 TD_DSN 连接串")
    import taosws  # 延迟导入，离线校验参数和单元测试不依赖数据库驱动。

    run_started = time.monotonic()
    log_progress(f"开始连接 TDengine；模式={'写入' if args.apply else '预览'}，"
                 f"固定截止={format_time(args.end)}，"
                 f"固定窗口={window_hours} 小时，每窗口直接写入，不限制行数")
    if args.max_rows is not None:
        log_progress("--max-rows 已不生效；请用 --window-hours 调整单次写入范围")
    connection = taosws.connect(TD_DSN)
    try:
        precision = execute(connection, "SELECT `precision` FROM information_schema.ins_databases WHERE name = 'digital_coil'")
        if not precision or str(precision[0]).lower() != "ms":
            raise ValueError("digital_coil 必须使用毫秒精度")
        log_progress("连接成功；digital_coil 毫秒精度校验通过")
        tables = discover_tables(connection)
        if args.table:
            if len(set(args.table)) != len(args.table):
                parser.error("--table 不能重复")
            if any(name not in tables for name in args.table):
                parser.error("指定表不在含 repeat_prod_no 的普通表中")
        if args.resume_table is not None and args.resume_table not in tables:
            parser.error("--resume-table 不在含 repeat_prod_no 的普通表中")
        selected = args.table or tables
        if args.resume_table is not None:
            selected = tables[tables.index(args.resume_table):]
        log_progress(f"发现 {len(tables)} 张普通表，计划处理 {len(selected)} 张")
        step = window_hours * 3600 * 1000
        # 默认按整点处理；显式按天时保留原有自然日边界。
        align_start = day_start if args.window_days is not None else hour_start
        processed_tables = 0
        total_rows = 0
        total_windows = 0
        for table_index, table in enumerate(selected, 1):
            table_label = f"[{table_index}/{len(selected)}] {table}"
            table_started = time.monotonic()
            status = {"current": ("校验表结构", table_started)}
            log_progress(f"{table_label} 开始；校验表结构")
            with progress_heartbeat(table_label, status, args.progress_seconds):
                validate_table(connection, table)
                # 断点时间只作用于第一张表；后续表仍须从各自最早记录开始。
                resume_start = args.resume_start if table == args.resume_table else None
                if args.start is None and resume_start is None:
                    set_phase(status, "查询最早时间戳")
                    first = execute(connection, f"SELECT CAST(ts AS BIGINT) FROM digital_coil.{quote(table)} ORDER BY ts ASC LIMIT 1")
                    start = int(first[0]) if first else args.end
                else:
                    start = resume_start if resume_start is not None else args.start
                table_start = start
                window_count = ((args.end - align_start(start) + step - 1) // step
                                if start < args.end else 0)
                log_progress(f"{table_label} 时间范围 [{format_time(start)}, {format_time(args.end)})；"
                             f"初始窗口 {window_count} 个")
                table_rows = 0
                for window_index in range(1, window_count + 1):
                    end = min(align_start(start) + step, args.end)
                    window_label = f"窗口 {window_index}/{window_count}"
                    window_started = time.monotonic()
                    log_progress(f"{table_label} {window_label} 开始 [{format_time(start)}, {format_time(end)})")
                    count = process_window(connection, table, start, end,
                                           args.pause, args.apply, status, window_label)
                    if count is not None:
                        table_rows += count
                    total_windows += 1
                    progress = (end - table_start) * 100 / (args.end - table_start)
                    set_phase(status, f"{window_label} 已完成")
                    log_progress(f"{table_label} {window_label} 完成；时间范围进度 {progress:.1f}%，" +
                                 (f"本表已执行 {window_index} 个写入窗口，" if args.apply
                                  else f"本表预计填充 {table_rows} 行，") + "窗口耗时 " +
                                 f"{time.monotonic() - window_started:.1f} 秒")
                    start = end
                processed_tables += 1
                total_rows += table_rows
                log_progress(f"{table_label} 完成；" +
                             (f"已执行 {window_count} 个写入窗口，" if args.apply
                              else f"预计填充 {table_rows} 行，") +
                             f"耗时 {time.monotonic() - table_started:.1f} 秒")
        log_progress(f"全部完成：处理 {processed_tables} 张，" +
                     (f"已执行 {total_windows} 个写入窗口，" if args.apply
                      else f"预计填充 {total_rows} 行，") +
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
