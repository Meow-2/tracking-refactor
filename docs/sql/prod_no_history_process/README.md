# 重复生产序号历史补全

本目录脚本只从 TDengine `digital_coil` 中每台机组指定的一张过程表提取已有的生产序号，将同一机组、入口卷号、序号的采样合并为一条候选记录。候选时间取该表中对应序号最早的采样时间。脚本同时保存该结果对应的连续采样时间段。脚本不查询 Cube POR 点位，也不推断过程表未出现的序号。

| 机组 | 序号来源表 |
| --- | --- |
| CBL1 | `cbl1_process_default` |
| CP1 | `cp1_process_nof` |
| CSL1 | `csl1_process_default` |
| DCL1 | `dcl1_process_sf3` |
| FCL1 | `fcl1_process_ctf` |
| ZRM1 | `zrm1_process_default` |

脚本复用现有 status 算法的卷号过滤口径，以及 PostgreSQL 机组编码大写约定。历史表结构见 [qm_dc_repeat_prod_no_log.sql](../qm_dc_repeat_prod_no_log.sql)。status 结果不写入 TDengine；过程表提供的是下游采样，因此 create_time 是“首次过程采样时间”，不一定等于实际分配序号的时刻。

## 准备

建议使用 Python 3.9 及以上版本，在隔离环境安装：

    python -m pip install -r docs/sql/prod_no_history_process/requirements.txt

在本目录 backfill.py 顶部填写 TD_DSN 和 PG_DSN 两个配置常量：

    TD_DSN = "ws://<user>:<password>@<host>:6041"
    PG_DSN = "postgresql://<user>:<password>@<host>:5432/<database>"

实际账号和密码只在本地填写，不要将填写后的脚本提交到 Git，也不要把连接字符串粘贴到日志或报告。TDengine 账号只需要 `digital_coil` 和 `information_schema` 的查询权限。扫描时不使用 PG 连接；写入时不使用 TDengine 连接。Windows 运行环境通过 tzdata 识别上海时区。

## 预览和复核

在仓库根目录运行，输出目录必须为空，建议放在仓库外：

    python docs/sql/prod_no_history_process/backfill.py --output-dir D:\prod-no-preview

只查 2026 年 9 月 3 日 00:00:00 至运行时刻（上海时间）：

    python docs/sql/prod_no_history_process/backfill.py --start "2026-09-03 00:00:00" --output-dir D:\prod-no-preview-0903

脚本启动时只校验上表 6 张 `digital_coil` 普通表，要求每张表具备 `ts`、`coil_no` 和恰好一个序号列：迁移前的 `in_mat_prod_no` 或迁移后的 `repeat_prod_no`。两列同时存在或序号列不是 INT 时停止，不自动纳入其他过程表。`--start` 按上海本地时间解释；不指定时从各表最早记录开始。扫描截止时间在启动时固定为上海时间，时间窗口为左闭右开，默认一天；可用 `--window-days 2` 调整。指定起点时直接按范围查询，不再先对整张过程表求最早时间。TDengine 先用 `SESSION(ts, 3600s)` 找出间隔超过一小时的采样段，再对每段用 `STATE_WINDOW(coil_no)` 返回卷号段；仅当一个卷号段内含不同生产序号时，再用 `STATE_WINDOW(序号列)` 细分。这样减少传输的采样行数，同时保留停采、卷号和序号切换。相邻同键段跨查询窗口会合并；间隔超过一小时或中间出现其他键时保留为独立段。TDengine 时间读写均使用 Unix 毫秒，输出时转为上海本地时间，避免客户端把查询结果显示为 UTC 时产生 8 小时偏差。脚本串行扫描，完整历史可能运行较久。

启动预检还会确认 `digital_coil` 为毫秒精度；否则停止扫描，避免时间单位错误。

扫描日志会显示当前机组与过程表、窗口序号及上海时间范围；每个窗口结束时输出停采段、卷号段、序号细分查询和采样数。单个窗口运行超过 30 秒时，每隔 30 秒输出当前阶段和已处理停采段数量。日志写到标准错误并立即刷新，不包含连接字符串或卷号。已启动的旧脚本不会自动使用新增日志；如需重跑，请停止旧进程并使用新的空输出目录。

输出文件：

| 文件 | 用途 |
| --- | --- |
| candidates.csv | 可写入的明确序号、最早过程时间及时间段数量 |
| segments.csv | 每个机组、卷号、序号对应的连续采样段；包含段号、起止 ts、采样数和来源表，异常卷也保留 |
| anomalies.csv | 缺号、序号时间倒置或重叠、无效序号 |
| manifest.json | 固定截止时间、扫描表、数量及候选与时间段文件的 SHA-256 |
| stage.sqlite | 本地聚合证据，供追溯和进一步核对 |

`sequence_gap`、`sequence_time_order`、`sequence_overlap` 和 `invalid_sequence` 会阻止该卷进入候选文件。复核时还应与旧 `qm_dc_product_no` 及现场生产记录比较最大次数。若过程表只有序号 1，但真实发生重复生产，本脚本不会自行生成序号 2，需依据其他可靠证据另行处理。

## 写入 PostgreSQL

确认目标连接指向预期数据库，且已按建表脚本创建 public.qm_dc_repeat_prod_no_log。预览文件复核完成后运行：

    python docs/sql/prod_no_history_process/backfill.py --output-dir D:\prod-no-preview --apply

--apply 只读取现有预览产物，先校验候选文件哈希，再逐卷核对目标表和可用的旧计数表。已有业务键保持原样；新行的 ID 使用确定性负数，与在线正数雪花 ID 分离。每卷单独事务，遇到缺号、已删除行、历史最大值冲突或 ID 碰撞时回滚该卷并写入 apply_results.csv。重复运行只会跳过已存在的行。退出码非零表示至少一卷被阻止，必须检查结果文件。执行期间建议停止新表相关在线写入，避免取号竞争；若未停写，唯一约束仍会阻止重复业务键，失败卷需复核后重跑。

本版增加了 `segments.csv`，并由 TDengine 聚合连续卷号段。旧版生成的预览产物不能直接用于 `--apply`；脚本会检查清单版本，旧产物须在新的空目录中重新扫描。

写入后可运行：

    SELECT unit_code, in_mat_no, COUNT(*) AS records,
           MIN(in_mat_repeat_prod_no) AS first_no,
           MAX(in_mat_repeat_prod_no) AS latest_no
    FROM public.qm_dc_repeat_prod_no_log
    GROUP BY unit_code, in_mat_no
    HAVING MIN(in_mat_repeat_prod_no) <> 1
        OR COUNT(*) <> MAX(in_mat_repeat_prod_no);

该查询结果应为空；再按 candidates.csv 逐卷核对最大序号及新行数量。当前已配置的 aygg_tracking DBX 连接没有目标表，脚本没有对其执行写入；正式运行必须提供实际目标库 DSN。

## 离线验证

    python -m unittest discover -s docs/sql/prod_no_history_process -p 'test_*.py' -v

测试使用本地模拟数据，不需要数据库账号。TDengine WebSocket Python 接口及版本要求见[官方连接文档](https://docs.tdengine.com/developer-guide/connectors-reference/)。
