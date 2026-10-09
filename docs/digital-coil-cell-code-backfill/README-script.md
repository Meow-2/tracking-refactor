# `cell_code` 分段重算脚本

`backfill.py` 从同目录清单中，仅选择表名以 `cp1_`、`cbl1_`、`dcl1_`、`fcl1_`、`zrm1_` 或 `csl1_` 开头的 92 张 `digital_coil` 普通表；`baf1_batch` 不在执行范围内。机组代码取表名第一个下划线前的部分并转大写，`cell_code` 为该代码加至少三位的道次。`pass_no IS NULL` 对应 `001`；`0` 对应 `000`；`6` 对应 `006`；`1234` 保留 `1234`。已有值与计算结果不一致时也会更新。负数沿用原 SQL 的字符串表示，例如 `-1` 对应 `CP1-1`，正式执行前需由业务方确认该规则。

脚本使用 TDengine 的 `INSERT INTO ... (ts, cell_code) SELECT ...` 对相同时间戳写入，依赖 TDengine 3.x 的部分列更新语义。先选少量历史数据验证 `pass_no`、`coil_no` 等其他列不变，再扩大范围。实时写入若在回填后再次覆盖同一时间戳，结果可能改变；应固定历史截止时间并避开仍会迟到写入的时间段。

## 准备

安装 `taos-ws-py`，然后在 `backfill.py` 顶部填写 `TD_DSN`：

```powershell
python -m pip install taos-ws-py
```

```python
TD_DSN = "ws://<user>:<password>@<host>:6041"
```

把占位符替换为实际连接信息；密码含有 `@`、`:` 等 URL 特殊字符时应先编码。连接串含敏感凭据，不要提交填写后的脚本，也不要将其粘贴到日志或报告中。脚本不会打印该连接串。

执行前确认目标库为 `digital_coil`、毫秒精度。脚本逐表检查 `ts TIMESTAMP`、`coil_no` 字符串列、`pass_no INT` 和 `cell_code` 普通字符串列。缺少 `coil_no` 或 `pass_no` 的表会记录原因并跳过，不执行任何回填；其他必要列不符合要求时停止。脚本不会添加列或改表结构。`--end` 必须是一次确定后保持不变的上海本地时间；所有窗口采用 `[start, end)`，不会在运行中向前推进截止点。

## 先预览，再写入

先选一张表和很短的历史时间段预览，确认数量与业务规则：

```powershell
python docs/digital-coil-cell-code-backfill/backfill.py --table cp1_process_sf --start '2026-09-01 00:00:00' --end '2026-09-01 01:00:00' --window-hours 1
```

复核样本及其他列后，添加 `--apply` 执行同一范围：

```powershell
python docs/digital-coil-cell-code-backfill/backfill.py --table cp1_process_sf --start '2026-09-01 00:00:00' --end '2026-09-01 01:00:00' --window-hours 1 --max-rows 10000 --pause 1 --apply
```

不指定 `--table` 则依次检查并处理上述 92 张表；不指定 `--start` 则逐表读取最早时间戳。脚本串行运行，默认初始窗口 24 小时、单条写入至多 10000 行、写入窗口之间暂停 1 秒。每个窗口先计数，空窗口跳过；超过 `--max-rows` 时按时间二分，直到满足上限。预览只执行计数，不写入。窗口异常时脚本立即停止，日志显示最后处理的表和时间范围；复查该范围后可用相同 `--start`、`--end` 重跑，已正确的行会自动跳过。大范围运行前建议按实际负载调整窗口、行数上限与暂停时间。

## 进度日志

每条日志带上海本地时间。启动时显示模式、固定截止时间、计划表数及批次上限；每张表显示 `当前表序号/总表数`、扫描起止时间与初始窗口数。每个窗口显示 `窗口序号/窗口总数`、左闭右开时间范围、待更新行数、计数耗时；超过行数上限时会记录二分位置。实际写入时还会显示写入和复查耗时、剩余待更新行数。窗口结束时显示本表已覆盖的**时间范围比例**及累计行数，最后汇总处理表数、跳过表数、行数和总耗时。时间范围比例并非数据行数比例。

若计数、写入或复查查询长时间未返回，默认每 30 秒输出一次“仍在运行”，标明当前表、窗口、阶段及已等待时间。可通过 `--progress-seconds 10` 调整间隔。脚本不会在心跳日志中输出连接信息、卷号或原始行数据。

脚本不会对整个历史范围一次性计数或写入。最终核对时，按窗口抽样比较 `cell_code` 与规则表达式，并检查其他业务列及 TDengine 写入延迟。

离线测试：

```powershell
python -m unittest discover -s docs/digital-coil-cell-code-backfill -p 'test_*.py' -v
```
