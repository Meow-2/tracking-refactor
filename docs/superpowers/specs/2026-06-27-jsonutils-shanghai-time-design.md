# JsonUtils 上海时区时间展示设计

## 目标

`JsonUtils.toPrettyJson(Object)` 序列化 `Instant` 时，按 `Asia/Shanghai` 展示带偏移量的 ISO-8601 时间，例如：

```json
{
  "receivedAt": "2026-06-27T08:29:40.323+08:00"
}
```

## 范围

- 仅修改 `JsonUtils.toPrettyJson(Object)` 使用的默认 `ObjectMapper`。
- 保持领域模型中的时间类型为 `Instant`。
- 不改变 RocketMQ 消息反序列化行为。
- 不改变时序数据库写入的 Unix 毫秒时间戳。
- 不改变接收外部 `ObjectMapper` 的 `toPrettyJson(ObjectMapper, Object)`；其格式仍由调用方配置决定。

## 设计

在 `JsonUtils` 中定义固定的 `Asia/Shanghai` 时区和 ISO 偏移日期时间格式，并为默认 `JavaTimeModule` 注册 `Instant` 序列化器。序列化器先将 `Instant` 转换到上海时区，再输出包含 `+08:00` 偏移量的字符串。

不依赖 `ObjectMapper#setTimeZone`，因为 Java Time 模块对 `Instant` 的默认序列化通常仍使用 UTC `Z` 表示。

## 测试

采用测试驱动方式：

1. 添加 `JsonUtils` 测试，输入固定的 `Instant`：`2026-06-27T00:29:40.323Z`。
2. 先验证当前实现输出 UTC `Z`，测试因未输出上海时区而失败。
3. 注册自定义序列化器。
4. 验证输出为 `2026-06-27T08:29:40.323+08:00`。
5. 运行相关测试及项目全量测试，并如实记录既有失败。

## 兼容性

这是展示格式变更。任何依赖 `JsonUtils.toPrettyJson(Object)` 输出中 `Instant` 必须以 `Z` 结尾的调用方都会受到影响；时间代表的瞬间不变。
