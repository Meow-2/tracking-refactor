# 从 Cube POR 点位补全重复生产序号

本脚本以 `docs/config/<机组>/status.json` 的 POR 卷号配置为入口，在 TDengine `cube` 子表内用 `STATE_WINDOW` 把连续相同卷号的采样聚合为卷号段，并用 `SESSION` 检查超过一小时的采样空洞。每台 POR 的卷号段再过滤短暂抖动；同卷跨 POR 近同时出现或时间段重叠时合并成一次，并列入异常供人工复核。对同机组同卷的稳定上卷事件按首次观测时间从 1 编号，生成 `events.csv`。**不读取 process 跟踪结果作为序号依据。**

## 使用

1. 在 `backfill.py` 顶部填写 `TDENGINE_DSN`、`POSTGRES_DSN`。请在本机副本填写密码，不要提交凭据。TDengine 仅需 `cube` 和 `information_schema` 的只读权限。运行环境需要 Python 3.9+，安装 `taos-ws-py`、`psycopg[binary]`、`tzdata`。
2. 先确定源数据的最早可用时间，选取**完整历史的起点**；起点之前若已有相同卷的生产，序号会整体偏小。时间参数均为上海本地时间，范围左闭右开。

   ```powershell
   python docs/sql/prod_no_history_cube/backfill.py --start '2026-01-01 00:00:00' --end '2026-10-01 00:00:00' --output-dir D:\prod-no-cube-preview
   ```

   可用 `--units CP1 DCL1` 限定机组。默认扫描 CBL1、CP1、CSL1、DCL1、FCL1、ZRM1；默认每 1 天分段查询，但设备状态跨分段保留。脚本在连接、每个查询窗口开始和结束时向终端输出进度，结束时报告聚合后的卷号段数量。预览文件在全部机组扫描完成后才生成。`--min-samples 3 --min-seconds 10` 是稳定卷号门槛，`--merge-seconds 300` 是跨 POR 同卷合并门槛，应结合现场采样周期复核。扫描只查询 `cube`；不连接 PG。

3. 复核 `events.csv` 和 `anomalies.csv`。`status=review` 的卷整卷不写入；核对首尾采样、停采区间、双 POR 重叠、现场上卷日志及现有 PG 最大序号。POR 卷号不变的卸卷再上卷无法从卷号点位单独识别；连续历史缺失也无法靠算法补出。处理这两类情况需补充可靠现场证据。首次观测时间是上卷时间的**上界**，不是精确的机械上卷时刻。
4. 确认扫描起点覆盖全部需计数历史、复核候选后，暂停在线取号写入，再执行：

   ```powershell
   python docs/sql/prod_no_history_cube/backfill.py --output-dir D:\prod-no-cube-preview --apply --accept-coverage
   ```

   写入按卷事务执行；重复业务键跳过，已有记录时间偏差超过 1 小时、序号缺口或 ID 冲突会阻止该卷。结果写入 `apply_results.csv`。目标表唯一键为 `(unit_code, in_mat_no, in_mat_repeat_prod_no)`；历史行使用确定性负数 ID，与在线雪花 ID 分离。

## 校验

在目标 PostgreSQL 核对：

```sql
SELECT unit_code, in_mat_no, COUNT(*) AS records, MAX(in_mat_repeat_prod_no) AS latest_no
FROM public.qm_dc_repeat_prod_no_log
GROUP BY unit_code, in_mat_no
HAVING MIN(in_mat_repeat_prod_no) <> 1 OR COUNT(*) <> MAX(in_mat_repeat_prod_no);
```

结果应为空。再将 `events.csv` 的候选行数量和各卷最大序号与目标表比对。连接目标库前先确认它确实包含 `public.qm_dc_repeat_prod_no_log`；当前 DBX 已配置的 PG 连接未核到此表，因此本脚本未对线上 PG 执行写入。
