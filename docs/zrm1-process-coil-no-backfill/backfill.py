#!/usr/bin/env python3
"""修正 digital_coil.zrm1_process_default 中倒数第三位为 3 的 coil_no。

沿用 digital-coil-repeat-prod-no-backfill 的 WebSocket、预览、窗口二分和心跳机制。
仅处理固定普通表，以同时间戳部分列写入将指定字符改为 2；不修改其他列或表结构。
"""

from __future__ import annotations

import argparse
import math
import re
import sys
import threading
import time
from contextlib import contextmanager
from datetime import datetime, timedelta, timezone


DATABASE = "digital_coil"
TABLE = "zrm1_process_default"
SOURCE = f"{DATABASE}.`{TABLE}`"
# 本地连接配置，沿用 cell-code 回填脚本；包含敏感凭据，禁止输出日志或提交真实值。
TD_DSN = "ws://<user>:<password>@<host>:6041"
SHANGHAI = timezone(timedelta(hours=8))
# 按字符数定位，不去空格、不补零；短于三位和空值均不处理。
MATCHING = "CHAR_LENGTH(coil_no) >= 3 AND SUBSTR(coil_no, -3, 1) = '3'"
# 三字符卷号没有前缀，单独处理，避免依赖 SUBSTR 长度为 0 的版本行为。
CORRECTED = ("CASE WHEN CHAR_LENGTH(coil_no) = 3 THEN CONCAT('2', SUBSTR(coil_no, 2, 2)) "
             "ELSE CONCAT(SUBSTR(coil_no, 1, CAST(CHAR_LENGTH(coil_no) - 3 AS INT)), "
             "'2', SUBSTR(coil_no, -2, 2)) END")
# TDengine 部分版本拒绝将算术表达式的推导类型作为 SUBSTR 参数，动态长度必须转 INT。


def log(message: str) -> None:
    """输出上海时间及执行位置；调用方不得传入凭据或业务行值。"""
    stamp = datetime.now(SHANGHAI).strftime("%Y-%m-%d %H:%M:%S")
    print(f"[{stamp}] {message}", flush=True)


def error_summary(error: Exception) -> str:
    """仅提取错误类型、数值错误码和白名单摘要，禁止输出驱动原始消息中的凭据。"""
    message = str(error)
    code = re.search(r"\b0x[0-9a-fA-F]{4,8}\b", message)
    parts = [f"异常类型={type(error).__name__}"]
    if code:
        parts.append(f"错误码={code.group(0)}")
    if "invalid parameter data type" in message.lower():
        parts.append("摘要=函数参数类型不受支持")
    return "；".join(parts)


def parse_time(value: str) -> int:
    """将上海无时区 ISO 时间转成 Unix 毫秒；拒绝更细精度和显式时区。"""
    try:
        parsed = datetime.fromisoformat(value)
    except ValueError as error:
        raise argparse.ArgumentTypeError("时间格式应为 YYYY-MM-DD HH:MM:SS[.mmm]") from error
    if parsed.tzinfo is not None or parsed.microsecond % 1000:
        raise argparse.ArgumentTypeError("请提供上海本地无时区时间，最多精确到毫秒")
    epoch = parsed.replace(tzinfo=SHANGHAI) - datetime(1970, 1, 1, tzinfo=timezone.utc)
    return (epoch.days * 86400 + epoch.seconds) * 1000 + epoch.microseconds // 1000


def format_time(epoch_ms: int) -> str:
    """将毫秒边界显示为上海时间，日志中的结束边界不包含在扫描范围内。"""
    return datetime.fromtimestamp(epoch_ms / 1000, SHANGHAI).strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]


def execute(connection, sql: str, all_rows: bool = False):
    """沿用参考脚本的独立游标；写入不取结果，任何异常均向上传递。"""
    cursor = connection.cursor()
    try:
        cursor.execute(sql)
        if all_rows:
            return cursor.fetchall()
        return cursor.fetchone() if sql.startswith("SELECT") else None
    except Exception as error:
        log(f"数据库请求失败；操作={'查询' if sql.startswith('SELECT') else '写入'}；{error_summary(error)}")
        raise
    finally:
        cursor.close()


def validate_schema(connection) -> None:
    """仅接受毫秒库及固定普通表，避免向超表、TAG 或非字符串列写入。"""
    precision = execute(connection, "SELECT `precision` FROM information_schema.ins_databases "
                        f"WHERE name = '{DATABASE}'")
    if not precision or str(precision[0]).lower() != "ms":
        raise ValueError("digital_coil 必须是毫秒精度数据库")
    rows = execute(connection, "SELECT table_type, col_name, col_type "
                   "FROM information_schema.ins_columns "
                   f"WHERE db_name = '{DATABASE}' AND table_name = '{TABLE}'", all_rows=True)
    if not rows or any(str(row[0]).upper() != "NORMAL_TABLE" for row in rows):
        raise ValueError(f"{TABLE}: 缺少表结构或不是普通表")
    columns = {str(row[1]).lower(): str(row[2]).upper() for row in rows}
    if columns.get("ts") != "TIMESTAMP":
        raise ValueError(f"{TABLE}: ts 必须是 TIMESTAMP")
    coil_type = columns.get("coil_no", "").split("(", 1)[0].strip()
    if coil_type not in ("VARCHAR", "BINARY", "NCHAR"):
        raise ValueError(f"{TABLE}: coil_no 必须是字符串列")
    description = execute(connection, f"DESCRIBE {SOURCE}", all_rows=True)
    # 只写 ts 和 coil_no 的前提是 ts 为首列且不存在第二主键；否则可能插入新行。
    if not description or str(description[0][0]).lower() != "ts":
        raise ValueError(f"{TABLE}: ts 必须是首列")
    if any(len(row) > 3 and "KEY" in str(row[3]).upper() for row in description[1:]):
        raise ValueError(f"{TABLE}: 不支持第二主键")


def time_bounds(connection) -> tuple[int, int] | None:
    """固定已有历史首尾，结束加一毫秒以包含最后一行；空表返回 None。"""
    first = execute(connection, f"SELECT CAST(ts AS BIGINT) FROM {SOURCE} ORDER BY ts ASC LIMIT 1")
    if not first:
        return None
    last = execute(connection, f"SELECT CAST(ts AS BIGINT) FROM {SOURCE} ORDER BY ts DESC LIMIT 1")
    if not last:
        raise RuntimeError("读取首尾期间数据发生变化，请重新运行")
    return int(first[0]), int(last[0]) + 1


def window_sql(start: int, end: int) -> tuple[str, str]:
    """计数、写入和复查使用相同条件；仅提交 ts 和修正后的 coil_no。"""
    condition = f"ts >= {start} AND ts < {end} AND {MATCHING}"
    return (f"SELECT COUNT(*) FROM {SOURCE} WHERE {condition}",
            f"INSERT INTO {SOURCE} (ts, coil_no) SELECT ts, {CORRECTED} "
            f"FROM {SOURCE} WHERE {condition}")


def process_window(connection, start: int, end: int, max_rows: int,
                   apply: bool, phase: dict) -> int:
    """超限按毫秒二分，写入后剩余候选非零则停止；重复运行跳过已修正记录。

    max_rows 是基于写前计数的批量阈值，并发迟到写入时不构成严格上限。
    返回写前候选行数，非数据库报告的实际影响行数。
    """
    interval = f"[{format_time(start)}, {format_time(end)})"
    count_sql, insert_sql = window_sql(start, end)
    phase["name"] = f"统计窗口 {interval}"
    count = int(execute(connection, count_sql)[0])
    if count > max_rows:
        if end - start <= 1:
            raise ValueError(f"{interval}: 单毫秒候选行数超过上限 {max_rows}")
        middle = start + (end - start) // 2
        log(f"{interval} 候选 {count} 行，超过上限；按 {format_time(middle)} 二分")
        return (process_window(connection, start, middle, max_rows, apply, phase)
                + process_window(connection, middle, end, max_rows, apply, phase))
    log(f"{interval} 候选 {count} 行" + ("；预览" if not apply else ""))
    if count and apply:
        phase["name"] = f"写入窗口 {interval}"
        execute(connection, insert_sql)
        phase["name"] = f"复查窗口 {interval}"
        remaining = int(execute(connection, count_sql)[0])
        if remaining:
            raise RuntimeError(f"{interval} 仍有 {remaining} 行待修正")
        log(f"{interval} 写入并复查完成，剩余 0 行")
    return count


@contextmanager
def heartbeat(phase: dict, seconds: float):
    """连接、查询及写入阻塞时仍报告阶段，退出时停止后台线程。"""
    stopped = threading.Event()
    started = time.monotonic()

    def report():
        while not stopped.wait(seconds):
            log(f"{TABLE} 仍在运行；阶段={phase['name']}；耗时 {time.monotonic() - started:.0f} 秒")

    worker = threading.Thread(target=report, daemon=True)
    worker.start()
    try:
        yield
    finally:
        stopped.set()
        worker.join(timeout=1)


def main(argv=None) -> int:
    """默认仅预览；--apply 才写入。失败不自动重试，可按日志起点续跑。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true", help="实际写入；默认只预览")
    parser.add_argument("--start", type=parse_time, help="上海时间起点（含）；默认取最早 ts，也用于续跑")
    parser.add_argument("--end", type=parse_time, help="上海时间终点（不含）；默认取最晚 ts 加一毫秒")
    parser.add_argument("--window-hours", type=int, default=24, help="初始窗口小时数，默认 24")
    parser.add_argument("--max-rows", type=int, default=10000, help="写前候选行数阈值，默认 10000")
    parser.add_argument("--progress-seconds", type=float, default=30, help="心跳秒数，默认 30")
    args = parser.parse_args(argv)
    if (args.window_hours <= 0 or args.max_rows <= 0
            or not math.isfinite(args.progress_seconds) or args.progress_seconds <= 0):
        parser.error("窗口、行数阈值和心跳间隔必须为有限正数")
    if args.start is not None and args.end is not None and args.start >= args.end:
        parser.error("起点必须早于终点")
    if not TD_DSN or "<" in TD_DSN or ">" in TD_DSN:
        parser.error("请在脚本顶部填写 TD_DSN 连接串")
    import taosws  # 延迟导入，离线测试不需要安装驱动。

    started = time.monotonic()
    phase = {"name": "连接 TDengine"}
    log(f"开始处理 {SOURCE}；模式={'写入' if args.apply else '预览'}")
    with heartbeat(phase, args.progress_seconds):
        connection = taosws.connect(TD_DSN)
        try:
            phase["name"] = "校验表结构和数据库精度"
            validate_schema(connection)
            phase["name"] = "固定扫描范围"
            bounds = time_bounds(connection) if args.start is None or args.end is None else (args.start, args.end)
            if bounds is None:
                log("空表，完成；候选 0 行")
                return 0
            first = args.start if args.start is not None else bounds[0]
            last = args.end if args.end is not None else bounds[1]
            log(f"固定范围 [{format_time(first)}, {format_time(last)})")
            step = args.window_hours * 3600 * 1000
            windows = max(0, (last - first + step - 1) // step)
            total = 0
            for index in range(windows):
                start = first + index * step
                end = min(start + step, last)
                log(f"窗口 {index + 1}/{windows} 开始 [{format_time(start)}, {format_time(end)})")
                total += process_window(connection, start, end, args.max_rows, args.apply, phase)
                log(f"窗口 {index + 1}/{windows} 完成；时间范围进度 "
                    f"{(end - first) * 100 / (last - first):.1f}%；累计候选 {total} 行")
            log(f"全部完成；模式={'写入并复查' if args.apply else '预览'}；累计候选 {total} 行；"
                f"耗时 {time.monotonic() - started:.1f} 秒")
            return 0
        finally:
            connection.close()


if __name__ == "__main__":
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8")
    try:
        sys.exit(main())
    except Exception as error:
        # 原始驱动异常可能包含连接串，仅输出类型；用前一条进度定位失败窗口。
        log(f"已停止；{error_summary(error)}；请按最后阶段检查后续跑")
        sys.exit(1)
