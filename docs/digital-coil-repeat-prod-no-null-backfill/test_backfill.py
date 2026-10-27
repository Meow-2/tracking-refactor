"""离线回归：空值补一、窗口边界、结构校验和续跑；不连接数据库。"""

import argparse
import importlib.util
import sys
import types
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch


SPEC = importlib.util.spec_from_file_location("null_backfill", Path(__file__).with_name("backfill.py"))
backfill = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(backfill)


class BackfillTest(unittest.TestCase):
    def test_sql_only_fills_null_with_one_at_original_timestamp(self):
        count, insert = backfill.window_sql("baf1_batch", 1000, 2000)
        self.assertEqual(
            "INSERT INTO digital_coil.`baf1_batch` (ts, repeat_prod_no) "
            "SELECT ts, 1 FROM digital_coil.`baf1_batch` "
            "WHERE ts >= 1000 AND ts < 2000 AND repeat_prod_no IS NULL", insert)
        self.assertEqual(count.split(" WHERE ")[1], insert.split(" WHERE ")[1])
        self.assertNotIn("in_mat_prod_no", count + insert)

    def test_discovery_sorted_and_excludes_child_and_super_tables(self):
        rows = [("zrm1_process_default", "NORMAL_TABLE", "INT"),
                ("baf1_batch", "NORMAL_TABLE", "INT"),
                ("child_table", "CHILD_TABLE", "INT"),
                ("super_table", "SUPER_TABLE", "INT")]
        with patch.object(backfill, "execute", return_value=rows):
            self.assertEqual(["baf1_batch", "zrm1_process_default"], backfill.discover_tables(None))

    def test_discovery_rejects_incompatible_type_and_unsafe_name(self):
        for row in [("a_table", "NORMAL_TABLE", "VARCHAR"),
                    ("a`; DROP TABLE a", "NORMAL_TABLE", "INT")]:
            with self.subTest(row=row), patch.object(backfill, "execute", return_value=[row]):
                with self.assertRaises(ValueError):
                    backfill.discover_tables(None)

    def test_structure_requires_only_timestamp_and_normal_int(self):
        rows = [("ts", "TIMESTAMP", 8, ""), ("repeat_prod_no", "INT", 4, "")]
        with patch.object(backfill, "execute", return_value=rows):
            backfill.validate_table(None, "a_table")
        for invalid in [rows[:1], [rows[0], ("repeat_prod_no", "INT", 4, "TAG")],
                        [("ts", "BIGINT", 8, ""), rows[1]]]:
            with self.subTest(rows=invalid), patch.object(backfill, "execute", return_value=invalid):
                with self.assertRaises(ValueError):
                    backfill.validate_table(None, "a_table")

    def test_apply_executes_only_one_insert_without_pre_read_or_recheck(self):
        with patch.object(backfill, "execute") as execute, patch.object(backfill, "log_progress"):
            self.assertIsNone(backfill.process_window(None, "a_table", 0, 100, 0, True))
        execute.assert_called_once_with(None, backfill.window_sql("a_table", 0, 100)[1])

    def test_preview_only_counts_without_writing(self):
        for count in [0, 20000]:
            with self.subTest(count=count), patch.object(backfill, "execute", return_value=(count,)) as execute, \
                    patch.object(backfill, "log_progress"):
                self.assertEqual(count, backfill.process_window(None, "a_table", 0, 100, 0, False))
            execute.assert_called_once_with(None, backfill.window_sql("a_table", 0, 100)[0])

    def test_write_error_stops_without_wait_or_further_queries(self):
        with patch.object(backfill, "execute", side_effect=RuntimeError("mock-error")) as execute, \
                patch.object(backfill.time, "sleep") as sleep, self.assertRaises(RuntimeError):
            backfill.process_window(None, "a_table", 0, 100, 1, True)
        self.assertEqual(1, execute.call_count)
        sleep.assert_not_called()

    def test_successful_write_waits_only_when_requested(self):
        for pause in [0, 1]:
            with self.subTest(pause=pause), patch.object(backfill, "execute"), \
                    patch.object(backfill, "log_progress"), patch.object(backfill.time, "sleep") as sleep:
                backfill.process_window(None, "a_table", 0, 100, pause, True)
                self.assertEqual(1 if pause else 0, sleep.call_count)

    def test_direct_insert_preserves_non_null_values_and_end_boundary(self):
        # 模拟稳定历史行，验证直接写入与重跑均只影响范围内的空值。
        import re
        values = {0: None, 1: 0, 2: 3, 10: None, 99: None, 100: None}
        original = dict(values)

        def write(_connection, sql):
            self.assertTrue(sql.startswith("INSERT"))
            start, end = map(int, re.search(r"ts >= (\d+) AND ts < (\d+)", sql).groups())
            for ts, value in values.items():
                if start <= ts < end and value is None:
                    values[ts] = 1

        with patch.object(backfill, "execute", side_effect=write) as execute, patch.object(backfill, "log_progress"):
            backfill.process_window(None, "a_table", 0, 100, 0, True)
            backfill.process_window(None, "a_table", 0, 100, 0, True)
        self.assertEqual({ts: 1 if value is None and ts < 100 else value for ts, value in original.items()}, values)
        self.assertEqual(2, execute.call_count)

    def test_shanghai_time_and_invalid_precision(self):
        self.assertEqual(0, backfill.parse_time("1970-01-01 08:00:00"))
        self.assertEqual("1970-01-01 08:00:00.001", backfill.format_time(1))
        for value in ["2026-10-26 00:00:00", "2026-10-26 12:30:00", "2026-10-26 23:59:59.999"]:
            self.assertEqual(backfill.parse_time("2026-10-26 00:00:00"),
                             backfill.day_start(backfill.parse_time(value)))
        for value in ["bad-time", "2026-01-01 00:00:00+08:00", "2026-01-01 00:00:00.000001"]:
            with self.subTest(value=value), self.assertRaises(argparse.ArgumentTypeError):
                backfill.parse_time(value)

    def test_invalid_arguments_stop_before_connecting(self):
        end = ["--end", "1970-01-01 10:00:00"]
        for arguments in [["--max-rows", "0"], ["--pause", "-1"], ["--window-days", "0"], ["--window-hours", "0"],
                          ["--window-days", "1", "--window-hours", "1"],
                          ["--progress-seconds", "0"], ["--resume-start", "1970-01-01 08:00:00"],
                          ["--resume-table", "a_table", "--table", "a_table"],
                          ["--start", "1970-01-01 10:00:00"]]:
            with self.subTest(arguments=arguments), patch("sys.stderr"), self.assertRaises(SystemExit) as error:
                backfill.main(end + arguments)
            self.assertEqual(2, error.exception.code)
        for dsn in ["", "ws://<user>:<password>@<host>:6041"]:
            with self.subTest(dsn=dsn), patch.object(backfill, "TD_DSN", dsn), \
                    patch("sys.stderr"), self.assertRaises(SystemExit):
                backfill.main(end)

    def test_resume_start_only_applies_to_first_table_and_connection_closes(self):
        connection = MagicMock()
        driver = types.SimpleNamespace(connect=lambda _dsn: connection)

        def query(_connection, sql, all_rows=False):
            return ("ms",) if "ins_databases" in sql else (3600000,)

        with patch.object(backfill, "TD_DSN", "mock-dsn"), \
                patch.dict(sys.modules, {"taosws": driver}), \
                patch.object(backfill, "discover_tables", return_value=["a_table", "b_table", "c_table"]), \
                patch.object(backfill, "validate_table"), \
                patch.object(backfill, "execute", side_effect=query), \
                patch.object(backfill, "process_window", return_value=0) as process, \
                patch.object(backfill, "log_progress"):
            self.assertEqual(0, backfill.main([
                "--resume-table", "b_table", "--resume-start", "1970-01-01 08:00:00",
                "--end", "1970-01-01 10:00:00", "--window-days", "1"]))
        self.assertEqual([("b_table", 0, 7200000), ("c_table", 3600000, 7200000)],
                         [call.args[1:4] for call in process.call_args_list])
        connection.close.assert_called_once()

    def test_cursor_closes_after_driver_error(self):
        connection = MagicMock()
        connection.cursor.return_value.execute.side_effect = RuntimeError("mock-error")
        with self.assertRaises(RuntimeError):
            backfill.execute(connection, "SELECT 1")
        connection.cursor.return_value.close.assert_called_once()

    def test_main_writes_adjacent_windows_without_any_null_check(self):
        connection = MagicMock()
        driver = types.SimpleNamespace(connect=lambda _dsn: connection)
        statements = []

        def execute(_connection, sql, all_rows=False):
            statements.append(sql)
            if "ins_databases" in sql:
                return ("ms",)
            return (0,) if sql.startswith("SELECT COUNT") else None

        with patch.object(backfill, "TD_DSN", "mock-dsn"), \
                patch.dict(sys.modules, {"taosws": driver}), \
                patch.object(backfill, "discover_tables", return_value=["a_table"]), \
                patch.object(backfill, "validate_table"), \
                patch.object(backfill, "execute", side_effect=execute), \
                patch.object(backfill, "log_progress"):
            self.assertEqual(0, backfill.main([
                "--start", "1970-01-01 08:00:00", "--end", "1970-01-03 10:30:00",
                "--window-days", "1", "--max-rows", "1", "--apply"]))
        self.assertEqual(["SELECT", "INSERT", "INSERT", "INSERT"],
                         [sql.split()[0] for sql in statements])
        for sql, (start, end) in zip(statements[1:], [(0, 57600000), (57600000, 144000000),
                                                     (144000000, 181800000)]):
            self.assertIn(f"ts >= {start} AND ts < {end}", sql)
        self.assertFalse(any("COUNT(*)" in sql for sql in statements))
        connection.close.assert_called_once()

    def test_default_two_hour_windows_cover_partial_first_and_last_windows(self):
        connection = MagicMock()
        driver = types.SimpleNamespace(connect=lambda _dsn: connection)
        statements = []

        def execute(_connection, sql, all_rows=False):
            statements.append(sql)
            return ("ms",) if "ins_databases" in sql else (0,)

        with patch.object(backfill, "TD_DSN", "mock-dsn"), \
                patch.dict(sys.modules, {"taosws": driver}), \
                patch.object(backfill, "discover_tables", return_value=["a_table"]), \
                patch.object(backfill, "validate_table"), \
                patch.object(backfill, "execute", side_effect=execute), \
                patch.object(backfill, "log_progress"):
            self.assertEqual(0, backfill.main([
                "--start", "1970-01-01 08:15:00", "--end", "1970-01-01 12:30:00", "--apply"]))
        inserts = [sql for sql in statements if sql.startswith("INSERT")]
        self.assertEqual(3, len(inserts))
        for sql, (start, end) in zip(inserts, [(900000, 7200000), (7200000, 14400000), (14400000, 16200000)]):
            self.assertIn(f"ts >= {start} AND ts < {end}", sql)
        self.assertEqual(4, len(statements))
        self.assertFalse(any("COUNT(*)" in sql for sql in statements))


if __name__ == "__main__":
    unittest.main()
