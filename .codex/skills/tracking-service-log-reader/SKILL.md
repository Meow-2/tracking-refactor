---
name: tracking-service-log-reader
description: 读取 tracking 服务指定机组和跟踪类型在指定日期的日志；按 tracking 代码、Kubernetes PVC 和 NFS CSI 卷目录定位日志。
---

# Tracking 服务日志读取

## 目标

在只读前提下，根据 tracking 代码中的机组、跟踪类型和日志日期，定位并读取 Kubernetes/NFS 上的服务日志。默认使用已配置的 SSH MCP Server，不负责安装或修改 SSH 连接。

## 与 tracking 代码对齐的名称

| 查询概念 | tracking 代码名称 | 示例 | 用途 |
| --- | --- | --- | --- |
| 机组 | `unitCode`、配置项 `tracking.unit`、环境变量 `TRACKING_UNIT` | `cp1` | 派生 Deployment、Service 和 PVC 名称。 |
| 跟踪类型 | Java 枚举 `TrackingType`、日志 MDC key `trackingType` | `shear` | 派生 `tracking-step-shear` 日志前缀。 |
| 日志流 | `tracking-step`、`application`、`error` | `tracking-step` | 选择算法步骤日志或通用服务日志。 |
| 日志日期 | Logback 文件名中的 `%d{yyyy-MM-dd, Asia/Shanghai}` | `2026-09-16` | 按上海时区筛选归档文件。 |
| 容器日志目录 | ConfigMap 的 `LOGGING_FILE_PATH`、Deployment 的挂载点 | `/logs` | 容器内日志根目录；NFS 对应卷目录。 |

当前 `TrackingType` 代码值为：`process`、`batch`、`status`、`coiler`、`shear`、`trimming`。查询时使用小写 `code`，不要使用中文描述或 Java 枚举大写名拼接文件名。

相关代码事实来自：

- `src/main/java/com/wisdri/tracking/domain/model/tracking/TrackingType.java`
- `src/main/resources/logback-spring.xml`
- `deploy/k8s/<unit>/tracking-<unit>-app.yaml`
- `deploy/k8s/<unit>/tracking-<unit>-cm.yaml`

## 稳定路径配置

普通 Deployment rollout 只重建 Pod，不会改变同一个 PVC 绑定的 PV/CSI 目录。当前 NFS 根目录是 `/mnt/nfs`，实际卷目录按 PV/CSI 的 `VOLUME_NAME` 保存，而不是按 PVC 名保存。

以下映射来自 2026-09-17 对 `tracking` 命名空间的实际查询，普通日志读取可直接使用：

| `unitCode` | PVC | `VOLUME_NAME` | NFS 日志目录 |
| --- | --- | --- | --- |
| `baf1` | `tracking-baf1-pvc` | `pvc-b7c7299f-234a-41ca-b53d-452783ba3b1e` | `/mnt/nfs/pvc-b7c7299f-234a-41ca-b53d-452783ba3b1e` |
| `cbl1` | `tracking-cbl1-pvc` | `pvc-7a223eea-34ca-470b-815b-0118e7726d08` | `/mnt/nfs/pvc-7a223eea-34ca-470b-815b-0118e7726d08` |
| `cp1` | `tracking-cp1-pvc` | `pvc-02e5d942-c6af-495a-abb5-75d499a0088a` | `/mnt/nfs/pvc-02e5d942-c6af-495a-abb5-75d499a0088a` |
| `csl1` | `tracking-csl1-pvc` | `pvc-21b7754b-1996-4ddf-a650-38120c685bc7` | `/mnt/nfs/pvc-21b7754b-1996-4ddf-a650-38120c685bc7` |
| `dcl1` | `tracking-dcl1-pvc` | `pvc-1b362e92-2d8c-4f1a-b995-7c1d86e84aed` | `/mnt/nfs/pvc-1b362e92-2d8c-4f1a-b995-7c1d86e84aed` |
| `fcl1` | `tracking-fcl1-pvc` | `pvc-65699ecc-cccf-416a-96ab-3a6dfaa69845` | `/mnt/nfs/pvc-65699ecc-cccf-416a-96ab-3a6dfaa69845` |
| `zrm1` | `tracking-zrm1-pvc` | `pvc-7985345b-b9a3-4c08-9b7e-b445534a64a0` | `/mnt/nfs/pvc-7985345b-b9a3-4c08-9b7e-b445534a64a0` |

只有以下情况才重新查询 Kubernetes 并更新映射：PVC 不在表中、NFS 目录不存在、用户明确提到 PVC/PV 重建或迁移，或查询结果与表不一致。PVC 删除后重建时，即使名称相同，也可能得到新的 `VOLUME_NAME`。

## 查询流程

### 1. 提取并规范化变量

从用户请求提取：

```text
unitCode=<机组编码，例如 cp1>
trackingType=<TrackingType.code，例如 shear>
logStream=<tracking-step|application|error，默认 tracking-step>
date=<YYYY-MM-DD；“昨天”按当前会话时区计算>
namespace=tracking
```

`CP1`、`cp1`、`TrackingType.SHEAR`、`shear` 等输入应分别规范为 `unitCode=cp1`、`trackingType=shear`。缺少机组、跟踪类型或日期且无法安全推断时，再向用户询问。

### 2. 派生 tracking 日志名称

Logback 的实际命名规则是：

```text
实时文件：${LOG_DIR}/tracking-step-${trackingType}.log
归档文件：${LOG_DIR}/archive/tracking-step-${trackingType}.<date>.<index>.log.gz
```

因此，`unitCode=cp1`、`trackingType=shear`、`date=2026-09-16` 时匹配：

```text
/mnt/nfs/pvc-02e5d942-c6af-495a-abb5-75d499a0088a/archive/tracking-step-shear.2026-09-16.*.log.gz
```

通用服务日志使用 `application.<date>.*.log.gz` 或 `error.<date>.*.log.gz`；它们不带 `trackingType`。

### 3. 通过 SSH MCP Server 读取

先调用 `mcp__ssh_mcp_server__list_servers`，确认已配置的 Kubernetes 和 NFS 连接。常见连接名为 `k8s-deploy` 与 `nfs-logs`，但应以工具返回结果为准。

正常情况下直接使用上面的稳定路径，在 NFS 服务器上只读执行目录检查和日志筛选。仅在需要重新核对映射时，在 Kubernetes 服务器执行：

```text
kubectl get pvc -n tracking -o wide
kubectl get pvc tracking-<unitCode>-pvc -n tracking -o yaml
```

从 `VOLUME` 字段得到 `VOLUME_NAME`，再检查 `/mnt/nfs/<VOLUME_NAME>`。不得把 `/mnt/nfs/tracking-<unitCode>-pvc` 当成默认路径。

使用 `mcp__ssh_mcp_server__execute_command` 做只读元数据和摘要查询；远端命令白名单拒绝压缩流式读取时，使用 `mcp__ssh_mcp_server__download` 下载已确认的日志后在本地解压。不得绕过命令校验。

### 4. 处理轮转和结果

同一天可能有多个 `<index>` 文件，全部纳入结果。按文件名日期和内容 timestamp 确认覆盖范围，注意跨午夜轮转；不要只依赖 NFS 文件修改时间。

结果至少说明：

1. 实际的 `unitCode`、`trackingType`、`logStream`、日期和时区。
2. PVC、`VOLUME_NAME` 和 NFS 目录。
3. 每个匹配文件的完整路径、压缩状态和时间范围；下载后提供本地文件链接。
4. 日志行数、关键事件或异常摘要，不要直接贴出大段原始日志。
5. 目录不存在、映射可能过期、权限不足或日期边界无法确认等风险。

## 安全边界

- 只读 Kubernetes、NFS 和日志；禁止删除、移动、覆盖、清理或修改远端文件。
- 不输出密码、私钥、令牌或生产配置中的敏感凭据。
- 生产日志通常很大，默认交付路径和摘要；只有用户明确要求时才下载完整日志。
- `unitCode`、PVC 名、`VOLUME_NAME`、`trackingType` 和日志文件前缀是不同层级的名称，必须分别记录，不能混用。
