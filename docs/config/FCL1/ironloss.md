# FCL1 铁损跟踪 Cube 配置

- 将 `ironloss.json` 写入 `/aygg_tracking/fcl1/ironloss` 的 `data.default`。
- 将 `ironloss-iron_loss.json` 写入 `/aygg_tracking/fcl1/ironloss/tech/iron_loss` 的 `data.default`。
- `tracking` 目录中的 `coil_no`、`length` 和 `tech/iron_loss` 中的参数点位由 Cube 元数据提供，不需要写入 `data.default` 的点位列表。
- 示例阈值为 `0.1`，实际判断是 `length >= threshold`；修改阈值只需更新 `start_condition.threshold`。
- 启用后建表名为 `FCL1_ironloss_iron_loss`，生产次数取同卷号的 STATUS 上下文。`PROCESS` 旧 `iron_loss` 段由配置维护方另行移除。
