# 道次历史补全接口

## 调用

```text
POST /openapi/retracking/zrm1/status/passHistory
Content-Type: application/json
```

```json
{
  "startTime": "2026-10-09 00:00:00",
  "endTime": "2026-10-09 01:00:00"
}
```

仅需 startTime、endTime。时间按上海时区解释，格式为 `yyyy-MM-dd HH:mm:ss`，可带 1～9 位小数秒，日期与时间之间固定一个空格。开始时间必须早于结束时间，不限制查询跨度。

接口同步处理并返回结果，不创建任务，不提供任务查询接口。重复提交会重新扫描并调用质量网关。

排查 quality-ts 调用时，tracking 日志会记录 `/data/query/states`、`bounds`、`latest` 的
请求参数、耗时（毫秒）和结果条数或首末时间；请求失败记录底层异常堆栈，响应失败记录
返回码、成功标记和消息。日志不展开查询结果列表。quality-ts 对应日志记录请求参数、
完成耗时和结果概况，参数错误及数据库异常由历史查询异常处理器记录。

## 返回

HTTP 200 使用 `R<List<PassOutputResult>>`，示例：

```json
{
  "code": "200",
  "message": "success",
  "data": [
    {
      "passNo": 1,
      "switchTime": "2026-10-09T00:10:00",
      "status": "SUCCESS",
      "reason": null,
      "output": {
        "unitCode": "zrm1",
        "inMatNo": "COIL-A",
        "inMatRepeatProdNo": 1,
        "cellCode": "ZRM1001",
        "startAt": "2026-10-09T00:00:02.123456789",
        "endAt": "2026-10-09T00:09:59.123456789",
        "outMatThick": 0.28,
        "outMatLength": 125.5
      },
      "sourceTimes": {
        "2026-10-09 00:09:59.123456789//example/coil_no": "2026-10-09T00:09:59"
      }
    }
  ],
  "success": null
}
```

Controller 和历史计算统一使用上海本地 LocalDateTime，保留小数秒。响应 switchTime、sourceTimes、output.startAt 和 output.endAt 均使用无时区后缀的 ISO 上海本地时间；请求及 quality-ts 查询仍使用空格分隔的上海时间格式。sourceTimes 的键为“取值帧时间/点位完整路径”，值为点位实际来源时间。RollingPassOutput 内部仍保存 Instant，返回 JSON 时转换为上海时间展示，不改变质量写入时的时间换算。

| 状态 | 含义 |
| --- | --- |
| SUCCESS | 质量网关调用成功，质量响应 code=200 |
| SKIPPED | 没有速度达标帧、点位缺失或过期、卷号或数值无效、首末帧入口卷不一致、无法确定设备对等；reason 给出原因 |
| FAILED | 单道次历史查询、次数查询或质量调用失败；reason 给出原因，质量写入失败时保留 output |

HTTP 200 表示处理完成，需逐项检查 status；没有切换事件时 data 为 `[]`。参数错误返回 HTTP 400；配置加载或全局扫描异常交由 Spring Boot 默认错误处理，通常返回 HTTP 500。控制器不自定义错误响应，也不保证错误响应为 R 格式。

## 处理规则

1. 固化本次 Cube `zrm1/status` 配置，查询 `[startTime-1h,endTime)` 的道次区间。
2. 相邻道次递增时结算前段，只保留切换时间位于 `[startTime,endTime)` 的事件；跳号不补造中间道次，回退、归零不结算，最后一段不结算。按已确认选择，固定 1 小时回溯，不增加起点完整性判定。
3. 用前段自身 `[firstTime,lastTime]` 查询速度严格大于 quality_min_speed 的首末帧，不因其他字段缺失移动时间。
4. 复用 status 设备判断、卷号清洗及 cell_code 规则，读取首末帧入口卷号与末帧卷取长度、出口厚度；最近值最大滞后 5 秒，并保留来源时间。
5. 只读查询同机组同卷 `create_time < passStartTime` 的有效记录最大重复生产次数，无记录默认 1，不分配新次数。
6. 所有历史扫描和数据准备完成后，按切换顺序同步调用现有 QualityServiceGateway，复用请求转换和最多三次尝试；单道次失败继续，结果中不返回 READY。

质量请求时间继续使用现有网关的秒级上海时间格式；历史查询和返回结果保留原始时间精度。调用不会修改点位历史、实时配置缓存、实时运行态或生产次数记录。

## 配置与部署

```yaml
tracking:
  unit: api
```

Cube、quality、PostgreSQL 和 time-series-history 连接沿用现有配置，history 查询 readTimeout 为 60000 毫秒；这个超时只限制单次查询，不限制整个同步请求。

现有 api 部署保持一个副本、Recreate 更新策略、NodePort 服务。接口没有任务状态、任务表或重启恢复；实例停止可能中断请求。客户端或入口代理的 HTTP 超时需按实际时间跨度和道次数量设置；不自动重试整个 POST 请求。

验收应在明确的联调环境与时间范围内调用：核对道次切换、首末帧、来源时间、质量请求字段和每项结果。本次自动化测试使用 Mock 质量客户端，不执行真实质量写入；实际部署和历史样本联调尚未执行。
