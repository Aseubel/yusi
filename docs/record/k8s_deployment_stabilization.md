# Yusi K8s 本地部署稳定化与排障记录

本文记录了在本地 Kind（Kubernetes in Docker）集群中，基于 GitOps（Argo CD + Kustomize）流水线部署并稳定 Yusi 容器栈（Frontend、Backend、MCP）的全过程及排障记录。

---

## 1. 部署架构概述

本地环境采用混合架构：
- **核心容器栈**：在 Kind 集群内运行（位于命名空间 `yusi-prod`），包括：
  - `yusi-frontend` (Nginx + 静态资产)
  - `yusi-backend` (Spring Boot API + gRPC 服务)
  - `yusi-mcp` (Go MCP Server)
  - `yusi-ingress` (NGINX Ingress)
- **依赖外部服务**：直接运行在 WSL 宿主机 Docker 中的基础组件（MySQL、Redis），以及云端向量库（Zilliz Cloud Milvus）。
  - Kind 集群内部通过 `infra` 命名空间下的 **ExternalName** 服务指向宿主机 IP (`host.kind.internal`) 来访问本地 MySQL 和 Redis。

---

## 2. 核心故障与排障记录

在部署调优过程中，主要解决并修复了以下五个关键问题：

### 2.1 环境变量中双引号引发容器崩溃
- **现象**：`yusi-frontend` 启动后崩溃并抛出语法错误，部分依赖项报错包含未知引号。
- **原因**：本地脱敏配置文件 `prod_secrets.txt` 中的密钥值被包裹在双引号 `"` 内。在使用 `envsubst` 注入或 K8s 掛载 Secret 时，双引号被一同作为字符串值读入，导致 Nginx 解析配置模版及后端注入时发生字符冲突。
- **解决**：清理了 `prod_secrets.txt` 中所有字段值的外层双引号，重新生成 K8s Secret。

### 2.2 跨命名空间域名解析超时
- **现象**：后端与 MySQL、Redis 建立连接时发生超时崩溃，报 DNS 解析超时。
- **原因**：之前的 `configmap.yaml` 中配置了跨命名空间的完整域名（FQDN，如 `mysql-service.infra.svc.cluster.local`），而在 Kind 本地网络下由于 search 域规则和 CoreDNS 解析开销，长域名解析容易超时。
- **解决**：将数据库、Redis 和向量库的连接域名统一精简为**短格式跨命名空间域名**（例如：`mysql-service.infra`），极大提升了解析速度和连接稳定性。

### 2.3 Milvus 向量库连接异常 (`Missing hostname in url` 与连接超时)
- **现象**：后端启动抛出 `Missing hostname in url`，导致 `milvusClientV2` 初始化失败。
- **原因**：`backend.yaml` 模版中没有将 `MILVUS_URI` 环境变量映射并注入到容器中，导致 Spring Boot 运行时回退至默认占位符 `your-milvus-cloud-uri`。
- **解决**：
  1. 寻回了之前测试配置的 Zilliz Cloud 云端域名（`https://in03-cf10f8f6eef3a63.serverless.aws-eu-central-1.cloud.zilliz.com`）。
  2. 将 `MILVUS_URI` 写入 `prod_secrets.txt` 并重建 Secret，在 `backend.yaml` 中增加此变量的映射。
  *注：Zilliz 免费服务实例因长时间未使用会进入休眠状态，首次启动调用时可能引发 `DEADLINE_EXCEEDED` 连接超时，触发 Pod 重启。一旦实例被自动唤醒后，后续连接均能瞬间成功建立。*

### 2.4 MySQL 8.0 缓存加密认证失败 (`Public Key Retrieval is not allowed`)
- **现象**：后端已解析到本地 MySQL 域名但抛出上述异常。
- **原因**：MySQL 8.0 默认使用 `caching_sha2_password` 插件。在非 SSL 通道连接时，JDBC 驱动出于安全性默认禁止客户端向服务端检索公钥进行密码传输加密。
- **解决**：在 `backend.yaml` 的 `SPRING_DATASOURCE_URL` 中添加 `allowPublicKeyRetrieval=true` 参数。

### 2.5 密钥配置未映射导致 RSA 校验失败 (`Backup RSA private key is not valid Base64`)
- **现象**：后端报错 `Illegal base64 character 2d`（ASCII `0x2d` 即 `-` 字符）并崩溃。
- **原因**：`YUSI_RSA_PRIVATE_KEY`、`YUSI_RSA_PUBLIC_KEY` 和 `YUSI_JWT_SECRET` 未在 Deployment 的 `env:` 中注入，Spring Boot 读取了 `application-prod.yml` 中的默认带连字符占位符（如 `your-rsa-private-key-placeholder`），在 HUTool 的 Base64 解码器中引发了字符解析错误。
- **解决**：在 `backend.yaml` 中添加上述三个环境变量的 Secret 映射。

### 2.6 端口不一致引发存活/就绪探针失败 (Readiness Probe Failed)
- **现象**：后端服务日志显示已经 `Started YusiApplication`，但 Pod 状态长时间保持 `0/1 READY`，最终超时被 Kubernetes 强行重启。
- **原因**：`application-prod.yml` 配置的 Tomcat 监听端口为 `611`（`server.port: 611`），而 `yusi-infra` 的 Deployment 中配置的 `containerPort`、`readinessProbe`、`livenessProbe`、`Service` 端口以及 `Ingress` 转发目标端口均为 `20611`。
- **解决**：将 `yusi-infra` 中所有关于 `yusi-backend` 的容器端口、探针、Service 以及 Ingress 配置端口统一调整对齐为 `611`。

---

## 3. 稳定状态验证

在全部修复推送并由 Argo CD 完成同步后，集群状态已回归稳定：

### 3.1 Pod 运行情况
执行 `kubectl get pods -n yusi-prod` 状态如下：
```text
NAME                            READY   STATUS    RESTARTS   AGE
yusi-backend-6b4d689799-gdvrw   1/1     Running   0          5m
yusi-backend-6b4d689799-vgrrv   1/1     Running   2          5m
yusi-frontend-cdd967b98-5d446   1/1     Running   0          13m
yusi-frontend-cdd967b98-zqqlc   1/1     Running   0          13m
yusi-mcp-6cff75c9f9-kkf69       1/1     Running   0          14m
```

### 3.2 服务连通性与健康状态
调用后端的 `/actuator/health` 接口，服务能够正确返回运行指标：
```json
{"status":"UP","groups":["liveness","readiness"]}
```
Tomcat 监听 `611`，gRPC 监听 `9090`，均运行正常，且成功与本地 MySQL、Redis、MCP Server 以及云端 Zilliz Milvus 建立了稳定的连接池通道。

---

## 4. 本地环境常用调试命令

### 4.1 容器状态与日志追踪
- **查看 Pod 列表**：`kubectl get pods -n yusi-prod`
- **查看后端实时日志**：`kubectl logs -n yusi-prod -l app=yusi-backend -c backend -f --tail=100`
- **查看前端 Access 日志**：`kubectl logs -n yusi-prod -l app=yusi-frontend -f --tail=100`

### 4.2 本地 Secret 与配置同步
- **本地修改 `prod_secrets.txt` 后更新 K8s**：
  ```bash
  kubectl delete secret -n yusi-prod yusi-secret
  kubectl create secret generic yusi-secret -n yusi-prod --from-env-file=prod_secrets.txt
  ```
- **强制滚动重启后端以应用新配置**：
  ```bash
  kubectl rollout restart deployment/yusi-backend -n yusi-prod
  ```
- **强行命令 Argo CD 立刻从 Git 拉取并同步配置**：
  ```bash
  kubectl annotate app -n argocd yusi-app-prod argocd.argoproj.io/refresh=hard --overwrite
  ```

### 4.3 网关及 DNS 劫持测试
本地若运行了代理工具（如 Clash），容器域名解析可能被 Fake-IP 规则劫持。在容器内测试网络联通性时，请加尾部绝对点 `.` 来规避 DNS 自动补全拦截：
```bash
# 测试本地前端 Ingress 响应
kubectl exec -n ingress-nginx deploy/ingress-nginx-controller -- curl -sI http://yusi-frontend.yusi-prod.svc.cluster.local.:8080/
```
