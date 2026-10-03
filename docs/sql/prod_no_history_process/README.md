# 重复生产序号历史补全

本目录脚本从 TDengine digital_coil 的普通过程表提取明确的生产序号，将同一机组、入口卷号、序号的秒级采样合并为一条候选记录。候选时间取所有过程表中最早的采样时间。Cube 中 por 卷号的切换只生成疑似漏次提示，不会推断或插入过程表未出现的序号。

脚本复用现有 status 算法的卷号过滤口径，以及 PostgreSQL 机组编码大写约定。历史表结构见 [qm_dc_repeat_prod_no_log.sql](../qm_dc_repeat_prod_no_log.sql)。status 结果不写入 TDengine；过程表提供的是下游采样，因此 create_time 是“首次过程采样时间”，不一定等于实际分配序号的时刻。

## 准备

建议使用 Python 3.9 及以上版本，在隔离环境安装：

    python -m pip install -r docs/sql/prod_no_history_process/requirements.txt

在本目录 backfill.py 顶部填写 TD_DSN 和 PG_DSN 两个配置常量：

    TD_DSN = "ws://<user>:<password>@<host>:6041"
    PG_DSN = "postgresql://<user>:<password>@<host>:5432/<database>"

实际账号和密码只在本地填写，不要将填写后的脚本提交到 Git，也不要把连接字符串粘贴到日志或报告。TDengine 账号只需要 digital_coil、cube 和 information_schema 的查询权限。扫描时不使用 PG 连接；写入时不使用 TDengine 连接。Windows 运行环境通过 tzdata 识别上海时区。

## 预览和复核

在仓库根目录运行，输出目录必须为空，建议放在仓库外：

    python docs/sql/prod_no_history_process/backfill.py --output-dir D:\prod-no-preview

脚本启动时枚举 digital_coil 实际的 *_process_* 普通表，要求具备 ts、coil_no 和恰好一个序号列：迁移前的 in_mat_prod_no 或迁移后的 repeat_prod_no。两列同时存在或序号列不是 INT 时停止。机组 por 点位从 docs/config/<机组>/status.json 读取，Cube 子表映射写在脚本顶部；执行前请核对映射与现场 Cube 元数据。扫描截止时间在启动时固定为上海时间，时间窗口为左闭右开，默认一天；可用 --window-days 2 调整。TDengine 时间读写均使用 Unix 毫秒，写入目标表前转为上海本地时间，避免客户端把查询结果显示为 UTC 时产生 8 小时偏差。脚本串行扫描，完整历史可能运行较久。

启动预检还会确认 digital_coil 和 cube 均为毫秒精度；否则停止扫描，避免时间单位错误。

输出文件：

| 文件 | 用途 |
| --- | --- |
| candidates.csv | 可写入的明确序号及最早过程时间 |
| anomalies.csv | 缺号、序号时间倒置或重叠、无效序号和 por 疑似漏次 |
| manifest.json | 固定截止时间、扫描表、数量及候选文件 SHA-256 |
| stage.sqlite | 本地聚合证据，供追溯和进一步核对 |

sequence_gap、sequence_time_order、sequence_overlap 和 invalid_sequence 会阻止该卷进入候选文件；suspected_missing_por 仅提示复核，不改变过程表明确的序号。复核时还应与旧 qm_dc_product_no 及现场生产记录比较最大次数。若发现过程表只有序号 1、但真实发生重复生产，应先确认可靠证据，再另行处理；不要把疑似 por 切换直接当成序号 2。

## 写入 PostgreSQL

确认目标连接指向预期数据库，且已按建表脚本创建 public.qm_dc_repeat_prod_no_log。预览文件复核完成后运行：

    python docs/sql/prod_no_history_process/backfill.py --output-dir D:\prod-no-preview --apply

--apply 只读取现有预览产物，先校验候选文件哈希，再逐卷核对目标表和可用的旧计数表。已有业务键保持原样；新行的 ID 使用确定性负数，与在线正数雪花 ID 分离。每卷单独事务，遇到缺号、已删除行、历史最大值冲突或 ID 碰撞时回滚该卷并写入 apply_results.csv。重复运行只会跳过已存在的行。退出码非零表示至少一卷被阻止，必须检查结果文件。执行期间建议停止新表相关在线写入，避免取号竞争；若未停写，唯一约束仍会阻止重复业务键，失败卷需复核后重跑。

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
