# `repeat_prod_no` 历史填充和旧列删除

脚本从 `information_schema.ins_columns` 发现 `digital_coil` 中同时具有普通列 `in_mat_prod_no`、`repeat_prod_no` 且两列类型一致的普通表。不使用固定表清单。默认只预览，`--apply` 才修改数据。超表、子表或两列类型不一致时会停止，请先核对结构。

安装 `taos-ws-py`，并通过环境变量 `TD_DSN` 配置 TDengine WebSocket 连接串；未配置时脚本会停止。连接串含敏感凭据，不得提交到 Git，运行日志不会输出连接信息：

```powershell
python -m pip install taos-ws-py
```

先预览，再执行填充：

```powershell
python docs/digital-coil-repeat-prod-no-backfill/backfill.py
python docs/digital-coil-repeat-prod-no-backfill/backfill.py --apply
```

填充仅针对 `in_mat_prod_no IS NOT NULL AND repeat_prod_no IS NULL` 的历史行。脚本按每表现有首尾时间固定扫描范围，以左闭右开窗口统计待填行数；超过 `--max-rows`（默认 10000）时按时间二分。写入使用 TDengine 3.x 的 `INSERT INTO ... (ts, repeat_prod_no) SELECT ...` 同时间戳部分列更新，随后复查窗口。执行前请在相同版本测试环境验证部分列更新不会改变其他列。运行中有新写入、迟到写入时，可能需要再次运行填充。

旧列删除使用**独立模式**，不执行填充。每张表先全量检查是否仍有“源列有值、目标列为空”的记录；有则保留该表旧列并继续检查其他表。没有则可删列，执行前仍须加 `--apply`：

```powershell
python docs/digital-coil-repeat-prod-no-backfill/backfill.py --drop-in-mat-prod-no
python docs/digital-coil-repeat-prod-no-backfill/backfill.py --drop-in-mat-prod-no --apply
```

删列是不可逆的 DDL。执行前应停写或确保写入方已不再使用 `in_mat_prod_no`，并备份历史数据；校验和删列之间若有并发写入，可能丢失新写的旧列值。删列条件只保证原值不因**目标为空**而丢失，不会覆盖或比较两个均有值但不相等的行。已成功删列的表下次运行会自动排除；某表失败后，可从进度日志确认位置并重新发现剩余双列表。

可用 `--table <表名>`（可重复）先在单表验证，或使用 `--resume-table <表名>` 从发现清单的指定表继续。`--window-hours`、`--max-rows` 和 `--progress-seconds` 控制填充窗口、单次写入上限和心跳间隔。日志包含上海时间、表序号、窗口序号、时间范围进度、待填行数、删列校验结果和总耗时。若数据库请求阻塞，心跳会持续报告当前阶段。脚本不会输出连接串或原始数据值。

离线测试：

```powershell
python -m unittest discover -s docs/digital-coil-repeat-prod-no-backfill -p 'test_*.py' -v
```
