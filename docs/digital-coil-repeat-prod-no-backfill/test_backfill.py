"""离线校验表发现、填充条件和删列前的全量校验。"""

import importlib.util
import os
import unittest
from pathlib import Path
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location("repeat_backfill", Path(__file__).with_name("backfill.py"))
backfill = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(backfill)


class FakeCursor:
    def __init__(self, connection):
        self.connection = connection
        self.sql = ""

    def execute(self, sql):
        self.sql = sql
        self.connection.statements.append(sql)

    def fetchone(self):
        if "COUNT(*)" in self.sql:
            return (self.connection.counts.pop(0),)
        if "ORDER BY ts ASC" in self.sql:
            return (1000,)
        if "ORDER BY ts DESC" in self.sql:
            return (2000,)
        return None

    def fetchall(self):
        return self.connection.columns

    def close(self):
        pass


class FakeConnection:
    def __init__(self, columns=(), counts=()):
        self.columns = columns
        self.counts = list(counts)
        self.statements = []

    def cursor(self):
        return FakeCursor(self)


class BackfillTest(unittest.TestCase):
    def test_missing_connection_string_stops_before_connecting(self):
        """缺少连接配置时立即停止，避免使用隐式默认凭据连接数据库。"""
        with patch.dict(os.environ, {}, clear=True), patch("sys.stderr"):
            with self.assertRaises(SystemExit) as error:
                backfill.main([])
        self.assertEqual(2, error.exception.code)

    def test_discover_only_complete_matching_normal_tables(self):
        connection = FakeConnection(columns=[
            ("a_table", "NORMAL_TABLE", "in_mat_prod_no", "INT"),
            ("a_table", "NORMAL_TABLE", "repeat_prod_no", "INT"),
            ("b_table", "NORMAL_TABLE", "in_mat_prod_no", "INT"),
        ])
        self.assertEqual(["a_table"], backfill.discover_tables(connection))

    def test_discover_rejects_incompatible_columns(self):
        connection = FakeConnection(columns=[
            ("a_table", "NORMAL_TABLE", "in_mat_prod_no", "INT"),
            ("a_table", "NORMAL_TABLE", "repeat_prod_no", "BIGINT"),
        ])
        with self.assertRaisesRegex(ValueError, "类型不一致"):
            backfill.discover_tables(connection)

    def test_discover_rejects_child_table(self):
        connection = FakeConnection(columns=[
            ("a_table", "CHILD_TABLE", "in_mat_prod_no", "INT"),
            ("a_table", "CHILD_TABLE", "repeat_prod_no", "INT"),
        ])
        with self.assertRaisesRegex(ValueError, "不是普通表"):
            backfill.discover_tables(connection)

    def test_fill_only_missing_target_and_rechecks(self):
        connection = FakeConnection(counts=[2, 0])
        with patch.object(backfill, "log"):
            self.assertEqual(2, backfill.fill_window(connection, "a_table", 1000, 2000, 10, True, {}))
        insert = next(sql for sql in connection.statements if sql.startswith("INSERT"))
        self.assertIn("(ts, repeat_prod_no) SELECT ts, in_mat_prod_no", insert)
        self.assertIn(backfill.MISSING, insert)
        self.assertIn("ts >= 1000 AND ts < 2000", insert)

    def test_preview_does_not_write(self):
        connection = FakeConnection(counts=[1])
        with patch.object(backfill, "log"):
            backfill.fill_window(connection, "a_table", 1000, 2000, 10, False, {})
        self.assertFalse(any(sql.startswith("INSERT") for sql in connection.statements))

    def test_failed_recheck_stops_window(self):
        connection = FakeConnection(counts=[1, 1])
        with patch.object(backfill, "log"), self.assertRaisesRegex(RuntimeError, "仍有 1 行"):
            backfill.fill_window(connection, "a_table", 1000, 2000, 10, True, {})

    def test_full_table_check_has_no_time_filter(self):
        connection = FakeConnection(counts=[1])
        self.assertEqual(1, backfill.count_missing(connection, "a_table"))
        self.assertNotIn("ts >=", connection.statements[0])


if __name__ == "__main__":
    unittest.main()
