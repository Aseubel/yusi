# Yusi

<p align="center">
  <strong>An open-source, privacy-first AI companion built around long-term memory.</strong>
</p>

<p align="center">
  <a href="README.md">中文</a> ·
  <a href="https://github.com/Aseubel/yusi/issues">Issues</a> ·
  <a href="docs/guides/installation.md">Installation guide</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white" alt="Java 21">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.4.5-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot 3.4.5">
  <img src="https://img.shields.io/badge/LangChain4j-1.18.0-111827" alt="LangChain4j 1.18.0">
  <img src="https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=20232A" alt="React 19">
  <img src="https://img.shields.io/badge/license-MIT-yellow.svg" alt="MIT License">
</p>

> Yusi is under active development and should be treated as a developer preview. APIs, configuration, and data models may change.

## What is Yusi?

Yusi is a privacy-first AI companion for turning journals, conversations, and life experiences into a continuously growing personal memory. It is designed around an AI loop of perception, memory, reasoning, and action rather than adding a chat box to a conventional application.

Its guiding idea is simple: **know your vibe, find your tribe.** Understanding concrete moments and choices is more meaningful than reducing a person to a set of labels.

## Features

| Capability | Description |
| --- | --- |
| Memory Journal | Capture meaningful moments, choices, and feelings with encrypted storage and rich text |
| Layered Memory | Combine short-term conversation context, intermediate memories, long-term summaries, and vector retrieval |
| RAG Chat | Retrieve relevant personal memories before generating grounded responses |
| Life Graph | Extract people, places, events, and emotions into an explorable relationship graph |
| Situation Room | Record choices in concrete scenarios and generate behavioral and emotional analysis |
| Soul Matching | Explore deeper resonance through behavior and narratives instead of profile labels |
| Model Control Plane | Route models by business scene with weights, priorities, health state, and failover |
| MCP Gateway | Expose memory tools through MCP; a Go gateway calls Java internal capabilities over gRPC |

## Architecture

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

The MCP process is a protocol gateway, not a separate memory backend. Memory queries, decryption, and scope authorization remain in the Java backend. Go handles protocol adaptation, tool registration, and request forwarding.

## Tech stack

- **Backend**: Java 21, Spring Boot 3.4.5, Spring Data JPA, MySQL, Redis, Milvus/Zilliz
- **AI**: LangChain4j 1.18.0, OpenAI-compatible APIs, DashScope, RAG, embeddings
- **Integration**: gRPC, Protocol Buffers, MCP (Model Context Protocol), WebSocket
- **Frontend**: React 19, TypeScript, Vite, Tailwind CSS, Radix UI, Zustand, Tiptap
- **MCP gateway**: Go, MCP Go SDK, Gin, gRPC
- **Security**: JWT, AES/GCM encrypted diary content, scoped MCP authorization

## Repository layout

```text
yusi/
├── src/main/java/com/aseubel/yusi/
│   ├── controller/       # HTTP / WebSocket endpoints
│   ├── service/          # Domain-oriented application capabilities
│   │   ├── ai/           # chat, embedding, prompt, rag, model, asr, etc.
│   │   ├── memory/       # Retrieval, summaries, and context assembly
│   │   └── cognition/    # Emotion and cognitive analysis
│   ├── repository/       # Persistence access
│   ├── pojo/             # Entities, DTOs, and domain data structures
│   ├── config/           # Spring, AI, data, and security configuration
│   └── grpc/             # Internal capability boundary for the MCP gateway
├── src/main/resources/   # Application configuration and templates
├── frontend/             # React web client
├── mcp/                  # Go MCP gateway and protobuf definitions
└── docs/                 # PRDs, designs, guides, plans, and engineering records
```

## Quick start

### Prerequisites

- Java 21+
- Maven 3.9+ (or the included Maven Wrapper)
- Node.js 18+
- pnpm 11.9+
- Go 1.25+
- MySQL 8+
- Redis 7+
- Milvus/Zilliz for vector retrieval (optional depending on configuration)

### 1. Clone and prepare the database

```bash
git clone https://github.com/Aseubel/yusi.git
cd yusi
```

```sql
CREATE DATABASE yusi CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. Configure the backend

Copy and adapt `src/main/resources/application-dev.yml` for your local environment. Configure MySQL, Redis, model providers, and the diary encryption key. Inject secrets through local untracked configuration or environment variables; never commit API keys.

```bash
export YUSI_ENCRYPTION_KEY="<base64-encoded-32-byte-key>"
export CHAT_MODEL_APIKEY="<chat-provider-key>"
export EMBEDDING_MODEL_APIKEY="<embedding-provider-key>"
```

### 3. Start the backend

```bash
./mvnw spring-boot:run
```

### 4. Start the frontend

```bash
cd frontend
pnpm install
pnpm run dev
```

### 5. Start the MCP gateway (optional)

Make sure the Java backend gRPC target and authorization settings are ready:

```bash
cd mcp
go mod download
go run ./cmd/server
```

The default MCP port is `11611`. Streamable HTTP (recommended) and legacy SSE are supported. See [`mcp/README.md`](mcp/README.md) for client configuration, scopes, and proto generation.

## Development verification

```bash
# Backend compilation without tests
./mvnw -DskipTests compile

# Frontend type checking and production build
cd frontend
pnpm run build

# MCP compilation check
cd ../mcp
go build ./...
```

## Documentation

- [Installation and local development](docs/guides/installation.md)
- [Product philosophy](docs/design/philosophy.md)
- [Backend design](docs/design/backend-design.md)
- [Model management and routing framework](docs/design/model-management-framework.md)
- [LangChain4j 1.18 architecture evolution record](docs/record/langchain4j-1.18-architecture-evolution.md)
- [Backend structure review](docs/record/backend-structure-review-2026-08-02.md)
- [PRD v4](docs/prd/prd_v4.md)

## License

Released under the [MIT License](LICENSE).
