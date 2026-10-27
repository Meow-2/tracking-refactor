# repeat_prod_no 空值补一

`backfill.py` 将 `digital_coil` 普通表中 `repeat_prod_no IS NULL` 的行补为整数 `1`。已有非空值（包括 `0`）保持不变，不依赖 `in_mat_prod_no`、`coil_no` 或 `pass_no`，不新增列或删除列。

沿用 `digital-coil-repeat-prod-no-backfill` 的元数据表发现，以及 `digital-coil-cell-code-backfill` 的脚本顶部连接配置、固定截止时间、暂停和进度心跳流程。脚本独立运行，不导入兄弟目录，也不修改原脚本。

安装 `taos-ws-py`，需使用 Python 3.10 或更新版本：

```powershell
python -m pip install taos-ws-py
```

在 `backfill.py` 顶部将 `TD_DSN` 占位符替换为实际 WebSocket 连接串；未填写时脚本会停止。连接串含敏感凭据，不得提交真实值或写入日志；密码中的 URL 特殊字符应先编码。

```python
TD_DSN = "ws://<user>:<password>@<host>:6041"
```

先指定单表和小范围预览；添加 `--apply` 才实际写入：

```powershell
python docs/digital-coil-repeat-prod-no-null-backfill/backfill.py --table cp1_process_sf --start '2026-09-01 00:00:00' --end '2026-09-01 01:00:00'
python docs/digital-coil-repeat-prod-no-null-backfill/backfill.py --table cp1_process_sf --start '2026-09-01 00:00:00' --end '2026-09-01 01:00:00' --apply
```

不指定 `--table` 时，从 `information_schema.ins_columns` 自动发现所有含 `repeat_prod_no` 的普通表，按名称排序处理，包含符合条件的 BAF 表；排除超表和子表。目标列必须为普通 `INT` 列，`ts` 必须为普通 `TIMESTAMP` 列，数据库必须为毫秒精度，否则停止。可重复指定 `--table` 处理多张表。

`--end` 必填，使用固定的上海本地时间；`--start` 可省略，默认逐表从最早时间戳开始。所有范围为 `[start, end)`。默认每窗口 2 小时，按上海整点划分；首个窗口从指定起点或最早记录开始，最后一个窗口截至 `--end`。仍可用 `--window-days` 显式按自然日划分，两个窗口参数不能同时使用。实际写入时，每窗口直接执行一条 `INSERT ... SELECT`，不预读时间戳、不预先计数、不二分、不逐批复查；数据在 TDengine 内部筛选并回写。

所有表填充结束后直接汇总窗口数和耗时，不再统一统计剩余空值。所有 SQL 成功执行后返回 `0`；写入异常立即停止。成功退出仅表示 SQL 执行完成，不表示已经验证不存在空值。

预览模式每窗口执行一次 `COUNT(*)`，只显示预计填充行数，不写入。写入模式仅报告已执行的窗口数和耗时，不将未知的实际影响行数报告为零。

`--pause` 控制每窗口写后暂停秒数，默认 **0**；`--progress-seconds` 控制心跳秒数（默认 30）。日志显示表、窗口、时间范围进度和耗时。`--max-rows` 仅兼容旧命令，已不生效，指定后会输出提示。单次写入量由 `--window-hours` 控制，不再保证行数上限；默认 2 小时，可设置其他正整数小时数。

例如按小时直接填充：

```powershell
python docs/digital-coil-repeat-prod-no-null-backfill/backfill.py --end '2026-10-26 00:00:00' --window-hours 2 --apply
```

新旧版本均可用原时间范围重跑，已补全的行自动跳过。性能优化通过离线测试验证查询次数和范围，尚未在实际数据库上测量耗时。

全表预览示例（按实际历史范围调整截止时间）：

```powershell
python docs/digital-coil-repeat-prod-no-null-backfill/backfill.py --end '2026-10-01 00:00:00'
```

中断后可用 `--resume-table` 从指定表继续，`--resume-start` 仅作用于该表，后续表仍从各自最早时间戳开始。保留原运行的 `--end`；若窗口开始后没有完成日志，应从该窗口起点重查。已补全的行会跳过。续跑参数不能与 `--table`、`--start` 混用：

```powershell
python docs/digital-coil-repeat-prod-no-null-backfill/backfill.py --resume-table dcl1_process_atc --resume-start '2026-09-01 00:00:00' --end '2026-10-01 00:00:00' --window-hours 2 --apply
```

写入采用与参考脚本相同的 TDengine 3.x 同时间戳部分列写入：

```sql
INSERT INTO digital_coil.`cp1_process_sf` (ts, repeat_prod_no)
SELECT ts, 1 FROM digital_coil.`cp1_process_sf`
WHERE ts >= <start_ms> AND ts < <end_ms> AND repeat_prod_no IS NULL;
```

执行前需在目标版本和数据库更新配置下验证其他列保持不变。应选择已稳定的历史时间段；各窗口写入不在同一事务中，并发更新同一时间戳可能产生竞争，迟到记录也可能需要再次扫描。本次仅交付脚本及离线测试，未连接或修改线上数据库。

离线测试：

```powershell
python -m unittest discover -s docs/digital-coil-repeat-prod-no-null-backfill -p 'test_*.py' -v
```
