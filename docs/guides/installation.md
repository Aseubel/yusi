# Yusi 安装及使用指南

---

## 1. 环境要求

### 1.1 硬件要求

| 资源 | 最低配置 | 推荐配置 |
|:---|:---|:---|
| CPU | 2 核 | 4 核+ |
| 内存 | 4 GB | 8 GB+ |
| 磁盘 | 20 GB | 50 GB+ SSD |

### 1.2 软件依赖

| 软件 | 版本要求 | 说明 |
|:---|:---|:---|
| Docker | 20.10+ | 容器化部署 |
| Docker Compose | 2.0+ | 多容器编排 |
| MySQL | 8.0+ | 推荐使用 Docker 部署 |
| Redis | 6.0+ | 推荐使用 Docker 部署 |

### 1.3 第三方服务

| 服务 | 必要性 | 获取方式 |
|:---|:---|:---|
| AI 模型 API | 必须 | SiliconFlow / OpenAI / 其他 LangChain4j 支持的渠道 |
| 向量模型 API | 必须 | SiliconFlow BAAI/bge-m3 或其他 embedding 模型 |
| 高德地图 API | 可选 | 日记定位功能 |
| 阿里云邮件 | 可选 | 邮件推送功能 |
| 阿里云 OSS | 可选 | 图片存储功能 |
| Milvus 向量库 | 可选 | 云版 Zilliz Cloud 或自建 |

---

## 2. 快速安装 (Docker)

### 2.1 默认配置启动

**Step 1：克隆代码**
```bash
git clone https://github.com/your-repo/yusi.git
cd yusi
```

**Step 2：配置环境变量**
```bash
# Copy the complete production variable template and fill in its values.
cp yusi_prod.env.example yusi_prod.env
chmod 600 yusi_prod.env
# Edit yusi_prod.env with the production credentials.
```

**Step 3：启动服务**
```bash
# 启动 MySQL 和 Redis
docker network create my-network
docker-compose -f docker-compose.yml up -d mysql redis

# 等待 MySQL 就绪 (约10秒)
sleep 10

# 初始化数据库
docker exec -i yusi-mysql mysql -uroot -p123456 < src/main/resources/db/init.sql

# 启动 Yusi 应用
docker-compose -f docker-compose.yml up -d yusi

# 启动 MCP 网关。它读取同一个文件，但只注入 MCP 所需变量。
docker compose --env-file yusi_prod.env -f mcp/docker-compose.yml up -d
```

**Step 4：验证服务**
```bash
# 检查容器状态
docker-compose -f docker-compose.yml ps

# 查看应用日志
docker-compose -f docker-compose.yml logs -f yusi
```

服务启动后，访问 `http://localhost:20611` 即可使用。

### 2.2 一键启动 (完整环境)

如需同时启动 MySQL、Redis 和 Yusi：

```bash
# 创建外部网络
docker network create my-network 2>/dev/null || true

# 一键启动
docker-compose -f docker-compose.yml up -d

# 初始化数据库
docker exec -i yusi-mysql mysql -uroot -p123456 < src/main/resources/db/init.sql
```

---

## 3. 典型使用流程

### 3.1 用户注册与登录

```
1. 访问注册页面 /register
2. 填写用户名、邮箱、密码
3. 选择密钥模式:
   - DEFAULT (服务端管理密钥): 简单便捷，服务端加密
   - CUSTOM (用户自定义密钥): 端到端加密，密钥本地保存
4. 注册成功后自动登录
```

### 3.2 写日记

```
1. 进入「写日记」页面
2. 输入日记内容
3. (可选) 添加图片
4. (可选) AI 自动分析情感
5. 点击保存
6. 系统自动:
   - 加密存储内容
   - 提取实体关系构建人生图谱
   - 生成向量索引
```

### 3.3 人生图谱

```
1. 进入「人生图谱」页面
2. 查看自动构建的知识图谱:
   - 人物关系
   - 地点轨迹
   - 情感变化
   - 事件时间线
3. 点击实体查看详情和证据
4. 使用自然语言搜索图谱
```

### 3.4 灵魂匹配

```
1. 开启「灵魂匹配」开关
2. 设置匹配偏好
3. 每日凌晨系统自动匹配
4. 收到 AI 生成的推荐信
5. 决定「感兴趣」或「跳过」
6. 双向感兴趣则匹配成功，开启私信
```

### 3.5 情景室

```
1. 创建或加入情景室
2. 房间满2人后房主点击「开始」
3. 每人提交对情景的描述
4. AI 分析生成洞察
5. 全员投票决定是否继续深入
```

### 3.6 AI 记忆漫游 (MCP)

```
1. 在支持的 AI 客户端中连接 Yusi MCP Server
2. AI 可通过工具查询:
   - memorySearch: 搜索用户记忆
   - diarySearch: 检索日记内容
   - web_search: 互联网搜索
3. 实现「数字分身」效果
```

---

## 4. 配置详解

### 4.1 应用配置 (application-prod.yml)

| 配置项 | 说明 | 示例 |
|:---|:---|:---|
| server.port | 应用端口 | 20611 |
| spring.datasource.url | 数据库地址 | jdbc:mysql://mysql:3306/yusi |
| redis.sdk.config.* | Redis 连接配置 | host, port, password |
| model.embedding.* | 向量模型配置 | baseurl, apikey, model |
| yusi.security.crypto.* | 加密配置 | AES密钥, RSA密钥对 |
| mcp.enabled | 是否启用MCP | true/false |

### 4.2 AI 模型路由配置

```yaml
model:
  routing:
    schema-version: 2
    default-language: zh        # 默认语言
    default-scene: chat        # 默认场景
    default-tier: chat-primary
    default-route:
      id: default
      language: '*'
      scene: '*'
      risk-level: LOW
      primary-tier: chat-primary
      fallback-tiers: [chat-fallback]
      priority: 0
    failure-threshold: 3        # 熔断失败阈值
    recovery-success-threshold: 2  # 恢复成功阈值
    models:
      - id: qwen-main
        display-name: Qwen main
        provider: dashscope
        protocol: CHAT_COMPLETIONS
        baseurl: ${CHAT_MODEL_BASEURL}
        apikey: ${CHAT_MODEL_APIKEY}
        model: ${CHAT_MODEL_NAME}
        context-window-tokens: ${CHAT_MODEL_CONTEXT_WINDOW_TOKENS}
        capabilities: [CHAT, STREAMING_CHAT]
        weight: 100
        priority: 1
        languages: [zh, en, ja]
        scenes: [chat, situation-analysis, memory-extract]
      - id: responses-model
        display-name: Responses model
        provider: openai-compatible
        protocol: RESPONSES
        baseurl: ${RESPONSES_MODEL_BASEURL}
        apikey: ${RESPONSES_MODEL_APIKEY}
        model: ${RESPONSES_MODEL_NAME}
        capabilities: [CHAT, STREAMING_CHAT]
      - id: claude-messages
        display-name: Claude Messages
        provider: anthropic
        protocol: ANTHROPIC_MESSAGES
        baseurl: ${ANTHROPIC_BASEURL:https://api.anthropic.com}
        apikey: ${ANTHROPIC_APIKEY}
        model: ${ANTHROPIC_MODEL}
        capabilities: [CHAT, STREAMING_CHAT]
    tiers:
      chat-primary:
        display-name: Primary chat
        strategy: FAIL_OVER
        members: [qwen-main, responses-model]
        capabilities: [CHAT, STREAMING_CHAT]
      chat-fallback:
        display-name: Fallback chat
        strategy: FAIL_OVER
        members: [claude-messages]
        capabilities: [CHAT, STREAMING_CHAT]
    routes:
      - id: zh-chat
        language: zh
        scene: chat
        risk-level: LOW
        primary-tier: chat-primary
        fallback-tiers: [chat-fallback]
        max-input-tokens: 12000
        max-output-tokens: 512
        enabled: true
        priority: 100
```

模型注册表中的 `protocol` 决定实际线协议：`CHAT_COMPLETIONS` 和 `RESPONSES` 由 OpenAI-compatible adapter 创建客户端，`ANTHROPIC_MESSAGES` 必须与 `anthropic` provider 配对。`openai`、`deepseek`、`dashscope` 是 OpenAI-compatible adapter 的 provider 别名；provider 与 protocol 不匹配时配置不会发布。

路由会在调用 Provider 前做一次保守的 Token 估算：消息文本、工具参数和图片会计入输入预算，`max-input-tokens` 限制单条 route 的输入，模型的 `context-window-tokens` 限制估算输入与预留输出之和。未配置 route 输出上限时默认预留 1024 token；超出主模型上下文时会继续检查 fallback tier。该估算只负责准入和候选过滤，Provider 返回的真实 usage 才是成本与审计的最终依据。

治理控制台使用以下接口发布完整 schema v2 快照：`GET /api/model/console`、`PUT /api/model/console`、`POST /api/model/routes/preview`。更新必须携带 `expectedVersion`；API key 只返回配置状态，不返回原文。

### 4.3 Gateway 多维限流与 Token 对账

生产和开发配置在 `model.gateway.admission` 下启用固定窗口准入。每次模型 attempt 会原子预留请求数和 `input + reserved output` Token，Provider 返回真实 usage 后再 reconcile；usage 缺失或断流时保留预留值。Redis 不可用时默认拒绝新的模型 attempt，避免故障期间无上限放量。

```yaml
model:
  gateway:
    admission:
      enabled: ${LLM_ADMISSION_ENABLED:true}
      window-seconds: ${LLM_ADMISSION_WINDOW_SECONDS:60}
      reservation-ttl-seconds: ${LLM_ADMISSION_RESERVATION_TTL_SECONDS:300}
      user:
        max-requests: ${LLM_USER_MAX_REQUESTS:60}
        max-tokens: ${LLM_USER_MAX_TOKENS:200000}
      tenant:
        max-requests: ${LLM_TENANT_MAX_REQUESTS:0}
        max-tokens: ${LLM_TENANT_MAX_TOKENS:0}
      model:
        max-requests: ${LLM_MODEL_MAX_REQUESTS:600}
        max-tokens: ${LLM_MODEL_MAX_TOKENS:2000000}
      provider:
        max-requests: ${LLM_PROVIDER_MAX_REQUESTS:1000}
        max-tokens: ${LLM_PROVIDER_MAX_TOKENS:4000000}
```

`tenantId` 只有调用方明确放入 `ModelRouteContext` 时才参与租户桶和 trace；当前登录模型只提供 `userId`，不会错误地把用户配额累计到虚构租户。限流拒绝会写入 `model_call_trace.status=REJECTED` 和 `error_code=LIMIT_EXCEEDED:*` 或存储不可用原因，可从 `/api/model/attempts` 查询。

### 4.4 MCP Server 配置 (mcp/config.yaml)

```yaml
server:
  port: 11611
  env: "dev"
  auth_api_key: "set-a-long-random-internal-service-key" # 仅 /internal/mcp，生产环境通过 SERVER_AUTH_API_KEY 注入

search:
  provider: bocha    # 搜索提供商: bocha/google/serper/tavily
  api_key: ""        # 搜索API密钥

grpc:
  backend_target: "localhost:9090"  # 后端gRPC地址
```

公共 MCP `POST /mcp` 只暴露 `diarySearch` 和 `memorySearch`，必须携带用户开发者 Key（推荐 `Authorization: Bearer <developer-key>`）。Java 后端访问内部 MCP `POST /internal/mcp`，只暴露 `web_search`，必须携带 `X-MCP-Service-Key`。两种密钥不得复用；生产环境只通过 HTTPS 反向代理发布公共 `/mcp`，不要公开 Java gRPC `9090`。

---

## 5. Docker Compose 组成

| 容器 | 镜像 | 端口 | 说明 |
|:---|:---|:---|:---|
| yusi | yusi:latest | 20611 | Spring Boot 应用 |
| yusi-mysql | mysql:8.0 | 3306 | MySQL 数据库 |
| yusi-redis | redis:7 | 6379 | Redis 缓存 |
| yusi-mcp | yusi-mcp:latest | 11611 | MCP Server (可选) |

---

## 6. 运维命令

### 6.1 日志查看

```bash
# 查看应用日志
docker-compose logs -f yusi

# 查看 MySQL 日志
docker-compose logs -f yusi-mysql

# 查看 Redis 日志
docker-compose logs -f yusi-redis
```

### 6.2 重启服务

```bash
# 重启 Yusi 应用
docker-compose restart yusi

# 重启所有服务
docker-compose restart
```

### 6.3 数据持久化

```bash
# 备份 MySQL 数据
docker exec yusi-mysql mysqldump -uroot -p123456 yusi > backup.sql

# 恢复 MySQL 数据
docker exec -i yusi-mysql mysql -uroot -p123456 yusi < backup.sql
```

### 6.4 清理环境

```bash
# 停止并删除容器
docker-compose down

# 删除数据卷 (慎用!)
docker-compose down -v

# 删除网络
docker network rm my-network
```

---

## 7. 常见问题

### Q1: 启动失败，提示数据库连接错误

```bash
# 检查 MySQL 是否就绪
docker-compose ps

# 等待 MySQL 完全启动后重试
sleep 30
docker-compose restart yusi
```

### Q2: AI 模型调用失败

```bash
# 检查 API Key 配置
grep -E "MODEL_APIKEY|MODEL_BASEURL" yusi_prod.env

# 检查模型是否可用
curl -X POST ${CHAT_MODEL_BASEURL}/chat/completions \
  -H "Authorization: Bearer ${CHAT_MODEL_APIKEY}" \
  -H "Content-Type: application/json" \
  -d '{"model": "Qwen/Qwen2.5-72B-Instruct", "messages": [{"role": "user", "content": "hi"}]}'
```

### Q3: 内存不足

```bash
# 增加 Docker 内存限制至 4GB+
# macOS: Docker Desktop → Settings → Resources
# Linux: 编辑 /etc/docker/daemon.json
```

### Q4: MCP Server 无法连接

```bash
# 确认 MCP 已启用
grep MCP_ENABLED yusi_prod.env
# 应为 MCP_ENABLED=true

# 检查端口连通性
curl http://localhost:11611/health
```

---

## 8. 安全建议

1. **修改默认密码**：生产环境务必修改 MySQL 和 Redis 默认密码
2. **JWT 密钥**：使用 `openssl rand -base64 32` 生成强随机密钥
3. **加密密钥**：使用 `openssl rand -base64 32` 生成 AES-256 密钥
4. **RSA 密钥对**：使用 OpenSSL 生成并妥善保管私钥
5. **网络隔离**：生产环境使用内网而非公网暴露服务
6. **定期备份**：配置数据库自动备份策略
