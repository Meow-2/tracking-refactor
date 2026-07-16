# Tracking Kubernetes 部署流程

Tracking 使用一个 Jar、一个镜像、七套机组资源。各实例通过 ConfigMap 中的 `TRACKING_UNIT` 区分机组。

## 资源约定

| 资源 | 名称 |
| --- | --- |
| Jar | `tracking-0.0.1-SNAPSHOT.jar` |
| 镜像 | `172.16.200.26/quality/tracking:latest` |
| 远端构建目录 | `/opt/anyang/tracking` |
| 本地 K8s 配置目录 | `deploy/k8s` |
| 远端 K8s 配置目录 | `/data/mainfest/tracking` |
| Deployment / Service | `tracking-baf1`、`tracking-cbl1`、`tracking-cp1`、`tracking-csl1`、`tracking-dcl1`、`tracking-fcl1`、`tracking-zrm1` |

七个 Deployment 使用同一镜像，每个机组的 ConfigMap 必须设置对应机组：

```yaml
data:
  TRACKING_UNIT: "cp1"
  TRACKING_STORAGE_ABNORMAL_ENABLED: "true"
  TRACKING_STORAGE_TRACKINGRESULT_ENABLED: "true"
```

Service 的 selector 必须匹配各自 Deployment Pod 的 label。

## 执行

```sh
export NFS_SSH_PASS='构建服务器密码'
export K8S_SSH_PASS='K8S 控制节点密码'
.zed/scripts/deploy-k8s-server.sh
```

脚本执行顺序：

1. 本地执行一次 `mvn clean package`。
2. 创建远端 `/opt/anyang/tracking`，上传统一 Jar、Dockerfile 和 `build-push.sh`。
3. 调用上传后的 `build-push.sh`，构建并推送一次统一镜像。
4. 将 `deploy/k8s` 完整上传到 K8s 控制节点的 `/data/mainfest/tracking`。
5. 幂等创建 `tracking` Namespace，再在远端递归执行 `kubectl apply`，创建或更新七套 Deployment、Service、ConfigMap 和 PVC。
6. 校验七套资源存在，重启七个 Deployment，并逐个等待滚动更新完成。
7. 全部更新成功后删除旧 Jar 备份。

Service 本身不会在代码部署时重启。

上述流程同时适用于初次部署和后续更新。初次部署时不要求远端构建目录、K8s 配置目录、Deployment、Service、ConfigMap、PVC 或 Namespace 预先存在；集群仍需预先安装 `nfs-csi` StorageClass，构建服务器需要 Docker 并能够推送镜像，集群节点需要能够拉取项目镜像。

## 远端 build-push.sh

```sh
#!/bin/bash
set -e

IMAGE=172.16.200.26/quality/tracking:latest

docker rmi -f "${IMAGE}" || true
docker build -t "${IMAGE}" .
docker push "${IMAGE}"
```

远端根目录需要放置该脚本和 Dockerfile。Deployment 使用 `latest` 时应设置 `imagePullPolicy: Always`，否则滚动更新可能继续使用节点缓存镜像。
