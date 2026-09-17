---
name: tracking-point-history-reader
description: 通过 Cube 定位 tracking 点位，并使用 DBX 查询 TDengine 历史数据；适用于按机组、跟踪类型、点位名称或编码查询历史值。
---

# Tracking 点位历史数据查询

## 目标

当用户要求查询 tracking 点位的历史数据时，完成以下闭环：

1. 从 Cube 配置树定位机组、跟踪类型和目标点位。
2. 从点位节点读取 `cubeKey` 和 `code`，推导 TDengine 数据对象名。
3. 使用 DBX MCP 只读查询指定时间范围的 `ts` 和点位字段。
4. 返回查询条件、点位元数据、数据量、值分布和必要的数据样例；数据量较大时使用分页或汇总，不能把“前 100 行”误报成全量。

本技能只处理 Cube/TDengine 点位历史数据查询，不用于读取 tracking 服务日志，也不修改仓库代码、Cube 配置或数据库数据。

## 1. 调用 Cube 并定位点位

tracking 当前使用 Cube API：

```http
POST ${cube-api.base-url}/openapi/meta/tree
Content-Type: application/json
```

请求体按项目实现发送，字段名使用 camelCase：

```json
{
  "level": null,
  "paraRange": 3,
  "path": "/aygg_tracking",
  "pathHeader": false,
  "onlyDir": false
}
```

`base-url` 从当前 profile 的 `cube-api.base-url` 读取，不要臆造地址。项目接口和 DTO 可参考：

- `src/main/java/com/wisdri/tracking/infrastructure/service/feign/client/CubeApiFeignClient.java`
- `src/main/java/com/wisdri/tracking/infrastructure/dto/feign/cube/CubeApiTreeRequest.java`
- `src/main/java/com/wisdri/tracking/infrastructure/dto/feign/cube/CubeApiTreeNode.java`

在响应树中按以下层级查找，机组和编码比较应不区分大小写：

```text
响应 data
└── 机组根节点，例如 cp1
    └── 跟踪类型，例如 shear
        └── tracking 或其他目录
            └── 点位节点
```

点位节点通常满足 `itemType = 2`，但查找时优先匹配用户给出的中文名称、`code` 或 `cubeKey`，不要只依赖 `itemType`。至少记录：

- `path`
- `name`
- `code`
- `cubeKey`
- `valueType`
- `valid`

若同名点位存在多个，必须结合机组、跟踪类型、树路径和 `cubeKey` 消歧；无法消歧时停止查询并说明候选项。

## 2. 推导 TDengine 查询对象

当前 tracking/Cube 约定如下：

```text
cubeKey = <对象名>_<code>
对象名 = cubeKey 去掉末尾的 "_" + code
TDengine 数据库 = cube
TDengine 字段 = code
时间字段 = ts
```

例如：

```text
cubeKey: cp1_plc1_mese_shear_is_upper_cut
code:    is_upper_cut
对象名:  cp1_plc1_mese_shear
数据库:  cube
字段:    is_upper_cut
```

这里的 `cp1_plc1_mese_shear` 是 `cube` 数据库中的数据对象名；不要把它误当成 DBX 连接的数据库参数。DBX 查询的数据库参数应为 `cube`。

如果 `cubeKey` 不以 `_<code>` 结尾，不要猜测截断规则；先检查 Cube 树和 TDengine schema，或向用户确认。

## 3. 使用 DBX MCP 查询

优先使用 DBX MCP 的只读工具：

1. `dbx_list_connections` 找到 TDengine 连接。
2. 必要时用 `dbx_describe_table` 或 `dbx_list_tables` 核对对象和字段。
3. 使用 `dbx_execute_query` 查询数据，数据库参数传 `cube`。

基础查询模板：

```sql
SELECT ts, <code>
FROM <object_name>
WHERE ts >= '<start>'
  AND ts < '<end>'
ORDER BY ts
LIMIT 100;
```

时间条件使用左闭右开区间，避免结束时刻重复计入。用户只说“今天上午”且未指定其他定义时，按 `Asia/Shanghai` 的 `00:00:00` 到 `12:00:00` 处理，并在结果中明确写出日期和时区。当前日期必须从运行环境获取，不要硬编码示例日期。

例如 CP1 上通道剪刃剪切触发信号：

```sql
SELECT ts, is_upper_cut
FROM cp1_plc1_mese_shear
WHERE ts >= '2026-09-17 00:00:00'
  AND ts < '2026-09-17 12:00:00'
ORDER BY ts
LIMIT 100;
```

## 4. 全量和大结果集处理

DBX 单次查询通常最多返回 100 行。看到 `(100 rows)` 时只能说明本页已满，不能说明查询结果只有 100 行。

查询前先获取全量统计：

```sql
SELECT COUNT(*) AS total
FROM <object_name>
WHERE ts >= '<start>' AND ts < '<end>';

SELECT <code>, COUNT(*) AS value_count
FROM <object_name>
WHERE ts >= '<start>' AND ts < '<end>'
GROUP BY <code>;
```

大结果集优先使用时间游标分页：下一页条件使用上一页最后一条记录的 `ts`，并继续使用 `ORDER BY ts LIMIT 100`。若 `ts` 可能重复，应同时使用稳定的唯一排序键；只有在确认 TDengine/DBX 支持时才使用 `LIMIT ... OFFSET ...`。

结果输出按数据量选择方式：

- 少量数据：直接列出全部 `ts` 和值。
- 大量数据：返回总行数、值分布、首末时间、异常/状态切换区间和样例；如用户明确要求全量，再分批导出或分段展示。
- Boolean 点位：可以补充 `true`/`false` 数量和连续区间，但不能用统计结果替代用户明确要求的原始明细。

## 5. 时区和异常检查

- 查询前明确用户时区；项目运行环境通常使用 `Asia/Shanghai`。
- TDengine/DBX 返回的时间显示可能是 UTC。用首条和末条记录核对查询边界，必要时同时给出本地时间和返回时间。
- 如果结果为 0 行，依次检查：数据库是否为 `cube`、对象名是否正确、字段是否存在、时间范围是否按正确时区转换。
- 如果 Cube 节点 `valid` 为 `false`，仍可报告节点元数据，但应提醒用户该点位在 Cube 中标记为无效。
- 只执行查询和 schema 读取，不执行建库、建表、写入、更新或删除。
