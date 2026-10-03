"""POR 上卷事件推断的离线回归测试，不连接远端数据库。"""

import importlib.util
import unittest
from datetime import datetime, timedelta
from pathlib import Path
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("cube_backfill", Path(__file__).with_name("backfill.py"))
backfill = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(backfill)


class PorEventTest(unittest.TestCase):
    def setUp(self):
        self.start = datetime(2026, 1, 1)

    def run_at(self, coil, seconds, samples=3):
        first = self.start + timedelta(seconds=seconds)
        return [coil, first, first + timedelta(seconds=10), samples]

    def test_stable_transition_counts_repeated_coil(self):
        runs, unstable = backfill.stable_runs([
            self.run_at("A", 0), self.run_at("B", 20), self.run_at("A", 40)
        ], 3, 10)
        events, overlaps = backfill.infer_events({"por1": runs}, 300)
        self.assertEqual([("A", 1), ("B", 1), ("A", 2)], [(e[0], e[1]) for e in events])
        self.assertEqual([], unstable)
        self.assertEqual([], overlaps)

    def test_brief_spike_does_not_increment(self):
        runs, unstable = backfill.stable_runs([
            self.run_at("A", 0), self.run_at("B", 20, 1), self.run_at("A", 40)
        ], 3, 10)
        self.assertEqual(1, len(runs))
        self.assertEqual("A", runs[0][0])
        self.assertEqual(["B"], [item[0] for item in unstable])

    def test_cross_por_overlap_is_one_reviewable_event(self):
        events, overlaps = backfill.infer_events({
            "por1": [self.run_at("A", 0)], "por2": [self.run_at("A", 600)]
        }, 300)
        # 两设备上卷起点相距超过合并门槛，但持续区间若重叠仍需合并。
        self.assertEqual(2, len(events))
        self.assertEqual([], overlaps)
        events, overlaps = backfill.infer_events({
            "por1": [["A", self.start, self.start + timedelta(seconds=900), 20]],
            "por2": [self.run_at("A", 600)]
        }, 300)
        self.assertEqual(1, len(events))
        self.assertEqual(1, len(overlaps))

    def test_short_spike_blocks_neighboring_coil(self):
        samples = [(backfill.epoch_ms(self.start + timedelta(seconds=second)), coil)
                   for second, coil in [(0, "A"), (5, "A"), (10, "A"),
                                        (20, "B"), (30, "A"), (35, "A"), (40, "A")]]
        with patch.object(backfill, "point_fields", return_value={"por1": "por_coil_no"}), \
             patch.object(backfill, "validate_source"), \
             patch.object(backfill, "query", return_value=iter(samples)):
            events, anomalies = backfill.scan(object(), "ZRM1", self.start,
                self.start + timedelta(hours=1), 1, 3, 10, 300)
        self.assertEqual(1, len(events))
        self.assertIn(("A", "unstable_neighbor"), {(row[0], row[1]) for row in anomalies})
        self.assertIn(("B", "unstable_neighbor"), {(row[0], row[1]) for row in anomalies})


if __name__ == "__main__":
    unittest.main()
