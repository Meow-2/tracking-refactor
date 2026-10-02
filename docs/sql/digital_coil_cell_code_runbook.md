# 安阳生产环境 `digital_coil.cell_code` 历史补全操作手册

## 1. 适用范围与执行前提

本手册用于新算法上线、目标表均已有 `cell_code` 普通列之后，补全安阳生产环境 TDengine `digital_coil` 中 93 张表的历史数据。准确表名见[93 张表清单](digital_coil_cell_code_tables.md)；历史计算语句见[完整补全 SQL](backfill_digital_coil_cell_code.sql)；小范围功能验证见[测试 SQL](test_digital_coil_cell_code_backfill.sql)。表清单来自生产环境的一次元数据快照，执行前须重新核对。

本次历史计算规则按需求定义为：`大写机组前缀 + 三位道次号`。机组前缀取表名第一个下划线之前的部分；`pass_no IS NULL` 按 1；非负且不足三位的道次补前导零。例如 `cp1_process_sf` 的空道次得到 `CP1001`，`zrm1_process_default` 的道次 6 得到 `ZRM1006`。现有 SQL 对负数及超过三位的数字直接转为字符串；执行前要确认这两类值的业务处理规则。

**业务规则核对是启动门槛。** 当前仓库的 process 实时算法从加工单元配置或点位生成 `cell_code`，与 `pass_no` 是两条独立计算路径；当前 batch 仓储没有写入 `cell_code`。新算法上线后，负责人须确认 7 个机组、93 张表（包括 `baf1_batch`）的新数据都带有非空 `cell_code`，且历史 SQL 的 `pass_no` 规则与最终业务口径一致。若新算法仍按加工单元配置生成不同值，先修订历史 SQL 的计算规则，再进行任何补全写入。

## 2. 角色与记录

- 操作人：使用已授权的生产库客户端执行 SQL，记录每批结果和耗时。
- 业务确认人：确认历史计算规则、异常道次值处理、抽样结果。
- 运维值守人：观察 TDengine CPU、内存、磁盘空间、磁盘 I/O、写入延迟和错误；准备环境已有的备份/恢复方案。

为每一张表保存一条执行记录，至少包括：表名、数据最早/最晚时间、新程序生效时间、补全截止时间 `T`、批次起止时间、批前待补行数、批后待补行数、执行耗时、执行结果、操作人。记录不包含生产账号、密码或令牌。正式写入前，按现有生产流程确认可用备份及恢复负责人；不要把“重跑 SQL”当成错误值的回滚方法。

## 3. 上线后检查

1. 核对连接确实指向“安阳生产环境 / TDengine / `digital_coil`”，记录 TDengine 版本。确认[表清单](digital_coil_cell_code_tables.md)中的 93 张表仍存在，且没有新增需要纳入的 `cp1_`、`cbl1_`、`fcl1_`、`dcl1_`、`zrm1_`、`baf1_`、`csl1_` 表。
2. 逐表核对 `ts TIMESTAMP`、`pass_no INT`、`cell_code` 普通字符串列；不能把 `cell_code` 定义为 TAG。若有表缺列或类型不符，先处理结构问题，不对该表执行回填。
3. 核对所有实时写入实例已切换到新算法；对每类表抽查切换后新产生的数据。特别检查 `baf1_batch`，以及没有 `pass_no` 值的 process 表。
4. **先上线程序，再生成和使用补全 SQL。** 仅凭程序发布时间不能判定切换完成；若有多实例或写入队列，等待全部实例生效和积压处理完毕，确认新数据稳定带有正确 `cell_code`，并记录程序生效时间。
5. 在准备补全 SQL 的那一刻，查询并记录数据库当前时间，作为固定的**补全截止时间 `T`**。可以用 `SELECT NOW()` 读取一次，再把得到的时间字面量写入 SQL；不要把 `NOW()` 直接留在回填条件中作为随执行推进的上界。`T` 必须晚于已确认的程序生效时间；后续全部 93 张表使用这一固定 `T`，即使回填持续数小时或数天也不向前移动。
6. 只读查询每张表最早的 `ts`，记为该表的起点 `E`。将 93 个表名、各自的 `E` 和固定 `T` 交给 AI，基于[完整补全 SQL](backfill_digital_coil_cell_code.sql)生成每张表带有 `ts >= E AND ts < T` 条件的 SQL，并按实际数据量进一步拆成连续时间窗口。执行前由操作人逐条核对表名、前缀、计算表达式、时间边界及 93 张表是否齐全；空表跳过。这样每张表的首轮补全有确定的结束时间，不会追着不断写入的新数据扫描。
7. 记录每张表在 `[E, T)` 范围内的待补行数；先按表统计，避免一次对全部 93 张大表同时做全量计数。确认业务低峰时段、监控入口和可接受的写入延迟。

单表结构与数量核对示例（替换表名和 `T`）：

```sql
SELECT NOW() AS cutoff_time;

DESCRIBE digital_coil.`cp1_process_sf`;

SELECT ts
FROM digital_coil.`cp1_process_sf`
ORDER BY ts ASC
LIMIT 1;

SELECT ts
FROM digital_coil.`cp1_process_sf`
ORDER BY ts DESC
LIMIT 1;

SELECT COUNT(*) AS pending_rows
FROM digital_coil.`cp1_process_sf`
WHERE ts < '2026-10-03 10:00:00.000'
  AND (cell_code IS NULL OR cell_code = '');

SELECT ts, pass_no, cell_code
FROM digital_coil.`cp1_process_sf`
WHERE ts >= '2026-10-03 10:00:00.000'
ORDER BY ts DESC
LIMIT 20;
```

示例 `T` 仅为占位时间，执行时必须替换为生成补全 SQL 时记录的固定截止时间；每张表的 `E` 以该表最早的 `ts` 为准。

## 4. 小范围验证与速度测量

1. 先执行[测试 SQL](test_digital_coil_cell_code_backfill.sql)中的只读计数；确认两张表已加列，样本时间段仍符合预期，再逐条执行其中的 `INSERT`。该脚本当初查到每个时间段各 10 行，执行当天的待补行数以实际查询为准。
2. 分别核对空 `pass_no`、非空 `pass_no` 的 `cell_code`；同时对比样本行的 `pass_no`、`coil_no` 和其他业务列，确认同时间戳写回没有改变非目标字段。
3. 10 行仅用于功能验证。选一张有代表性的大表，挑选约 1 万至 10 万待补行的历史时间段。先 `COUNT(*)`，只计时该时间段的单条 `INSERT INTO ... SELECT`，再统计剩余待补行数。记录实际补全量、耗时和实时写入延迟。不同表的密度、磁盘数据位置和负载可能不同，应再测一张高数据量表。
4. 粗估耗时：`预计历史待补总行数 ÷ 试跑实际补全行数 × 试跑耗时`。这是排期参考，不是完成时间保证。若试跑使实时写入明显变慢，缩小时间窗口并调整执行时段。

## 5. 正式分批补全

**不要直接整文件执行[完整补全 SQL](backfill_digital_coil_cell_code.sql)。** 它包含 93 条无时间边界的整表语句，可作为表名和表达式的核对来源。必须先按第 3 节让 AI 为每张表补上从该表最早时间 `E` 到固定截止时间 `T` 的范围，再核对并拆批。正式执行时，一次只处理一张表的一个时间段，条件为 `ts >= 本批起点 AND ts < 本批终点`；所有批次均位于 `[E, T)`。推荐先串行执行，依据试跑结果再决定是否增加并发。

单批示例（时间和表名仅示范；执行前替换，保持起止时间与计数一致）：

```sql
SELECT COUNT(*) AS pending_before
FROM digital_coil.`cp1_process_sf`
WHERE ts >= '2026-09-01 00:00:00.000'
  AND ts < '2026-09-01 01:00:00.000'
  AND (cell_code IS NULL OR cell_code = '');

INSERT INTO digital_coil.`cp1_process_sf` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'CP1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`cp1_process_sf`
WHERE ts >= '2026-09-01 00:00:00.000'
  AND ts < '2026-09-01 01:00:00.000'
  AND (cell_code IS NULL OR cell_code = '');

SELECT COUNT(*) AS pending_after
FROM digital_coil.`cp1_process_sf`
WHERE ts >= '2026-09-01 00:00:00.000'
  AND ts < '2026-09-01 01:00:00.000'
  AND (cell_code IS NULL OR cell_code = '');
```

按照表内时间从早到晚，连续使用左闭右开窗口 `[起点, 终点)`，直到覆盖该表的整个 `[E, T)`；不要漏掉边界，也不要按行号分页。窗口可从小时级开始，按实测行数、耗时和线上负载增减。空窗口记录为已检查，不执行写入。每批只运行一条写入语句，完成后核对 `pending_after`；结果异常时暂停，不继续下一批。避免一次性启动 93 条整表写入。

每批只为 `cell_code` 为空的行写值；已补的行再次扫描应跳过，因此失败后可以先复查该窗口的待补数量，再决定重跑。不要假定一条失败的写入完全没有生效。TDengine 相同时间戳再次写入属于更新，实时写入与历史回填重叠时还可能出现写入先后竞争，因此要保持计算口径一致并观察其他业务列。[TDengine 写入说明](https://docs.tdengine.com/quick-start/write-data/)及[官方历史回填指南](https://docs.tdengine.com/data-ingest-and-delivery/no-code-ingestion/pi/backfill-guide/)可作为这些行为和分批建议的参考。

## 6. 持续写入、迟到数据与收尾

回填期间保持实时写入，但要求新算法持续写入正确 `cell_code`。固定补全截止时间 `T` 后，首轮批次只处理 `[E, T)`；已有新值不应被空值筛选条件选中。仍须考虑迟到写入：它可能在某批扫描结束后才以旧 `ts` 到达，也可能由尚未更新的写入方写出空值。

93 张表的首轮窗口全部完成后，逐表执行以下检查：

```sql
-- 历史范围：查找漏补和迟到写入的数据。
SELECT COUNT(*) AS historical_pending
FROM digital_coil.`cp1_process_sf`
WHERE ts < '2026-10-03 10:00:00.000'
  AND (cell_code IS NULL OR cell_code = '');

-- 补全截止时间之后：确认新算法没有持续产生空值。
SELECT COUNT(*) AS new_pending
FROM digital_coil.`cp1_process_sf`
WHERE ts >= '2026-10-03 10:00:00.000'
  AND (cell_code IS NULL OR cell_code = '');
```

若 `historical_pending > 0`，定位对应时间窗口，只对该窗口再执行补全并复查。若 `new_pending > 0`，先排查写入方、队列、配置与业务口径；修复后再补漏，不要靠反复扫全表维持新数据完整性。若存在允许长时间迟到的历史数据，在其到达期限后再做一次末次检查。对于业务明确允许 `cell_code` 为空的记录，应先确认例外规则并单独记录，不能把它们算作补全成功。

完成标准：93 张表逐一有执行记录；每张表的历史待补数为 0；新写入范围没有持续出现空值；抽样计算结果符合已确认的业务规则；实时写入延迟和 TDengine 资源指标恢复到正常水平。将异常记录、处理结论和最终复查时间写入执行记录。

## 7. 异常处理

| 现象 | 处理 |
| --- | --- |
| 表不存在、缺少 `cell_code` 或类型不符 | 停止该表操作，重新核对 93 表清单和上线后的表结构。 |
| 结果与新算法不一致 | 停止所有历史写入，确认业务规则与样本；修订 SQL 后重新做小范围验证。已写错的值按已确认的恢复方案处理。 |
| 单批执行报错或连接中断 | 记录表名与窗口，查询该窗口剩余空值及业务列；确认状态后仅重试该窗口。 |
| 单批耗时过长或影响实时写入 | 暂停后续批次，缩小窗口、降低并发，改在低峰继续；由运维评估资源状况。 |
| 切换后新数据仍为空 | 先修复实时写入链路，再执行尾部补漏和最终核对。 |

本手册及关联 SQL 文件均为本地交付物；编写本手册时没有对生产 TDengine 执行写入或结构修改。
