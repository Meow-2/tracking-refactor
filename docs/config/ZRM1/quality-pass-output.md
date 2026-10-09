# ZRM1 换道质量接口部署配置

1. 将同目录 `status.json` 中的 `rolling.quality_output_enabled`、`quality_min_speed` 和 `out_thickness_point` 同步到 Cube 的 `zrm1/status` 配置。
2. 在 Cube 的 `/aygg_tracking/zrm1/status/tracking/` 下增加直属叶子点位 `exit_thickness_pv`，其物理点位 `id` 为 `exit_thickness_pv`、类型为浮点。采集器的 `zrm1/status` 路由会按 Cube 叶子点位生成 MQTT 字段；仅修改 tracking 配置而不增加叶子点位，status 消息不会含厚度值。
3. 通过各环境的 `quality.base-url` 配置质量服务根地址，不含 `/api/quality/material/cell-blood/output`；Kubernetes ConfigMap 使用 `QUALITY_BASEURL` 覆盖该配置，未设置时使用 YAML 默认值。`quality.connectTimeout` 和 `quality.readTimeout` 的默认值分别为 3000、5000 毫秒。
4. 确认该厚度点位单位为 mm，卷取端长度点位单位为 m。接口时间使用上海本地 `yyyy-MM-dd HH:mm:ss`。
5. 异步发送任务仅保存在内存中；重启、队列拒绝或三次重试失败时，按日志中的机组、入口卷、生产次数和加工单元定位并人工处理。
