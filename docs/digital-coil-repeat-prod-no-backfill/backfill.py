#!/usr/bin/env python3
"""补全 digital_coil 普通表的 repeat_prod_no，或独立校验并删除旧列。

通过 TDengine WebSocket 发现同时包含两个普通列的表；填充使用固定时间窗口，
删除模式对每张表检查完整历史后执行 DDL。连接信息只从环境变量读取。
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


DATABASE = "digital_coil"
SHANGHAI = timezone(timedelta(hours=8))
IDENTIFIER = re.compile(r"^[a-z][a-z0-9_]*$")
HEARTBEAT_SECONDS = 30
MILLISECOND = 1
MISSING = "in_mat_prod_no IS NOT NULL AND repeat_prod_no IS NULL"


def log(message: str) -> None:
    """输出带上海时间的进度；调用方不得传入连接串或业务行值。"""
    stamp = datetime.now(SHANGHAI).strftime("%Y-%m-%d %H:%M:%S")
    print(f"[{stamp}] {message}", flush=True)


def quote(identifier: str) -> str:
    """元数据表名进入 SQL 前只允许简单标识符，防止意外拼接。"""
    if not IDENTIFIER.fullmatch(identifier):
        raise ValueError(f"不支持的 TDengine 表名: {identifier!r}")
    return f"`{identifier}`"


def format_time(epoch_ms: int) -> str:
    """将毫秒边界按上海时间显示，方便从日志定位断点。"""
    return datetime.fromtimestamp(epoch_ms / 1000, SHANGHAI).strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]


def execute(connection, sql: str, all_rows: bool = False):
    """每次查询使用独立游标；写入语句不读取结果。"""
    cursor = connection.cursor()
    try:
        cursor.execute(sql)
        if all_rows:
            return cursor.fetchall()
        if sql.startswith("SELECT"):
            return cursor.fetchone()
        return None
    finally:
        cursor.close()


def discover_tables(connection) -> list[str]:
    """只选择两个列均为普通列且类型一致的普通表；异常结构立即停止。"""
    rows = execute(connection, (
        "SELECT table_name, table_type, col_name, col_type "
        "FROM information_schema.ins_columns "
        f"WHERE db_name = '{DATABASE}' AND col_name IN ('in_mat_prod_no', 'repeat_prod_no')"
    ), all_rows=True)
    columns = {}
    for table, table_type, column, column_type in rows:
        name = str(table)
        quote(name)
        columns.setdefault(name, {})[str(column).lower()] = (str(table_type).upper(), str(column_type).upper())
    selected = []
    for table, fields in sorted(columns.items()):
        if set(fields) != {"in_mat_prod_no", "repeat_prod_no"}:
            continue
        source_kind, source_type = fields["in_mat_prod_no"]
        target_kind, target_type = fields["repeat_prod_no"]
        if source_kind != "NORMAL_TABLE" or target_kind != "NORMAL_TABLE":
            raise ValueError(f"{table}: 同时含两列，但不是普通表；请单独核对超表/子表")
        if source_type != target_type:
            raise ValueError(f"{table}: 两列类型不一致 ({source_type}/{target_type})")
        selected.append(table)
    return selected


def count_missing(connection, table: str, start: int | None = None,
                  end: int | None = None) -> int:
    """完整表校验不传时间边界；填充窗口采用左闭右开毫秒边界。"""
    condition = MISSING
    if start is not None:
        condition += f" AND ts >= {start} AND ts < {end}"
    row = execute(connection, f"SELECT COUNT(*) FROM {DATABASE}.{quote(table)} WHERE {condition}")
    return int(row[0])


def time_bounds(connection, table: str) -> tuple[int, int] | None:
    """固定本表现有数据首尾时间；终点加一毫秒以覆盖最后一条记录。"""
    source = f"{DATABASE}.{quote(table)}"
    first = execute(connection, f"SELECT CAST(ts AS BIGINT) FROM {source} ORDER BY ts ASC LIMIT 1")
    if not first:
        return None
    last = execute(connection, f"SELECT CAST(ts AS BIGINT) FROM {source} ORDER BY ts DESC LIMIT 1")
    return int(first[0]), int(last[0]) + MILLISECOND


def fill_window(connection, table: str, start: int, end: int,
                max_rows: int, apply: bool, phase: dict) -> int:
    """大窗口递归二分；写入后复查，失败时可按原范围安全重跑。"""
    interval = f"[{format_time(start)}, {format_time(end)})"
    phase["name"] = f"统计窗口 {interval}"
    count = count_missing(connection, table, start, end)
    if count > max_rows:
        if end - start <= MILLISECOND:
            raise ValueError(f"{table} {interval}: 单毫秒待填充行数超过上限 {max_rows}")
        middle = start + (end - start) // 2
        log(f"{table} {interval} 待填充 {count} 行，超过上限；按 {format_time(middle)} 二分")
        return (fill_window(connection, table, start, middle, max_rows, apply, phase)
                + fill_window(connection, table, middle, end, max_rows, apply, phase))
    log(f"{table} {interval} 待填充 {count} 行" + ("；预览" if not apply else ""))
    if count and apply:
        phase["name"] = f"写入窗口 {interval}"
        source = f"{DATABASE}.{quote(table)}"
        sql = (f"INSERT INTO {source} (ts, repeat_prod_no) "
               f"SELECT ts, in_mat_prod_no FROM {source} "
               f"WHERE {MISSING} AND ts >= {start} AND ts < {end}")
        execute(connection, sql)
        phase["name"] = f"复查窗口 {interval}"
        remaining = count_missing(connection, table, start, end)
        if remaining:
            raise RuntimeError(f"{table} {interval} 仍有 {remaining} 行待填充")
        log(f"{table} {interval} 写入并复查完成，剩余 0 行")
    return count


@contextmanager
def heartbeat(label: str, phase: dict, seconds: float):
    """数据库调用阻塞时仍定时报告表名、阶段和已等待时长。"""
    stopped = threading.Event()
    started = time.monotonic()

    def report():
        while not stopped.wait(seconds):
            log(f"{label} 仍在运行；阶段={phase['name']}；耗时 {time.monotonic() - started:.0f} 秒")

    worker = threading.Thread(target=report, daemon=True)
    worker.start()
    try:
        yield
    finally:
        stopped.set()
        worker.join(timeout=1)


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true", help="实际写入或删列；默认只预览")
    parser.add_argument("--drop-in-mat-prod-no", action="store_true",
                        help="独立删列模式：逐表全量校验后删除旧列，不执行填充")
    parser.add_argument("--table", action="append", help="只处理指定表；可重复指定")
    parser.add_argument("--resume-table", help="从指定表开始继续，包含该表")
    parser.add_argument("--window-hours", type=int, default=24, help="填充窗口小时数，默认 24")
    parser.add_argument("--max-rows", type=int, default=10000, help="单次写入最大行数，默认 10000")
    parser.add_argument("--progress-seconds", type=float, default=HEARTBEAT_SECONDS,
                        help="无响应时的进度心跳秒数，默认 30")
    args = parser.parse_args(argv)
    if args.window_hours <= 0 or args.max_rows <= 0 or args.progress_seconds <= 0:
        parser.error("窗口、行数上限和心跳间隔必须为正数")
    if args.table and args.resume_table:
        parser.error("--table 和 --resume-table 不能同时使用")
    # 连接串包含敏感凭据，仅在运行时读取；缺失时停止，且不得写入日志。
    td_dsn = os.environ.get("TD_DSN")
    if not td_dsn:
        parser.error("请通过环境变量 TD_DSN 配置 TDengine WebSocket 连接串")
    import taosws  # 延迟导入，便于离线测试参数和 SQL。
    started = time.monotonic()
    mode = "删列" if args.drop_in_mat_prod_no else "填充"
    log(f"开始连接 TDengine；模式={mode}，执行={'是' if args.apply else '否（预览）'}")
    connection = taosws.connect(td_dsn)
    try:
        precision = execute(connection, "SELECT `precision` FROM information_schema.ins_databases WHERE name = 'digital_coil'")
        if not precision or str(precision[0]).lower() != "ms":
            raise ValueError("digital_coil 必须是毫秒精度数据库")
        log("连接成功，开始发现双列普通表")
        tables = discover_tables(connection)
        if args.table:
            missing = set(args.table) - set(tables)
            if missing:
                parser.error(f"指定表不在双列普通表中: {', '.join(sorted(missing))}")
            if len(args.table) != len(set(args.table)):
                parser.error("--table 不能重复")
            tables = args.table
        if args.resume_table:
            if args.resume_table not in tables:
                parser.error("--resume-table 不在双列普通表中")
            tables = tables[tables.index(args.resume_table):]
        log(f"发现并计划处理 {len(tables)} 张双列普通表")
        total_rows = 0
        dropped = 0
        blocked = 0
        for index, table in enumerate(tables, 1):
            label = f"[{index}/{len(tables)}] {table}"
            phase = {"name": "开始"}
            table_started = time.monotonic()
            log(f"{label} 开始")
            with heartbeat(label, phase, args.progress_seconds):
                if args.drop_in_mat_prod_no:
                    phase["name"] = "全量校验缺失值"
                    remaining = count_missing(connection, table)
                    if remaining:
                        blocked += 1
                        log(f"{label} 保留旧列：仍有 {remaining} 行源列有值、目标列为空")
                        continue
                    log(f"{label} 全量校验通过：待填充 0 行")
                    if args.apply:
                        phase["name"] = "删除旧列"
                        execute(connection, f"ALTER TABLE {DATABASE}.{quote(table)} DROP COLUMN in_mat_prod_no")
                        dropped += 1
                        log(f"{label} 已删除 in_mat_prod_no")
                    log(f"{label} 完成；耗时 {time.monotonic() - table_started:.1f} 秒")
                    continue
                bounds = time_bounds(connection, table)
                if bounds is None:
                    log(f"{label} 空表，跳过")
                    continue
                first, last = bounds
                step = args.window_hours * 3600 * 1000
                windows = (last - first + step - 1) // step
                table_rows = 0
                for window_index in range(windows):
                    start = first + window_index * step
                    end = min(start + step, last)
                    log(f"{label} 窗口 {window_index + 1}/{windows} 开始 "
                        f"[{format_time(start)}, {format_time(end)})")
                    table_rows += fill_window(connection, table, start, end, args.max_rows, args.apply, phase)
                    log(f"{label} 窗口 {window_index + 1}/{windows} 完成；"
                        f"时间范围进度 {(end - first) * 100 / (last - first):.1f}%；本表累计 {table_rows} 行")
                total_rows += table_rows
                log(f"{label} 完成；{'已填充' if args.apply else '预计填充'} {table_rows} 行；"
                    f"耗时 {time.monotonic() - table_started:.1f} 秒")
        if args.drop_in_mat_prod_no:
            log(f"全部完成；表数={len(tables)}，{'已删列' if args.apply else '可删列'}="
                f"{dropped if args.apply else len(tables) - blocked}，删列受阻={blocked}，"
                f"总耗时 {time.monotonic() - started:.1f} 秒")
        else:
            log(f"全部完成；表数={len(tables)}，{'已填充' if args.apply else '预计填充'}="
                f"{total_rows} 行，总耗时 {time.monotonic() - started:.1f} 秒")
        return 0 if blocked == 0 else 2
    finally:
        connection.close()


if __name__ == "__main__":
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8")
    try:
        sys.exit(main())
    except (ValueError, RuntimeError) as error:
        log(f"已停止：{error}")
        sys.exit(1)
    except Exception as error:
        # 驱动异常可能携带 DSN，只输出类型；位置由前一条进度日志定位。
        log(f"已停止；异常类型={type(error).__name__}")
        sys.exit(1)
