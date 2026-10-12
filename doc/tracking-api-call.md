# tracking-api 集群内调用说明

供 Kubernetes 其他命名空间中的服务调用。`tracking-api` 的 Service 位于 `tracking` 命名空间，服务端口为 `8080`。调用方使用集群内 DNS 地址：

```text
http://tracking-api.tracking.svc.cluster.local:8080
```

集群 DNS 搜索域为默认配置时，也可使用 `http://tracking-api.tracking:8080`。这里使用的是 Service 端口，不是 `32338` NodePort。调用方 Pod 需要能解析集群 DNS，且所在命名空间的 NetworkPolicy 允许访问该 Service。

## 道次历史补全

目前 `tracking-api` 对外提供以下业务接口：

```text
POST /openapi/retracking/zrm1/status/passHistory
Content-Type: application/json
```

完整请求示例：

```bash
curl -X POST 'http://tracking-api.tracking.svc.cluster.local:8080/openapi/retracking/zrm1/status/passHistory' \
  -H 'Content-Type: application/json' \
  -d '{"startTime":"2026-10-09 00:00:00","endTime":"2026-10-09 01:00:00"}'
```

| 字段 | 说明 |
| --- | --- |
| `startTime` | 必填，开始时间，包含该时刻。 |
| `endTime` | 必填，结束时间，不包含该时刻，必须晚于 `startTime`。 |

时间按上海时区解释，格式为 `yyyy-MM-dd HH:mm:ss`，可带 1～9 位小数秒，例如 `2026-10-09 00:00:00.123`。

HTTP 200 表示请求已处理，响应为 `R` 包装的道次结果列表：

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
      "output": { "unitCode": "zrm1" },
      "sourceTimes": {}
    }
  ],
  "success": null
}
```

示例中的 `output` 和 `sourceTimes` 仅展示结构，实际响应包含更多字段。调用方应逐项检查 `data[].status`：`SUCCESS` 为质量写入成功，`SKIPPED` 为该道次不满足补全条件，`FAILED` 为该道次查询或写入失败；后两者可查看 `reason`。没有符合条件的道次时，`data` 为 `[]`。参数或时间格式错误返回 HTTP 400；全局处理异常通常返回 HTTP 500，错误响应不保证采用 `R` 格式。

接口同步扫描历史数据并调用质量服务，没有后台任务或任务查询接口。请求耗时取决于时间跨度和道次数量，调用方需设置足够的 HTTP 超时。重复提交会重新扫描并尝试写入，超时后不要自动重试整个 POST 请求，应先核对处理结果。

字段细节、状态判定与处理规则见[道次历史补全接口](pass-history-tracking-api.md)。
