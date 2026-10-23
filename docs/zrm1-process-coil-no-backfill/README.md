# ZRM1 过程历史钢卷号修正

固定处理 `digital_coil.zrm1_process_default.coil_no`：倒数第三个字符是 `3` 时改为 `2`，例如 `ABC301 → ABC201`、`33333 → 33233`。不去空格，不修改其他位置的 `3`；NULL、空串和不足三位的值跳过。只处理此表，不联动 PostgreSQL 或其他过程表。

沿用 `../digital-coil-repeat-prod-no-backfill` 的 WebSocket 连接、默认预览、`--apply` 写入、固定历史范围、左闭右开窗口、候选行数超限二分、写后复查和上海时间心跳日志。此次是单表修正，因此不提供表发现、续表和删列模式；通过 `--start` 限定或继续扫描。脚本独立运行，不依赖兄弟目录导入，也未改动原脚本。

## 运行

安装 `taos-ws-py`，连接串直接配置在脚本顶部的 `TD_DSN`，沿用 `digital-coil-cell-code-backfill` 的本地配置，无需设置环境变量。不得提交真实连接串，日志不会输出连接信息或业务行值。

```powershell
python -m pip install taos-ws-py
# 确认脚本顶部 TD_DSN 配置后，先预览完整历史。
python docs/zrm1-process-coil-no-backfill/backfill.py

# 实际修改。
python docs/zrm1-process-coil-no-backfill/backfill.py --apply

# 限定上海时间范围，也可将失败窗口的起点用作 --start 续跑。
python docs/zrm1-process-coil-no-backfill/backfill.py --start "2026-10-01 00:00:00" --end "2026-10-21 00:00:00" --apply
```

`--start` 包含、`--end` 不包含，均按上海时间解释，最多毫秒精度。不传时分别取启动扫描时已有的最早时间、最晚时间加一毫秒；两者均传时不再查询全表首尾。空表或有效范围无数据时不写入。

`--window-hours` 默认 24；`--max-rows` 默认 10000，超过阈值按时间二分到毫秒；`--progress-seconds` 默认 30，连接及请求阻塞期间仍输出阶段与耗时。累计候选数是写前计数，不是驱动报告的实际影响行数；并发迟到数据可能使单次实际写入超过阈值。

## 更新语义与恢复边界

写入沿用参考脚本的 `INSERT INTO ... (ts, coil_no) SELECT ts, ...`。依赖 TDengine 同时间戳部分列更新能力；仅提交两列并不能在所有版本或数据库设置下保证其他列保留。执行前应备份，并在相同版本和配置的测试库验证：目标卷号被修改、时间戳与行数保持不变、所有其他列（包括 NULL）保持原值。要求毫秒精度、普通表、首列 `ts` 为 TIMESTAMP、`coil_no` 为普通字符串列，不支持第二主键。

字符串表达式依据 [TDengine 官方函数说明](https://github.com/taosdata/TDengine/blob/main/docs/zh/05-tdengine-sql/04-data-query/03-function.md)，部分列写入形式见 [官方写入说明](https://docs.tdengine.com/tdengine-sql/data-write/insert/)。脚本不自动更改数据库配置。

每个写入窗口复查候选数必须为零，否则停止；数据库错误不自动重试，仅报告异常类型、可提取的十六进制错误码及白名单摘要，不输出原始异常或 DSN。按最后的阶段及时间范围定位问题。已改成 `2` 的行会自动跳过，失败后可以重跑原范围；没有持久化检查点，也没有自动回滚功能。原有的 `2` 和修正后的 `2` 无法区分，不能用逆向替换恢复，应使用备份。

兼容性：倒数定位使用固定负下标，前缀动态长度显式 `CAST(... AS INT)`。线上 TDengine 已用纯常量 SELECT 复现原算术参数的 `0x2802 Invalid parameter data type : substr`，修正后的表达式通过纯常量查询验证；未执行线上写入。

尽量在目标范围停止写入后执行；计数、写入、复查不是事务快照，新写、迟到或并发修改可能引起复查失败或漏检，需要重新预览范围。复查只检查仍满足条件的行，不等于其他列完整性验证。

## 离线验证

```powershell
python -m unittest discover -s docs/zrm1-process-coil-no-backfill -p 'test_*.py' -v
```

测试覆盖修正位置、中文字符、短串及 NULL、预览不写、二分边界、失败复查、结构拒绝、时间精度及指定范围续跑。字符串表达式在 SQLite 注册兼容函数后离线验证，不代替真实 TDengine 方言和部分列更新验证。本次交付未连接数据库或执行线上修改。
