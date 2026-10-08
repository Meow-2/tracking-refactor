"""cell_code 分段补全的离线回归测试，不连接生产数据库。"""

import importlib.util
import sys
import threading
import types
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch


SCRIPT = Path(__file__).with_name("backfill.py")
spec = importlib.util.spec_from_file_location("cell_code_backfill", SCRIPT)
backfill = importlib.util.module_from_spec(spec)
spec.loader.exec_module(backfill)


class BackfillTest(unittest.TestCase):
    def test_table_list_and_code_rule(self):
        tables = backfill.load_tables()
        self.assertEqual(92, len(tables))
        self.assertEqual("CP1", tables["cp1_process_sf"])
        self.assertEqual("CBL1", tables["cbl1_process_default"])
        self.assertNotIn("baf1_batch", tables)
        count, insert = backfill.window_sql("cp1_process_sf", "CP1", 1000, 2000)
        self.assertIn("ts >= 1000 AND ts < 2000", count)
        self.assertIn("cell_code IS NULL OR cell_code <> CONCAT('CP1'", count)
        self.assertIn("WHEN pass_no IS NULL THEN '001'", insert)
        self.assertIn("INSERT INTO digital_coil.`cp1_process_sf` (ts, cell_code)", insert)

    def test_table_requires_coil_no_and_pass_no(self):
        connection = MagicMock()
        cursor = connection.cursor.return_value
        rows = [
            ("ts", "TIMESTAMP", 8, ""),
            ("coil_no", "VARCHAR", 255, ""),
            ("pass_no", "INT", 4, ""),
            ("cell_code", "VARCHAR", 255, ""),
        ]
        cursor.fetchall.return_value = rows
        self.assertTrue(backfill.validate_table(connection, "cp1_process_sf"))
        cursor.fetchall.return_value = [row for row in rows if row[0] != "coil_no"]
        self.assertFalse(backfill.validate_table(connection, "cp1_process_sf"))
        cursor.fetchall.return_value = [row for row in rows if row[0] != "pass_no"]
        self.assertFalse(backfill.validate_table(connection, "cp1_process_sf"))

    def test_shanghai_time_boundary(self):
        self.assertEqual(0, backfill.parse_time("1970-01-01 08:00:00.000"))
        self.assertEqual("1970-01-01 08:00:00.001", backfill.format_time(1))

    def test_overloaded_window_is_split_without_writing_parent(self):
        observed = []

        def fake_execute(_connection, sql):
            observed.append(sql)
            if sql.startswith("SELECT"):
                # 父窗口超限，两个子窗口分别只需一次写入。
                return (5,) if "ts >= 0 AND ts < 4" in sql else (2,)
            return None

        with patch.object(backfill, "execute", side_effect=fake_execute):
            changed = backfill.process_window(None, "cp1_process_sf", "CP1", 0, 4, 3, 0, False)
        self.assertEqual(4, changed)
        self.assertEqual(3, len(observed))
        self.assertTrue(all(sql.startswith("SELECT") for sql in observed))

    def test_write_checks_remaining_rows(self):
        results = iter([(1,), None, (0,)])
        with patch.object(backfill, "execute", side_effect=lambda *_: next(results)) as execute:
            changed = backfill.process_window(None, "cp1_process_sf", "CP1", 0, 1, 3, 0, True)
        self.assertEqual(1, changed)
        self.assertEqual(3, execute.call_count)

    def test_heartbeat_reports_current_phase_while_query_blocks(self):
        status = {"current": ("窗口 2/5 统计待更新行数", backfill.time.monotonic())}
        with patch.object(backfill, "log_progress") as log:
            with backfill.progress_heartbeat("[1/92] cp1_process_sf", status, 0.01):
                threading.Event().wait(0.05)
        messages = [call.args[0] for call in log.call_args_list]
        self.assertTrue(any("窗口 2/5 统计待更新行数" in message
                            and "仍在运行" in message for message in messages))

    def test_window_updates_heartbeat_phase_before_each_query(self):
        status = {"current": ("", 0)}
        results = iter([(1,), None, (0,)])
        phases = []

        def fake_execute(_connection, _sql):
            phases.append(status["current"][0])
            return next(results)

        with patch.object(backfill, "execute", side_effect=fake_execute):
            backfill.process_window(None, "cp1_process_sf", "CP1", 0, 1, 3, 0, True,
                                    status, "窗口 1/1")
        self.assertIn("统计待更新行数", phases[0])
        self.assertIn("写入 1 行", phases[1])
        self.assertIn("复查写入结果", phases[2])

    def test_main_reports_table_window_and_run_progress(self):
        connection = MagicMock()
        connector = types.SimpleNamespace(connect=lambda _dsn: connection)
        arguments = ["backfill.py", "--table", "cp1_process_sf",
                     "--start", "1970-01-01 08:00:00",
                     "--end", "1970-01-01 09:00:00", "--window-hours", "1"]
        with patch.dict(sys.modules, {"taosws": connector}), \
                patch.dict(backfill.os.environ, {"TD_DSN": "mock-dsn"}), \
                patch.object(sys, "argv", arguments), \
                patch.object(backfill, "load_tables", return_value={"cp1_process_sf": "CP1"}), \
                patch.object(backfill, "execute", return_value=("ms",)), \
                patch.object(backfill, "validate_table", return_value=True), \
                patch.object(backfill, "process_window", return_value=2), \
                patch.object(backfill, "log_progress") as log:
            self.assertEqual(0, backfill.main())
        messages = [call.args[0] for call in log.call_args_list]
        self.assertTrue(any("[1/1] cp1_process_sf 开始" in message for message in messages))
        self.assertTrue(any("窗口 1/1 完成；时间范围进度 100.0%" in message
                            for message in messages))
        self.assertTrue(any("全部完成：处理 1 张，跳过 0 张，预计更新 2 行" in message
                            for message in messages))


if __name__ == "__main__":
    unittest.main()
