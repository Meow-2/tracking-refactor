# 跟踪历史元信息入口

按机组和跟踪类型获取算法配置，以及配置节点子树中的所有点位历史查询元信息：

```java
TrackingHistoryMetadata metadata = cubeApiGateway.fetchHistoryMetadata("zrm1", TrackingType.STATUS);
StatusTrackingConfig config = (StatusTrackingConfig) metadata.getTrackingConfig();
Map<String, PointHistoryMetadata> points = metadata.getPointMetadata();
```

其他类型使用相同入口，例如 `fetchHistoryMetadata("cp1", TrackingType.PROCESS)`；
返回配置的实际类型由已注册的 `CubeApiTrackingConfigConverter` 决定。
未注册转换器的类型明确报错。

每次调用按 `cube-api.tree-root` 请求 Cube 配置树，显式选择业务机组和跟踪类型，
不依赖实例的 `tracking.unit`，不读写 Redis 配置缓存或实时运行态。
实时跟踪继续使用原 `fetchTrackingConfigs()`，按实例的 `tracking.unit` 读取配置。

## 配置节点与点位范围

- 机组和跟踪类型目录按编码忽略大小写查找，缺失或重复时报错。
- 以对象形式的 `data.default` 识别配置节点。类型目录自身有配置时使用该目录；
  其下工艺段的 `data.default` 属于当前配置的一部分，不作为竞争候选。
- 类型目录自身没有配置时向下搜索，必须找到唯一配置节点；多个候选不做猜测。
- 对已定位的配置节点调用对应类型转换器，转换异常直接传播。
- 仅收集该配置节点子树内的点位，包括嵌套目录和工艺段下的点位，排除树外点位。
- 优先通过 `itemType=2` 识别点位；旧数据缺少 `itemType` 时，
  以带 `code` 或 `cubeKey`、且不含配置的叶子节点识别。显式目录类型不作为点位。

本入口加载全部子树点位元信息，不按 status 道次补全业务手工筛选点位，
也不额外要求 rolling、厚度点或质量速度阈值。各类型配置转换器原有的算法配置校验仍执行。

## 历史查询映射

`PointHistoryMetadataConverter` 按完整路径建立映射；缺少路径时按树层级还原，
优先使用 Cube 提供的路径。映射保留显示名称、code、cubeKey、值类型、单位和有效标记。
值类型读取节点的 `valueType`，或回退到 `data.valueType`；未提供时不推测实际类型。

数据库使用 `cube`，字段为 `code`，对象名通过从 `cubeKey` 去掉末尾 `_<code>` 得到。
数据库名、对象名及字段名均需通过标识符校验。路径重复、`valid=false`、
cubeKey 后缀不匹配或非法标识符直接报错。返回点位映射不可增删。

该入口只解析元信息，不查询 TDengine schema 或历史值，不计算道次，
不调用质量接口，也不开放 HTTP 接口。
