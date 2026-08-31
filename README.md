# Yusi

<p align="center">
  <strong>一个隐私优先、以长期记忆为核心的开源 AI companion。</strong>
</p>

<p align="center">
  <a href="README.en.md">English</a> ·
  <a href="https://github.com/Aseubel/yusi/issues">Issues</a> ·
  <a href="docs/guides/installation.md">安装指南</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white" alt="Java 21">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.4.5-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot 3.4.5">
  <img src="https://img.shields.io/badge/LangChain4j-1.18.0-111827" alt="LangChain4j 1.18.0">
  <img src="https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=20232A" alt="React 19">
  <img src="https://img.shields.io/badge/license-MIT-yellow.svg" alt="MIT License">
</p>

> 当前项目处于 active development / developer preview 阶段。接口、配置和数据模型仍可能演进，不建议直接用于生产关键数据。

## Yusi 是什么

Yusi（「予思」）把日记、对话和人生经历组织成可持续积累的个人记忆。它不是给传统应用附加一个聊天窗口，而是围绕 AI 的感知、记忆、推理与行动来构建一个能长期理解用户的 companion。

核心理念是：**认识自我，遇见同频。** 记录具体时刻和真实选择，理解会比标签更接近一个人的本来面貌。

## 核心能力

| 能力 | 说明 |
| --- | --- |
| Memory Journal | 记录重要时刻、选择和感受，支持加密存储与富文本内容 |
| Layered Memory | 结合短期对话、中期记忆、长期摘要和向量检索，形成可持续的上下文 |
| Memory Center | 透明化的记忆管理：半衰期软衰减替代硬过期，命中强化巩固记忆，双重门槛懒判定遗忘 |
| RAG Chat | 从个人记忆中检索相关内容，再生成有依据的对话回复 |
| Life Graph | 抽取人物、地点、事件和情绪，构建可探索的人生关系图谱与情绪时间线 |
| Situation Room | 在具体情境中记录选择，生成行为与情绪分析报告 |
| Soul Plaza & Matching | 发布心声卡片、基于情绪与共鸣信号互动；基于行为和叙事的深层理解探索精神共鸣，而非简单标签匹配 |
| Agent Framework | 后台 Agent 运行与主动关怀，工具调用具备幂等、重试、取消与全链路 trace |
| Model Control Plane | 按业务场景和故障状态统一管理模型路由、权重、预算准入与 failover |
| Admin Workbench | 运营后台：模型治理、用户管理、安全审计、场景审计与 Web 访问策略 |
| MCP Gateway | 通过标准 MCP 对外提供记忆工具；Go 网关经 gRPC 调用 Java 内部能力，并复用后端鉴权与数据边界 |

## 架构概览

```text
External MCP clients
        |
        v
Go MCP Gateway (HTTP / Streamable HTTP / SSE)
        | gRPC
        v
Java internal capabilities (memory, diary, life graph)
        |
Spring Boot API / WebSocket
        |
Domain services + AI capability layer
        |
MySQL · Redis · Milvus/Zilliz · Object Storage
```

MCP 层是面向外部模型和客户端的能力适配边界，不是一个独立的记忆后端。记忆查询、解密和 scope 鉴权仍由 Java 后端负责，Go 服务只负责协议适配、工具注册和请求转发。

## 技术栈

- **Backend**: Java 21, Spring Boot 3.4.5, Spring Data JPA, Flyway, MySQL, Redis, Milvus/Zilliz
- **AI**: LangChain4j 1.18.0, OpenAI-compatible APIs, DashScope, RAG, embeddings
- **Integration**: gRPC, Protocol Buffers, MCP (Model Context Protocol), WebSocket
- **Frontend**: React 19, TypeScript, Vite, Tailwind CSS, Radix UI, Zustand, Tiptap, i18next, PWA
- **MCP gateway**: Go, MCP Go SDK, Gin, gRPC
- **Security**: JWT, AES/GCM encrypted diary content, scoped MCP authorization
- **Observability**: Micrometer/Prometheus 指标、调用链 trace、告警评估、限流准入与备份恢复脚本

## 项目结构

```text
yusi/
├── src/main/java/com/aseubel/yusi/
│   ├── controller/       # HTTP / WebSocket 接口
│   ├── service/          # 按领域组织的业务能力
│   │   ├── agent/        # agent 成长与主动服务
│   │   ├── ai/           # chat, embedding, prompt, rag, model 路由, asr 等
│   │   ├── memory/       # 记忆检索、摘要、半衰期衰减与上下文
│   │   ├── cognition/    # 情绪与认知分析
│   │   ├── lifegraph/    # 人生图谱构建、查询与洞察
│   │   ├── match/ plaza/ room/  # 同频匹配、灵魂广场与情境房间
│   │   └── runtime/      # agent 运行时：锁、trace、幂等与取消
│   ├── repository/       # 持久化访问
│   ├── pojo/             # Entity、DTO 与领域数据结构
│   ├── config/           # Spring、AI、数据和安全配置
│   ├── observability/    # 指标、告警与 trace 支持
│   └── grpc/             # 对外部 MCP 网关开放的内部能力边界
├── src/main/resources/   # application 配置、Flyway 迁移与模板
├── frontend/             # React web client
├── mcp/                  # Go MCP gateway 与 protobuf
├── ops/                  # 备份与恢复脚本
└── docs/                 # PRD、设计、指南、计划与工程记录
```

## 快速开始

### 环境要求

- Java 21+
- Maven 3.9+（或仓库内 Maven Wrapper）
- Node.js 18+
- pnpm 11.9+
- Go 1.25+
- MySQL 8+
- Redis 7+
- Milvus/Zilliz（向量检索功能需要，可按配置选择）

### 1. 获取代码与基础设施

```bash
git clone https://github.com/Aseubel/yusi.git
cd yusi
```

创建数据库：

```sql
CREATE DATABASE yusi CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. 配置后端

复制并按本地环境调整 `src/main/resources/application-dev.yml`，至少配置 MySQL、Redis、模型 API 和日记加密密钥。API key 只应通过本地未提交配置或环境变量注入，禁止写入 Git。

```bash
export YUSI_ENCRYPTION_KEY="<base64-encoded-32-byte-key>"
export CHAT_MODEL_APIKEY="<chat-provider-key>"
export EMBEDDING_MODEL_APIKEY="<embedding-provider-key>"
```

### 3. 启动后端

```bash
./mvnw spring-boot:run
```

### 4. 启动前端

```bash
cd frontend
pnpm install
pnpm run dev
```

### 5. 启动 MCP 网关（可选）

先确保 Java 后端的 gRPC 地址和鉴权配置已就绪：

```bash
cd mcp
go mod download
go run ./cmd/server
```

默认 MCP 端口为 `11611`。支持 Streamable HTTP（推荐）和传统 SSE；具体客户端配置、scope 与 proto 生成方式见 [`mcp/README.md`](mcp/README.md)。

## 开发验证

```bash
# 后端编译（不执行测试）
./mvnw -DskipTests compile

# 前端类型检查与生产构建
cd frontend
pnpm run build

# MCP 编译检查
cd ../mcp
go build ./...
```

## 文档导航

- [安装与本地开发](docs/guides/installation.md)
- [产品理念与开发哲学](docs/design/philosophy.md)
- [后端设计](docs/design/backend-design.md)
- [模型管理与路由框架](docs/design/model-management-framework.md)
- [记忆系统优化方案](docs/design/memory_system_optimization_proposal.md)
- [LangChain4j 1.18 架构演进记录](docs/record/langchain4j-1.18-architecture-evolution.md)
- [后端目录结构审计记录](docs/record/backend-structure-review-2026-08-02.md)
- [PRD v4](docs/prd/prd_v4.md)

## 许可证

本项目基于 [MIT License](LICENSE) 开源。
