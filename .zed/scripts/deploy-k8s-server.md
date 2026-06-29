# Tracking Kubernetes 部署流程

Tracking 使用一个 Jar、一个镜像、六个 Deployment 和六个 Service。各实例通过 K8S 环境变量 `TRACKING_UNIT` 区分机组。

## 资源约定

| 资源 | 名称 |
| --- | --- |
| Jar | `tracking-0.0.1-SNAPSHOT.jar` |
| 镜像 | `172.16.200.26/quality/tracking:latest` |
| 远端构建目录 | `/opt/anyang/tracking` |
| Deployment / Service | `tracking-cbl1`、`tracking-cp1`、`tracking-csl1`、`tracking-dcl1`、`tracking-fcl1`、`tracking-zrm1` |

六个 Deployment 使用同一镜像，每个 Deployment 必须设置对应机组：

```yaml
env:
  - name: TRACKING_UNIT
    value: cp1
  - name: SPRING_PROFILES_ACTIVE
    value: prod
```

Service 的 selector 必须匹配各自 Deployment Pod 的 label。

## 执行

```sh
export NFS_SSH_PASS='构建服务器密码'
export K8S_SSH_PASS='K8S 控制节点密码'
.zed/scripts/deploy-k8s-server.sh
```

脚本执行顺序：

1. 校验六个 Deployment 和六个 Service 都存在。
2. 本地执行一次 `mvn clean package`。
3. 上传统一 Jar 到 `/opt/anyang/tracking`。
4. 调用根目录的 `build-push.sh`，构建并推送一次统一镜像。
5. 重启六个 Deployment，并逐个等待滚动更新完成。
6. 全部更新成功后删除旧 Jar 备份。

Service 本身不会在代码部署时重启。

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
