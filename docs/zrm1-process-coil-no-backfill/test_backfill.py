"""离线回归覆盖字符修正、批量边界、结构拒绝、预览和续跑参数；不连接数据库。"""

import argparse
import importlib.util
import sqlite3
import sys
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location("coil_no_backfill", Path(__file__).with_name("backfill.py"))
backfill = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(backfill)


class FakeCursor:
    """按预设顺序返回数据库结果，并记录 SQL 和游标释放次数。"""

    def __init__(self, connection):
        self.connection = connection

    def execute(self, sql):
        self.connection.statements.append(sql)
        if self.connection.fail_insert and sql.startswith("INSERT"):
            raise RuntimeError("模拟写入失败")

    def fetchone(self):
        return self.connection.results.pop(0)

    def fetchall(self):
        return self.connection.results.pop(0)

    def close(self):
        self.connection.closed_cursors += 1


class FakeConnection:
    """results 为每次取结果的返回值；fail_insert 控制写入异常路径。"""

    def __init__(self, results=(), fail_insert=False):
        self.results = list(results)
        self.fail_insert = fail_insert
        self.statements = []
        self.closed_cursors = 0
        self.closed = False

    def cursor(self):
        return FakeCursor(self)

    def close(self):
        self.closed = True


def schema_results(kind="NORMAL_TABLE", coil_type="VARCHAR(255)", precision="ms"):
    """构造参考脚本使用的 TDengine 毫秒普通表元数据。"""
    return [(precision,), [(kind, "ts", "TIMESTAMP"), (kind, "coil_no", coil_type)],
            [("ts", "TIMESTAMP", 8, ""), ("coil_no", "VARCHAR", 255, "")]]


class BackfillTest(unittest.TestCase):
    def test_substr_dynamic_length_has_explicit_integer_type(self):
        """回归 TDengine 算术参数类型错误；SQLite 不会拒绝原有表达式类型。"""
        self.assertIn("SUBSTR(coil_no, -3, 1)", backfill.MATCHING)
        self.assertIn("CAST(CHAR_LENGTH(coil_no) - 3 AS INT)", backfill.CORRECTED)
        self.assertIn("SUBSTR(coil_no, -2, 2)", backfill.CORRECTED)

    def test_error_summary_excludes_connection_details(self):
        error = RuntimeError("[0x2802] Invalid parameter data type : substr ws://private-secret")
        summary = backfill.error_summary(error)
        self.assertIn("0x2802", summary)
        self.assertIn("函数参数类型不受支持", summary)
        self.assertNotIn("private-secret", summary)

    def test_character_expression_preserves_other_characters(self):
        """SQLite 仅验证通用字符串表达式，不代替 TDengine 方言及更新语义验证。"""
        connection = sqlite3.connect(":memory:")
        connection.create_function("CHAR_LENGTH", 1, lambda value: None if value is None else len(value))
        connection.create_function("CONCAT", -1, lambda *values: "".join(values))
        try:
            cases = {"ABC301": "ABC201", "301": "201", "33333": "33233",
                     "卷号301": "卷号201", "ABC201": None, "ABC031": None,
                     "ABC3001": None, "": None, "3": None, "31": None, None: None,
                     "A3  ": "A2  "}
            for original, expected in cases.items():
                with self.subTest(original=original):
                    row = connection.execute(
                        f"SELECT {backfill.CORRECTED} FROM (SELECT ? AS coil_no) WHERE {backfill.MATCHING}",
                        (original,)).fetchone()
                    self.assertEqual(expected, row[0] if row else None)
        finally:
            connection.close()

    def test_preview_and_empty_window_never_write(self):
        for count, apply in [(4, False), (0, True)]:
            connection = FakeConnection([(count,)])
            with patch.object(backfill, "log"):
                self.assertEqual(count, backfill.process_window(connection, 1000, 2000, 10, apply, {}))
            self.assertFalse(any(sql.startswith("INSERT") for sql in connection.statements))

    def test_apply_writes_only_target_columns_and_rechecks(self):
        connection = FakeConnection([(2,), (0,)])
        with patch.object(backfill, "log"):
            self.assertEqual(2, backfill.process_window(connection, 1000, 2000, 10, True, {}))
        self.assertEqual(3, len(connection.statements))
        insert = connection.statements[1]
        self.assertIn("digital_coil.`zrm1_process_default` (ts, coil_no) SELECT ts,", insert)
        self.assertIn("ts >= 1000 AND ts < 2000", insert)
        self.assertIn(backfill.MATCHING, insert)
        self.assertEqual(connection.statements[0], connection.statements[2])
        self.assertEqual(3, connection.closed_cursors)

    def test_split_windows_have_no_gaps_or_overlap(self):
        connection = FakeConnection([(12,), (5,), (0,), (7,), (0,)])
        with patch.object(backfill, "log"):
            self.assertEqual(12, backfill.process_window(connection, 1000, 2000, 10, True, {}))
        inserts = [sql for sql in connection.statements if sql.startswith("INSERT")]
        self.assertEqual(2, len(inserts))
        self.assertIn("ts >= 1000 AND ts < 1500", inserts[0])
        self.assertIn("ts >= 1500 AND ts < 2000", inserts[1])

    def test_unsplittable_window_and_failed_recheck_stop(self):
        with patch.object(backfill, "log"):
            with self.assertRaises(ValueError):
                backfill.process_window(FakeConnection([(11,)]), 1000, 1001, 10, True, {})
            with self.assertRaisesRegex(RuntimeError, "仍有 1 行"):
                backfill.process_window(FakeConnection([(1,), (1,)]), 1000, 2000, 10, True, {})

    def test_write_error_closes_cursor_without_retry(self):
        connection = FakeConnection([(1,)], fail_insert=True)
        with patch.object(backfill, "log"), self.assertRaises(RuntimeError):
            backfill.process_window(connection, 1000, 2000, 10, True, {})
        self.assertEqual(2, len(connection.statements))
        self.assertEqual(2, connection.closed_cursors)

    def test_schema_validation_accepts_strings_and_rejects_unsafe_schema(self):
        for kind in ["VARCHAR(255)", "NCHAR(255)", "BINARY(255)"]:
            backfill.validate_schema(FakeConnection(schema_results(coil_type=kind)))
        for arguments in [{"kind": "CHILD_TABLE"}, {"kind": "SUPER_TABLE"},
                          {"coil_type": "INT"}, {"precision": "us"}]:
            with self.subTest(arguments=arguments), self.assertRaises(ValueError):
                backfill.validate_schema(FakeConnection(schema_results(**arguments)))
        results = schema_results()
        results[2].append(("other_key", "INT", 4, "COMPOSITE KEY"))
        with self.assertRaisesRegex(ValueError, "第二主键"):
            backfill.validate_schema(FakeConnection(results))

    def test_time_bounds_include_last_millisecond_and_handle_empty_table(self):
        self.assertEqual((1000, 2001), backfill.time_bounds(FakeConnection([(1000,), (2000,)])))
        self.assertIsNone(backfill.time_bounds(FakeConnection([None])))

    def test_shanghai_time_and_invalid_precision(self):
        self.assertEqual(0, backfill.parse_time("1970-01-01 08:00:00"))
        self.assertEqual(1, backfill.parse_time("1970-01-01 08:00:00.001"))
        for value in ["invalid", "2026-10-21T00:00:00Z", "2026-10-21 00:00:00.000001"]:
            with self.assertRaises(argparse.ArgumentTypeError):
                backfill.parse_time(value)

    def test_invalid_arguments_stop_before_connecting(self):
        for args in [[], ["--max-rows", "0"], ["--progress-seconds", "nan"],
                     ["--start", "2026-10-22", "--end", "2026-10-21"]]:
            with patch.object(backfill, "TD_DSN", ""), patch("sys.stderr"):
                with self.assertRaises(SystemExit) as error:
                    backfill.main(args)
                self.assertEqual(2, error.exception.code)

    def test_explicit_range_can_resume_without_reading_whole_table_bounds(self):
        connection = FakeConnection(schema_results() + [(0,)])
        with patch.object(backfill, "TD_DSN", "test-only"), patch.dict(
                sys.modules, {"taosws": SimpleNamespace(connect=lambda _: connection)}), patch.object(backfill, "log"):
            self.assertEqual(0, backfill.main(["--start", "2026-10-21 00:00:00",
                                               "--end", "2026-10-21 01:00:00"]))
        self.assertFalse(any("ORDER BY" in sql for sql in connection.statements))
        self.assertTrue(connection.closed)


if __name__ == "__main__":
    unittest.main()
