"""补全脚本的离线回归测试；不连接生产 TDengine 或 PostgreSQL。"""

import argparse
import copy
import csv
import importlib.util
import io
import json
import sqlite3
import tempfile
import threading
import unittest
from contextlib import contextmanager, redirect_stderr
from datetime import datetime, timedelta
from pathlib import Path
from unittest.mock import patch

HERE = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location("prod_no_backfill", HERE / "backfill.py")
backfill = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(backfill)


class QueryResult:
    def __init__(self, rows):
        self.rows = rows

    def fetchone(self):
        return self.rows[0] if self.rows else None

    def fetchall(self):
        return self.rows


class FakeTdCursor:
    def __init__(self, connection):
        self.connection = connection
        self.rows = []

    def execute(self, sql):
        self.connection.queries.append(sql)
        self.rows = list(self.connection.rows_for(sql))

    def fetchmany(self, size):
        result, self.rows = self.rows[:size], self.rows[size:]
        return result

    def close(self):
        pass


class FakeTd:
    def __init__(self):
        self.queries = []

    def cursor(self):
        return FakeTdCursor(self)

    def rows_for(self, sql):
        if "information_schema.ins_databases" in sql:
            return [("digital_coil", "ms")]
        if "information_schema.ins_columns" in sql:
            configured = [
                (table, "NORMAL_TABLE", column, column_type)
                for table in backfill.PROCESS_TABLES.values()
                for column, column_type in (("ts", "TIMESTAMP"),
                                            ("coil_no", "VARCHAR(255)"),
                                            ("in_mat_prod_no", "INT"))
            ]
            return configured + [
                ("cp1_process_sf", "NORMAL_TABLE", "ts", "TIMESTAMP"),
                ("cp1_process_sf", "NORMAL_TABLE", "coil_no", "VARCHAR(255)"),
                ("cp1_process_sf", "NORMAL_TABLE", "in_mat_prod_no", "INT"),
            ]
        if sql.startswith("SELECT MIN(CAST(ts AS BIGINT))"):
            return [(backfill.epoch_ms(datetime(2026, 1, 1)),
                     backfill.epoch_ms(datetime(2026, 1, 2)))]
        if "cp1_process_nof" in sql:
            if "SESSION(ts, 3600s)" in sql:
                return [(backfill.epoch_ms(datetime(2026, 1, 1, 1)),
                         backfill.epoch_ms(datetime(2026, 1, 1, 4)), 10)]
            if "STATE_WINDOW(coil_no)" in sql:
                return [(backfill.epoch_ms(datetime(2026, 1, 1, 1)),
                         backfill.epoch_ms(datetime(2026, 1, 1, 4)), 10, "A", 1, 1)]
            raise AssertionError(sql)
        if "cp1_process_sf" in sql:
            raise AssertionError("不应扫描未指定的过程表")
        if "FROM digital_coil." in sql:
            return []
        raise AssertionError(sql)


class FakePostgres:
    """模拟本脚本实际使用的 PG 查询，并在异常时回滚单卷事务。"""

    def __init__(self):
        self.records = {}
        self.old_max = {}
        self.table_exists = {"qm_dc_repeat_prod_no_log", "qm_dc_product_no"}

    @contextmanager
    def transaction(self):
        before = copy.deepcopy(self.records)
        try:
            yield
        except Exception:
            self.records = before
            raise

    def execute(self, sql, params=None):
        if "to_regclass" in sql:
            return QueryResult([(params[0].split(".")[-1]
                                 if params[0].split(".")[-1] in self.table_exists else None,)])
        if "information_schema.columns" in sql:
            return QueryResult([("in_mat_product_no",)])
        if "SELECT id, in_mat_repeat_prod_no" in sql:
            key = params
            rows = [(ident, seq, created, deleted)
                    for (unit, coil, seq), (ident, created, deleted) in self.records.items()
                    if (unit, coil) == key]
            return QueryResult(sorted(rows, key=lambda row: row[1]))
        if "SELECT in_mat_product_no FROM" in sql:
            value = self.old_max.get(params)
            return QueryResult([] if value is None else [(value,)])
        if "WHERE id=%s" in sql:
            for key, row in self.records.items():
                if row[0] == params[0]:
                    return QueryResult([key])
            return QueryResult([])
        if "INSERT INTO public.qm_dc_repeat_prod_no_log" in sql:
            ident, unit, coil, seq, created, _user = params
            key = (unit, coil, seq)
            if key in self.records:
                raise ValueError("重复业务键")
            self.records[key] = (ident, created, 0)
            return QueryResult([])
        raise AssertionError(sql)


class BackfillTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.output = Path(self.temp.name)

    def stage(self):
        return backfill.create_stage(self.output / "stage.sqlite")

    def test_normalize_coil_matches_status_rules(self):
        self.assertEqual(backfill.normalize_coil("  A01\u200b "), "A01")
        self.assertIsNone(backfill.normalize_coil("................"))
        self.assertIsNone(backfill.normalize_coil("Request Color 123"))
        self.assertEqual(backfill.normalize_coil("A01.B"), "A01.B")

    def test_epoch_milliseconds_preserve_shanghai_local_time(self):
        local = datetime(2026, 10, 3, 16, 59, 0)
        self.assertEqual(backfill.epoch_ms(local), 1791017940000)
        self.assertEqual(backfill.timestamp_text(1791017940003),
                         "2026-10-03 16:59:00.003000")

    def test_start_is_parsed_as_shanghai_local_time(self):
        self.assertEqual(backfill.parse_shanghai_start("2026-09-03"),
                         datetime(2026, 9, 3))
        self.assertEqual(backfill.parse_shanghai_start("2026-09-03 12:30:00"),
                         datetime(2026, 9, 3, 12, 30))
        with self.assertRaises(argparse.ArgumentTypeError):
            backfill.parse_shanghai_start("2026-09-03T00:00:00+08:00")

    def test_heartbeat_reports_while_a_query_is_waiting(self):
        reported = threading.Event()
        messages = []

        def capture(message):
            messages.append(message)
            if "仍在运行" in message:
                reported.set()

        status = {"phase": "查询停采段", "sessions_done": 0, "sessions_total": "?",
                  "state_windows": 0, "sequence_queries": 0, "samples": 0}
        with patch.object(backfill, "log_progress", side_effect=capture):
            with backfill.progress_heartbeat("测试窗口", status, interval_seconds=0.01):
                self.assertTrue(reported.wait(timeout=1))
        self.assertTrue(any("阶段=查询停采段" in message for message in messages))
        self.assertIn("完成", messages[-1])

    def test_explicit_start_skips_full_table_bounds_query(self):
        source = FakeTd()
        stage = self.stage()
        try:
            backfill.scan_process(source, stage,
                [("cp1_process_nof", "CP1", "in_mat_prod_no")],
                datetime(2026, 1, 2), 1, datetime(2026, 1, 1))
            manifest = backfill.finalize(stage, self.output, datetime(2026, 1, 2),
                [("cp1_process_nof", "CP1", "in_mat_prod_no")], datetime(2026, 1, 1))
        finally:
            stage.close()
        self.assertFalse(any(sql.startswith("SELECT MIN(CAST(ts AS BIGINT))")
                             for sql in source.queries))
        self.assertEqual(manifest["start_shanghai"], "2026-01-01 00:00:00.000000")

    def test_configured_table_rejects_ambiguous_sequence_columns(self):
        columns = backfill.table_columns(FakeTd(), "digital_coil")
        columns["cp1_process_nof"]["repeat_prod_no"] = ("NORMAL_TABLE", "INT")
        with patch.object(backfill, "table_columns", return_value=columns):
            with self.assertRaisesRegex(ValueError, "恰有一个"):
                backfill.configured_process_tables(FakeTd())

    def test_configured_table_requires_every_selected_source(self):
        columns = backfill.table_columns(FakeTd(), "digital_coil")
        del columns["fcl1_process_ctf"]
        with patch.object(backfill, "table_columns", return_value=columns):
            with self.assertRaisesRegex(ValueError, "fcl1_process_ctf"):
                backfill.configured_process_tables(FakeTd())

    def test_precision_must_be_milliseconds(self):
        class WrongPrecision(FakeTd):
            def rows_for(self, sql):
                if "information_schema.ins_databases" in sql:
                    return [("digital_coil", "us")]
                return super().rows_for(sql)

        with self.assertRaisesRegex(ValueError, "必须为 ms"):
            backfill.validate_precision(WrongPrecision())

    def test_finalize_deduplicates_and_uses_earliest_window_time(self):
        stage = self.stage()
        stage.execute("INSERT INTO evidence VALUES ('CP1','A',1,'2026-01-02 12:00:00','2026-01-02 12:05:00')")
        stage.execute(
            """INSERT INTO evidence VALUES ('CP1','A',1,'2026-01-01 12:00:00','2026-01-01 12:05:00')
            ON CONFLICT(unit, coil, seq) DO UPDATE SET
            first_ts=min(first_ts,excluded.first_ts), last_ts=max(last_ts,excluded.last_ts)"""
        )
        stage.commit()
        stage.execute(
            "INSERT INTO segment(unit,coil,seq,start_ts,end_ts,sample_count,table_name) "
            "SELECT unit,coil,seq,first_ts,last_ts,1,'cp1_process_nof' FROM evidence")
        manifest = backfill.finalize(stage, self.output, datetime(2026, 1, 3), [("cp1_process_nof", "CP1", "in_mat_prod_no")])
        stage.close()
        self.assertEqual(manifest["candidate_count"], 1)
        with (self.output / "candidates.csv").open(encoding="utf-8-sig", newline="") as stream:
            rows = list(csv.DictReader(stream))
        self.assertEqual(rows[0]["create_time"], "2026-01-01 12:00:00")

    def test_scan_process_uses_one_selected_table_per_unit(self):
        source = FakeTd()
        backfill.validate_precision(source)
        tables = backfill.configured_process_tables(source)
        self.assertEqual(len(tables), 6)
        self.assertIn(("cp1_process_nof", "CP1", "in_mat_prod_no"), tables)
        self.assertNotIn("cp1_process_sf", [table for table, _, _ in tables])
        stage = self.stage()
        progress = io.StringIO()
        try:
            with redirect_stderr(progress):
                backfill.scan_process(source, stage, tables, datetime(2026, 1, 2), 1)
            row = stage.execute("SELECT first_ts, last_ts FROM evidence").fetchone()
            segment = stage.execute(
                "SELECT start_ts, end_ts, sample_count FROM segment").fetchone()
        finally:
            stage.close()
        self.assertEqual(row, ("2026-01-01 01:00:00.000000",
                               "2026-01-01 04:00:00.000000"))
        self.assertFalse(any("cube" in sql.lower() for sql in source.queries))
        self.assertEqual(len([sql for sql in source.queries if "SESSION(ts, 3600s)" in sql]), 6)
        state_queries = [sql for sql in source.queries if "STATE_WINDOW(coil_no)" in sql]
        self.assertEqual(len(state_queries), 1)
        self.assertTrue(all("CAST(_wstart AS BIGINT)" in sql
                            and "CAST(_wend AS BIGINT)" in sql
                            for sql in state_queries))
        self.assertEqual(segment, ("2026-01-01 01:00:00.000000",
                                   "2026-01-01 04:00:00.000000", 10))
        self.assertIn("CBL1/cbl1_process_default 窗口 1/1", progress.getvalue())
        self.assertIn("停采段=1，卷号段=1，序号细分查询=0，采样数=10", progress.getvalue())

    def test_mixed_sequence_window_is_split_by_sequence(self):
        class MixedTd(FakeTd):
            def rows_for(self, sql):
                if "cp1_process_nof" in sql and "SESSION(ts, 3600s)" in sql:
                    return [(backfill.epoch_ms(datetime(2026, 1, 1, 1)),
                             backfill.epoch_ms(datetime(2026, 1, 1, 2)), 6)]
                if "cp1_process_nof" in sql and "STATE_WINDOW(coil_no)" in sql:
                    return [(backfill.epoch_ms(datetime(2026, 1, 1, 1)),
                             backfill.epoch_ms(datetime(2026, 1, 1, 2)), 6, "A", 1, 2)]
                if "cp1_process_nof" in sql and "STATE_WINDOW(`in_mat_prod_no`)" in sql:
                    return [
                        (backfill.epoch_ms(datetime(2026, 1, 1, 1)),
                         backfill.epoch_ms(datetime(2026, 1, 1, 1, 20)), 3, "A", 1),
                        (backfill.epoch_ms(datetime(2026, 1, 1, 1, 30)),
                         backfill.epoch_ms(datetime(2026, 1, 1, 2)), 3, "A", 2),
                    ]
                return super().rows_for(sql)

        stage = self.stage()
        try:
            source = MixedTd()
            backfill.scan_process(source, stage,
                [("cp1_process_nof", "CP1", "in_mat_prod_no")], datetime(2026, 1, 2), 1)
            segments = stage.execute(
                "SELECT seq, sample_count FROM segment ORDER BY start_ts").fetchall()
        finally:
            stage.close()
        self.assertEqual(segments, [(1, 3), (2, 3)])
        self.assertTrue(any("STATE_WINDOW(`in_mat_prod_no`)" in sql for sql in source.queries))

    def test_long_sampling_gap_inside_unchanged_state_creates_two_segments(self):
        class GapTd(FakeTd):
            def rows_for(self, sql):
                if "cp1_process_nof" in sql and "SESSION(ts, 3600s)" in sql:
                    return [
                        (backfill.epoch_ms(datetime(2026, 1, 1, 1)),
                         backfill.epoch_ms(datetime(2026, 1, 1, 1, 10)), 3),
                        (backfill.epoch_ms(datetime(2026, 1, 1, 4)),
                         backfill.epoch_ms(datetime(2026, 1, 1, 4, 10)), 4),
                    ]
                if "cp1_process_nof" in sql and "STATE_WINDOW(coil_no)" in sql:
                    if f"ts >= {backfill.epoch_ms(datetime(2026, 1, 1, 1))}" in sql:
                        return [(backfill.epoch_ms(datetime(2026, 1, 1, 1)),
                                 backfill.epoch_ms(datetime(2026, 1, 1, 1, 10)), 3, "A", 1, 1)]
                    return [(backfill.epoch_ms(datetime(2026, 1, 1, 4)),
                             backfill.epoch_ms(datetime(2026, 1, 1, 4, 10)), 4, "A", 1, 1)]
                return super().rows_for(sql)

        stage = self.stage()
        try:
            source = GapTd()
            backfill.scan_process(source, stage,
                [("cp1_process_nof", "CP1", "in_mat_prod_no")], datetime(2026, 1, 2), 1)
            segments = stage.execute(
                "SELECT start_ts, end_ts, sample_count FROM segment ORDER BY start_ts").fetchall()
        finally:
            stage.close()
        self.assertEqual(segments, [
            ("2026-01-01 01:00:00.000000", "2026-01-01 01:10:00.000000", 3),
            ("2026-01-01 04:00:00.000000", "2026-01-01 04:10:00.000000", 4),
        ])

    def test_adjacent_same_key_segments_merge_but_gap_stays_separate(self):
        stage = self.stage()
        try:
            previous = None
            base = datetime(2026, 1, 1)
            for first_minute, last_minute in [(0, 10), (11, 20), (120, 130)]:
                # 前两段相邻，第三段与前段相隔超过一小时。
                first = backfill.epoch_ms(base + timedelta(minutes=first_minute))
                last = backfill.epoch_ms(base + timedelta(minutes=last_minute))
                previous = backfill.record_process_segment(
                    stage, "cp1_process_nof", "CP1", "A", 1,
                    first, last, 3, previous)
            rows = stage.execute(
                "SELECT sample_count FROM segment ORDER BY start_ts").fetchall()
        finally:
            stage.close()
        self.assertEqual(rows, [(6,), (3,)])

    def test_finalize_blocks_gap_and_time_overlap(self):
        stage = self.stage()
        stage.executemany("INSERT INTO evidence VALUES (?, ?, ?, ?, ?)", [
            ("CP1", "GAP", 1, "2026-01-01 00:00:00", "2026-01-01 00:10:00"),
            ("CP1", "GAP", 3, "2026-01-02 00:00:00", "2026-01-02 00:10:00"),
            ("CP1", "OVER", 1, "2026-01-01 00:00:00", "2026-01-02 00:00:00"),
            ("CP1", "OVER", 2, "2026-01-01 23:00:00", "2026-01-02 01:00:00"),
            ("CP1", "VALID", 1, "2026-01-01 00:00:00", "2026-01-01 01:00:00"),
        ])
        stage.execute(
            "INSERT INTO segment(unit,coil,seq,start_ts,end_ts,sample_count,table_name) "
            "SELECT unit,coil,seq,first_ts,last_ts,1,'cp1_process_nof' FROM evidence")
        stage.commit()
        manifest = backfill.finalize(stage, self.output, datetime(2026, 1, 3), [])
        stage.close()
        self.assertEqual(manifest["candidate_count"], 1)
        self.assertEqual(manifest["blocked_coils"], 2)
        anomalies = (self.output / "anomalies.csv").read_text(encoding="utf-8-sig")
        self.assertIn("sequence_gap", anomalies)
        self.assertIn("sequence_overlap", anomalies)
        self.assertNotIn("suspected_missing_por", anomalies)

    def candidates(self, values):
        backfill.write_csv(
            self.output / "candidates.csv",
            ["unit_code", "in_mat_no", "in_mat_repeat_prod_no", "create_time", "segment_count"],
            [(*value, 1) for value in values],
        )
        backfill.write_csv(
            self.output / "segments.csv",
            ["unit_code", "in_mat_no", "in_mat_repeat_prod_no", "segment_no",
             "start_ts", "end_ts", "sample_count", "source_table"],
            [(unit, coil, seq, 1, created, created, 1, "cp1_process_nof")
             for unit, coil, seq, created in values],
        )
        content = (self.output / "candidates.csv").read_bytes()
        segments = (self.output / "segments.csv").read_bytes()
        import hashlib
        (self.output / "manifest.json").write_text(json.dumps({
            "preview_version": backfill.PREVIEW_VERSION,
            "cutoff_shanghai": "2026-01-03 00:00:00",
            "candidate_count": len(values),
            "segment_count": len(values),
            "candidate_sha256": hashlib.sha256(content).hexdigest(),
            "segments_sha256": hashlib.sha256(segments).hexdigest(),
        }), encoding="utf-8")

    def test_apply_first_and_second_run_are_idempotent(self):
        self.candidates([
            ("CP1", "A", 1, "2026-01-01 00:00:00"),
            ("CP1", "A", 2, "2026-01-02 00:00:00"),
        ])
        pg = FakePostgres()
        pg.old_max[("CP1", "A")] = 2
        first = backfill.apply(self.output, pg)
        self.assertEqual(first, [("CP1", "A", "inserted", "2")])
        original = copy.deepcopy(pg.records)
        second = backfill.apply(self.output, pg)
        self.assertEqual(second, [("CP1", "A", "inserted", "0")])
        self.assertEqual(pg.records, original)

    def test_apply_blocks_old_count_conflict_and_preserves_existing_rows(self):
        self.candidates([("CP1", "A", 1, "2026-01-01 00:00:00")])
        pg = FakePostgres()
        pg.old_max[("CP1", "A")] = 2
        result = backfill.apply(self.output, pg)
        self.assertEqual(result[0][2], "blocked")
        self.assertEqual(pg.records, {})

    def test_apply_rejects_changed_preview(self):
        self.candidates([("CP1", "A", 1, "2026-01-01 00:00:00")])
        with (self.output / "candidates.csv").open("ab") as stream:
            stream.write(b"changed")
        with self.assertRaises(ValueError):
            backfill.apply(self.output, FakePostgres())

    def test_apply_rejects_old_preview(self):
        self.candidates([("CP1", "A", 1, "2026-01-01 00:00:00")])
        manifest_path = self.output / "manifest.json"
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        del manifest["preview_version"]
        manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "重新扫描"):
            backfill.apply(self.output, FakePostgres())

    def test_apply_rejects_changed_segments(self):
        self.candidates([("CP1", "A", 1, "2026-01-01 00:00:00")])
        with (self.output / "segments.csv").open("ab") as stream:
            stream.write(b"changed")
        with self.assertRaisesRegex(ValueError, "分段文件校验失败"):
            backfill.apply(self.output, FakePostgres())


if __name__ == "__main__":
    unittest.main()
