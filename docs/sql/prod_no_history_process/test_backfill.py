"""补全脚本的离线回归测试；不连接生产 TDengine 或 PostgreSQL。"""

import copy
import csv
import importlib.util
import json
import sqlite3
import tempfile
import unittest
from contextlib import contextmanager
from datetime import datetime
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
        self.rows = list(self.connection.rows_for(sql))

    def fetchmany(self, size):
        result, self.rows = self.rows[:size], self.rows[size:]
        return result

    def close(self):
        pass


class FakeTd:
    def cursor(self):
        return FakeTdCursor(self)

    def rows_for(self, sql):
        if "information_schema.ins_databases" in sql:
            return [("digital_coil", "ms"), ("cube", "ms")]
        if "information_schema.ins_columns" in sql:
            return [
                ("cp1_process_sf", "NORMAL_TABLE", "ts", "TIMESTAMP"),
                ("cp1_process_sf", "NORMAL_TABLE", "coil_no", "VARCHAR(255)"),
                ("cp1_process_sf", "NORMAL_TABLE", "in_mat_prod_no", "INT"),
                ("cp1_process_nof", "NORMAL_TABLE", "ts", "TIMESTAMP"),
                ("cp1_process_nof", "NORMAL_TABLE", "coil_no", "VARCHAR(255)"),
                ("cp1_process_nof", "NORMAL_TABLE", "in_mat_prod_no", "INT"),
            ]
        if sql.startswith("SELECT CAST(FIRST(ts) AS BIGINT)"):
            return [(backfill.epoch_ms(datetime(2026, 1, 1)),
                     backfill.epoch_ms(datetime(2026, 1, 2)))]
        if "cp1_process_sf" in sql:
            return [("A", 1, backfill.epoch_ms(datetime(2026, 1, 1, 2)),
                     backfill.epoch_ms(datetime(2026, 1, 1, 3)))]
        if "cp1_process_nof" in sql:
            return [("A", 1, backfill.epoch_ms(datetime(2026, 1, 1, 1)),
                     backfill.epoch_ms(datetime(2026, 1, 1, 4)))]
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

    def test_discovery_rejects_ambiguous_sequence_columns(self):
        columns = {
            "cp1_process_sf": {
                "ts": ("NORMAL_TABLE", "TIMESTAMP"),
                "coil_no": ("NORMAL_TABLE", "VARCHAR(255)"),
                "in_mat_prod_no": ("NORMAL_TABLE", "INT"),
                "repeat_prod_no": ("NORMAL_TABLE", "INT"),
            }
        }
        with patch.object(backfill, "table_columns", return_value=columns):
            with self.assertRaisesRegex(ValueError, "恰有一个"):
                backfill.discover_process_tables(FakeTd())

    def test_precision_must_be_milliseconds(self):
        class WrongPrecision(FakeTd):
            def rows_for(self, sql):
                if "information_schema.ins_databases" in sql:
                    return [("digital_coil", "us"), ("cube", "ms")]
                return super().rows_for(sql)

        with self.assertRaisesRegex(ValueError, "必须均为 ms"):
            backfill.validate_precision(WrongPrecision())

    def test_finalize_deduplicates_and_uses_earliest_cross_table_time(self):
        stage = self.stage()
        stage.execute("INSERT INTO evidence VALUES ('CP1','A',1,'2026-01-02 12:00:00','2026-01-02 12:05:00')")
        stage.execute(
            """INSERT INTO evidence VALUES ('CP1','A',1,'2026-01-01 12:00:00','2026-01-01 12:05:00')
            ON CONFLICT(unit, coil, seq) DO UPDATE SET
            first_ts=min(first_ts,excluded.first_ts), last_ts=max(last_ts,excluded.last_ts)"""
        )
        stage.commit()
        manifest = backfill.finalize(stage, self.output, datetime(2026, 1, 3), [("cp1_process_sf", "CP1", "in_mat_prod_no")])
        stage.close()
        self.assertEqual(manifest["candidate_count"], 1)
        with (self.output / "candidates.csv").open(encoding="utf-8-sig", newline="") as stream:
            rows = list(csv.DictReader(stream))
        self.assertEqual(rows[0]["create_time"], "2026-01-01 12:00:00")

    def test_scan_process_discovers_tables_and_aggregates_cross_table_rows(self):
        source = FakeTd()
        backfill.validate_precision(source)
        tables = backfill.discover_process_tables(source)
        self.assertEqual(len(tables), 2)
        stage = self.stage()
        try:
            backfill.scan_process(source, stage, tables, datetime(2026, 1, 2), 1)
            row = stage.execute("SELECT first_ts, last_ts FROM evidence").fetchone()
        finally:
            stage.close()
        self.assertEqual(row, ("2026-01-01 01:00:00.000000",
                               "2026-01-01 04:00:00.000000"))

    def test_finalize_blocks_gap_and_time_overlap_but_por_only_warns(self):
        stage = self.stage()
        stage.executemany("INSERT INTO evidence VALUES (?, ?, ?, ?, ?)", [
            ("CP1", "GAP", 1, "2026-01-01 00:00:00", "2026-01-01 00:10:00"),
            ("CP1", "GAP", 3, "2026-01-02 00:00:00", "2026-01-02 00:10:00"),
            ("CP1", "OVER", 1, "2026-01-01 00:00:00", "2026-01-02 00:00:00"),
            ("CP1", "OVER", 2, "2026-01-01 23:00:00", "2026-01-02 01:00:00"),
            ("CP1", "POR", 1, "2026-01-01 00:00:00", "2026-01-01 01:00:00"),
        ])
        stage.executemany("INSERT INTO por_event VALUES (?, ?, ?, ?)", [
            ("CP1", "POR", "por1_coil_no", "2026-01-01 00:00:00"),
            ("CP1", "POR", "por1_coil_no", "2026-01-02 00:00:00"),
        ])
        stage.commit()
        manifest = backfill.finalize(stage, self.output, datetime(2026, 1, 3), [])
        stage.close()
        self.assertEqual(manifest["candidate_count"], 1)
        self.assertEqual(manifest["blocked_coils"], 2)
        anomalies = (self.output / "anomalies.csv").read_text(encoding="utf-8-sig")
        self.assertIn("sequence_gap", anomalies)
        self.assertIn("sequence_overlap", anomalies)
        self.assertIn("suspected_missing_por", anomalies)

    def candidates(self, values):
        backfill.write_csv(
            self.output / "candidates.csv",
            ["unit_code", "in_mat_no", "in_mat_repeat_prod_no", "create_time"],
            values,
        )
        content = (self.output / "candidates.csv").read_bytes()
        import hashlib
        (self.output / "manifest.json").write_text(json.dumps({
            "cutoff_shanghai": "2026-01-03 00:00:00",
            "candidate_count": len(values),
            "candidate_sha256": hashlib.sha256(content).hexdigest(),
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


if __name__ == "__main__":
    unittest.main()
