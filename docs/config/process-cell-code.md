# process 加工单元代码配置

在多维度配置的 `process/tech/<segment>` 目录 `data.default` 中配置 `cell_code`。

固定加工单元序号：

```json
{"code":"sf","cell_code":2}
```

从点位读取序号（短名使用该 segment 的 `point_prefix`，也可使用完整路径）：

```json
{"code":"default","cell_code":{"name":"/aygg_tracking/zrm1/process/tracking/pass_no_pv","type":"short"}}
```

序号必须是 0～999 的整数。时序表中的 `cell_code` 为大写机组代码加三位序号，例如 CP1 的 `2` 写为 `CP1002`。不配置时使用序号 `1`；配置点位但当前值缺失或无效时写入空值，不影响同一段其他结果字段。
