# Yusi - 灵魂叙事

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-blue" alt="Java">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.4.5-green" alt="Spring Boot">
  <img src="https://img.shields.io/badge/React-18-blue" alt="React">
  <img src="https://img.shields.io/badge/License-MIT-yellow" alt="License">
  <a href="https://deepwiki.com/Aseubel/yusi"><img src="https://deepwiki.com/badge.svg" alt="Ask DeepWiki"></a>
</p>

## 产品理念

**人不被行为标签所定义，记忆使人成型。**

我们坚信，真正的理解不是记住几个标签（如"温柔"、"果断"），而是明白他人在具体情境下会做出怎样的选择和行动。Yusi 通过情景叙事和记忆图谱，让每个人都能记录对自己重要的选择与时刻，在具体场景中真正理解彼此。

---

## 核心功能

### 1. 情景叙事 (The Situation Room)

- **具体情境中的真实选择**：支持 2-8 人创建情景室，在精心设计的情景中呈现真实的自己
- **AI 行为分析**：基于用户提交的行动与想法，AI 生成多维度的分析报告
- **超越标签的理解**：不是用"温柔"或"果断"来定义，而是看见他人具体情境中的行动

### 2. 记忆成型 (The Memory Journal)

- **记录重要选择**：提供绝对私密的日记本，记录生命中的重要时刻与选择
- **AES/GCM 加密存储**：日记内容采用透明加密，确保绝对隐私
- **RAG 增强对话**：基于向量数据库的检索增强生成，让 AI 拥有"记忆"
- **记忆图谱**：AI 从日记中提取关键实体与关系，呈现你的人生轨迹

### 3. 深度理解与连接

- **叙事广场**：匿名分享你的故事，看见他人的选择
- **精神共鸣**：基于对行为和记忆的深度分析，找到真正理解你的人
- **匿名对话**：匹配成功后开启限时匿名对话，保护双方隐私

### 4. 人生图谱 (Life Graph)

- **实体关系抽取**：AI 自动从日记中提取人物、地点、事件、情绪等实体
- **关系图谱构建**：基于实体间的共现和语义分析，自动建立关系边
- **多跳推理问答**：AI 可回答复杂问题，通过图谱遍历进行多跳推理
- **情感图谱**：追踪用户情绪随时间和事件的变化，识别情绪触发点

---

## 技术架构

### 后端技术栈

| 类别 | 技术 |
|------|------|
| 核心框架 | Spring Boot 3.4.5, Java 21 |
| ORM | Spring Data JPA, Hibernate |
| 数据库 | MySQL 8.x, Redis, Milvus/Zilliz |
| AI & LLM | LangChain4j, Qwen/DeepSeek API |
| 分布式中间件 | Redisson (分布式锁、限流) |
| 高性能事件处理 | LMAX Disruptor |
| 安全 | JWT, AES/GCM 加密 |
| 记忆管理 | LangChain4j MessageWindowChatMemory |
| 协议集成 | gRPC, MCP (Model Context Protocol) |

### 前端技术栈

| 类别 | 技术 |
|------|------|
| 框架 | React 18 + TypeScript |
| 构建工具 | Vite |
| UI 框架 | Tailwind CSS |
| 状态管理 | Zustand |
| 动画 | Framer Motion |
| HTTP 客户端 | Axios |

### 项目模块

```
yusi/
├── src/                    # Spring Boot 后端
│   ├── main/java/com/aseubel/yusi/
│   │   ├── common/         # 通用组件 (Response, 异常，工具类)
│   │   ├── config/         # 配置类 (Redis, AI, Security, Memory)
│   │   ├── controller/     # REST API 控制器
│   │   ├── service/        # 业务逻辑层 (AI, Diary, LifeGraph, Memory)
│   │   ├── repository/     # 数据访问层
│   │   ├── pojo/           # 实体类与 DTO
│   │   ├── grpc/           # gRPC 服务实现 (MCP 集成)
│   │   └── monitor/        # 监控模块
│   └── resources/          # 配置与模板
├── frontend/               # React 前端
│   ├── src/
│   │   ├── pages/          # 页面组件
│   │   ├── components/     # 通用组件
│   │   ├── lib/            # API 与工具
│   │   └── stores/         # 状态管理
│   └── public/             # 静态资源
├── mcp/                    # MCP 服务 (AI 工具集成)
│   ├── proto/              # Protocol Buffers 定义
│   └── internal/           # Go 服务实现
│       ├── grpc/           # gRPC 客户端
│       └── tools/          # MCP 工具实现
└── docs/                   # 文档 (PRD, SQL, API)
```

---

## 快速开始

### 前置要求

- **Java 21+**
- **Maven 3.9+**
- **Node.js 18+**
- **MySQL 8.x**
- **Redis 7.x**

### 1. 克隆项目

```bash
git clone https://github.com/Aseubel/yusi.git
cd yusi
```

### 2. 配置数据库

```sql
CREATE DATABASE yusi CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 3. 配置后端

创建 `src/main/resources/application-dev.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/yusi?useSSL=false&serverTimezone=UTC
    username: root
    password: your_password
  data:
    redis:
      host: localhost
      port: 6379
```

设置环境变量：

```bash
# AI 模型配置
export CHAT_MODEL_APIKEY="your-api-key"
export CHAT_MODEL_BASEURL="https://api.deepseek.com"
export CHAT_MODEL_NAME="deepseek-chat"
export EMBEDDING_MODEL_APIKEY="your-api-key"
export EMBEDDING_MODEL_BASEURL="https://api.siliconflow.cn/v1"
export EMBEDDING_MODEL_NAME="BAAI/bge-m3"

# 日记加密密钥 (32 字节 AES-256 Key，Base64 编码)
export YUSI_ENCRYPTION_KEY="your-32-byte-base64-key"

# MCP 服务配置（可选，用于 LLM 集成）
export MCP_GRPC_BACKEND_TARGET="localhost:9090"
export MCP_API_KEY="sk-ys-XXX"
```

### 4. 配置 MCP 服务（可选）

MCP (Model Context Protocol) 服务允许 LLM 访问用户的记忆和日记数据：

```bash
cd mcp
# 安装依赖
go mod download
# 生成 proto 文件（如修改过 proto 定义）
./generate_proto.bat  # Windows
./generate_proto.sh   # Linux/Mac
# 启动服务
go run server.go
```

MCP 服务默认端口：`11611`

**MCP 提供的工具**：
- `diarySearch`: 搜索日记内容
- `memorySearch`: 搜索记忆（包括日记、图谱、中期记忆和短期对话上下文）

### 5. 启动后端

```bash
./mvnw spring-boot:run
```

服务默认端口：`20611`

### 5. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端默认端口：`5173`

---

## Docker 部署

### 使用 Docker Compose

```bash
# 启动所有服务
docker-compose up -d
```

### 手动构建

```bash
# 构建后端
./mvnw clean package -DskipTests
docker build -t yusi-backend .

# 构建前端
cd frontend
npm run build
docker build -t yusi-frontend .
```

---

## API 文档

### 核心接口

| 模块 | 方法 | 路径 | 描述 |
|------|------|------|------|
| 用户 | POST | `/api/user/register` | 用户注册 |
| 用户 | POST | `/api/user/login` | 用户登录 |
| 日记 | GET | `/api/diary/list` | 获取日记列表 |
| 日记 | POST | `/api/diary` | 写日记 |
| 日记 | POST | `/api/ai/chat/stream` | AI 对话 (流式) |
| 记忆 | GET | `/api/memory/config` | 获取记忆配置信息 (Admin) |
| 记忆 | POST | `/api/memory/summarize` | 手动触发中期记忆总结 (Admin) |
| 情景室 | POST | `/api/room/create` | 创建情景房间 |
| 情景室 | POST | `/api/room/join` | 加入房间 |
| 情景室 | POST | `/api/room/submit` | 提交叙事 |
| 广场 | GET | `/api/plaza/feed` | 获取广场内容 |
| 广场 | POST | `/api/plaza/submit` | 发布内容 |
| 匹配 | POST | `/api/soul/match` | 获取匹配推荐 |
| 匿名聊天 | POST | `/api/soul/chat/send` | 发送消息 |
| 人生图谱 | GET | `/api/lifegraph/emotions` | 情感时间线 |
| 人生图谱 | GET | `/api/lifegraph/communities` | 社区洞察 |
| MCP | gRPC | `localhost:9090` | MCP 扩展服务 (memorySearch, diarySearch) |

---

## 记忆系统架构

Yusi 采用分层记忆架构，模拟人类的记忆机制：

### 记忆分层

| 层级 | 存储内容 | 保留时长 | 存储方式 | 触发机制 |
|------|---------|---------|---------|---------|
| **短期记忆** | 当前会话对话历史 | 会话结束 | Redis + LangChain4j | 自动维护，默认 50 条消息窗口 |
| **中期记忆** | 近期重要事件摘要 | 30 天 | MySQL + Milvus 向量检索 | 定时任务扫描，满足时间间隔 (1h) 和消息数量 (50 条) 时触发 AI 总结 |
| **长期记忆** | 人生图谱、关键记忆节点 | 永久 | MySQL + 图谱 | 从日记和中期记忆中抽取实体关系 |

### 中期记忆总结机制

系统每 5 分钟扫描一次，当满足以下条件时触发总结：
1. 用户最后一次对话时间超过 1 小时
2. 未总结的消息数量达到 50 条（可配置）

总结流程：
- 调用 AI 对消息窗口进行语义压缩
- 生成结构化的中期记忆存储
- 标记已总结的消息并记录时间

### MCP 记忆搜索

通过 MCP 协议提供统一的记忆搜索接口：

```bash
# MCP 工具调用示例
{
  "tool": "memorySearch",
  "arguments": {
    "query": "我最近做了什么重要的决定？",
    "maxResults": 10
  }
}
```

返回结果包含：
- **MID_TERM_MEMORY**: AI 总结的中期记忆（向量检索）
- **SHORT_TERM_CONTEXT**: 最近的对话上下文（按时间排序）

---

## 监控体系

### 接口请求监控

系统内置 `InterfaceMonitorAspect` 切面，自动拦截 Controller 层请求：

- **数据流**：请求 → AOP 拦截 → Redis `INCR` → 定时任务 (30min) → MySQL `UPSERT`
- **Redis Key**：`yusi:interface:usage:{yyyy-MM-dd}`

### 限流策略

使用 `@RateLimiter` 注解进行流量控制：

```java
@RateLimiter(key = "chat", time = 60, count = 10, limitType = LimitType.USER)
@PostMapping("/send")
public Result sendMessage(...) { ... }
```

- `LimitType.IP`：针对来源 IP 限流
- `LimitType.USER`：针对用户 ID 限流
- `LimitType.DEFAULT`：全局限流

---

## 配置管理

### 记忆系统配置

在 `application-dev.yml` 中配置记忆相关参数：

```yaml
yusi:
  memory:
    # 短期记忆上下文窗口大小（消息条数）
    context-window-size: 50
    # 中期记忆总结时间间隔（毫秒）
    mid-term-summary-interval: 3600000  # 1 小时
    # 中期记忆触发扫描 Cron 表达式
    mid-term-scan-cron: "0 */5 * * * ?"  # 每 5 分钟
```

### MCP 服务配置

在 `mcp/config.yaml` 中配置 MCP 服务：

```yaml
server:
  port: 11611
  env: "dev"
  
grpc:
  backend_target: "localhost:9090"  # Java 后端 gRPC 地址
  api_key: "sk-ys-XXX"              # 从控制台生成的 API Key

log:
  level: "debug"
```

---

## 安全特性

- **日记加密**：采用 AES/GCM 透明加密，密钥由用户掌控
- **JWT 认证**：无状态身份验证
- **隐私优先**：AI 分析脱敏处理，数据库 ACL 控制

---

## 贡献指南

欢迎提交 Issue 和 Pull Request！

---

## 许可证

MIT License - 查看 [LICENSE](LICENSE) 了解更多。

---

<p align="center">
  Made with ❤️ by <a href="https://github.com/Aseubel">Aseubel</a>
</p>
