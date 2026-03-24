# Yusi 后端详细设计文档

---

## 1. 系统架构概述

### 1.1 技术栈

| 层次 | 技术选型 | 说明 |
|:---|:---|:---|
| 基础框架 | Spring Boot 3.4.5 + Java 21 | 现代化 Java 栈 |
| 关系存储 | MySQL + ShardingSphere 5.5.0 | 分库分表，支持高并发写入 |
| 缓存层 | Redis + Redisson 3.26.0 | 分布式限流、热点数据缓存 |
| 向量存储 | Milvus | 语义检索、记忆向量存储 |
| AI 框架 | LangChain4j 1.12.2 | 统一 AI 模型集成 |
| 事件驱动 | Disruptor 4.0.0 | 高性能异步事件处理 |
| 通信协议 | gRPC + MCP | 跨语言服务调用、模型上下文协议 |

### 1.2 核心架构图

```mermaid
flowchart TB
    subgraph Frontend["前端层"]
        Web["Web App / PWA"]
        Mobile["移动端"]
    end

    subgraph API["API 网关层"]
        Nginx["Nginx 负载均衡"]
    end

    subgraph Backend["Spring Boot 应用"]
        subgraph Controller["Controller 层"]
            UserCtrl["用户模块"]
            DiaryCtrl["日记模块"]
            LifeGraphCtrl["人生图谱模块"]
            SoulPlazaCtrl["灵魂广场模块"]
            SituationCtrl["情景室模块"]
            AIMemoryCtrl["AI 记忆模块"]
        end

        subgraph Service["Service 层"]
            ModelRouter["ModelRouterService (模型路由)"]
            AiLock["AiLockService (分布式锁)"]
            MemoryCompression["MemoryCompressionService (记忆压缩)"]
            LifeGraphBuild["LifeGraphBuildService (图谱构建)"]
        end

        subgraph Infrastructure["基础设施层"]
            LangChain4j["LangChain4j (AI 调用)"]
            Disruptor["Disruptor (异步事件)"]
            ShardingSphere["ShardingSphere (分库分表)"]
        end
    end

    subgraph MCPServer["MCP Server (Go)"]
        MCPHandler["MCPHandler (Streamable HTTP + SSE)"]
        SearchTool["web_search (网络搜索)"]
        DiarySearchTool["diarySearch (日记检索)"]
        MemorySearchTool["memorySearch (记忆漫游)"]
    end

    subgraph Storage["存储层"]
        MySQL["MySQL (关系数据)"]
        Milvus["Milvus (向量数据)"]
        Redis["Redis (缓存/锁)"]
    end

    Web --> Nginx
    Mobile --> Nginx
    Nginx --> Backend
    Backend --> MySQL
    Backend --> Milvus
    Backend --> Redis
    MCPServer <-->|gRPC| Backend
    MCPServer <-->|Streamable HTTP + SSE| Web
```

### 1.3 MCP Server 定位

MCP Server 是后端的扩展服务，用于对外部 AI 提供系统能力，实现 AI 记忆的"数字漫游"。

```mermaid
flowchart LR
    subgraph ExternalAI["外部 AI (ChatGPT, Claude 等)"]
        LLM["LLM 大语言模型"]
    end

    subgraph MCPServer["MCP Server (Go)"]
        Protocol["MCP Protocol (JSON-RPC 2.0)"]
        Tools["Tools"]
        Search["web_search"]
        DiarySearch["diarySearch"]
        MemorySearch["memorySearch"]
    end

    subgraph YusiBackend["Yusi 后端 (Java)"]
        gRPC["gRPC Client"]
        DiaryService["DiaryService"]
        MemoryService["MemoryService"]
    end

    subgraph Storage["存储"]
        MySQL["MySQL"]
        Milvus["Milvus"]
    end

    LLM -->|tools/call| Protocol
    Protocol --> Tools
    Tools --> Search
    Tools --> DiarySearch
    Tools --> MemorySearch
    DiarySearch -->|gRPC| gRPC
    MemorySearch -->|gRPC| gRPC
    gRPC --> DiaryService
    gRPC --> MemoryService
    DiaryService --> MySQL
    MemoryService --> Milvus
    MemoryService --> MySQL
```

---

## 2. 数据库设计

### 2.1 ER 关系概览

```mermaid
erDiagram
    USER ||--o{ DIARY : writes
    USER ||--o{ SOUL_CARD : creates
    USER ||--o{ SOUL_MATCH : matches
    USER ||--o{ SITUATION_ROOM : participates

    DIARY ||--o{ LIFE_GRAPH_ENTITY : extracts
    DIARY ||--o{ EMBEDDING_TASK : vectors
    DIARY ||--o{ LIFE_GRAPH_MENTION : mentions

    LIFE_GRAPH_ENTITY ||--o{ LIFE_GRAPH_ENTITY_ALIAS : aliases
    LIFE_GRAPH_ENTITY ||--o{ LIFE_GRAPH_RELATION : relates
    LIFE_GRAPH_ENTITY ||--o{ LIFE_GRAPH_MENTION : evidences

    SOUL_CARD ||--o{ SOUL_RESONANCE : resonated_by
    SOUL_MATCH ||--o{ SOUL_MESSAGE : messages

    USER ||--o| USER_PERSONA : has
    USER ||--o{ USER_LOCATION : locates

    CHAT_MEMORY_MESSAGE ||--o{ MID_TERM_MEMORY : summarized_to
```

### 2.2 核心表结构

#### 2.2.1 用户与日记

| 表名 | 字段 | 类型 | 说明 |
|:---|:---|:---|:---|
| **user** | key_mode | VARCHAR(255) | DEFAULT(服务端密钥) / CUSTOM(用户自定义密钥) |
| | encrypted_backup_key | VARCHAR(1024) | 云端加密备份密钥 (RSA-OAEP) |
| **diary** | content | TEXT | 加密内容 (AES-GCM) |
| | client_encrypted | TINYINT(1) | true 时跳过服务端解密 |
| | images | TEXT(JSON) | OSS 图片 Key 列表 |

> **设计要点**：`content` 字段存储加密密文，client_encrypted=true 时保证端到端加密。

#### 2.2.2 人生图谱 (GraphRAG)

| 表名 | 设计要点 |
|:---|:---|
| **life_graph_relation** | 存储时强制 source_id < target_id，查询时 UNION 展开双向 |
| **life_graph_entity_alias** | alias_norm 归一化存储，实现别名消歧 |

```mermaid
erDiagram
    LIFE_GRAPH_ENTITY {
        bigint id PK
        varchar user_id
        varchar type "Person/Event/Place/Emotion/Topic/Item/User"
        varchar name_norm "归一化名称(去重用)"
        varchar display_name
        int mention_count
        json props "扩展属性"
    }

    LIFE_GRAPH_ENTITY_ALIAS {
        bigint id PK
        varchar user_id
        bigint entity_id FK
        varchar alias_norm UK
        varchar alias_display
        decimal confidence "0-1"
    }

    LIFE_GRAPH_RELATION {
        bigint id PK
        varchar user_id
        bigint source_id "较小ID"
        bigint target_id "较大ID"
        varchar type "关系类型"
        decimal confidence
        int weight "共现次数"
        varchar evidence_diary_id
    }

    LIFE_GRAPH_MENTION {
        bigint id PK
        varchar user_id
        bigint entity_id FK
        varchar diary_id FK
        date entry_date
        varchar snippet "证据片段"
    }

    LIFE_GRAPH_ENTITY ||--o{ LIFE_GRAPH_ENTITY_ALIAS : has
    LIFE_GRAPH_ENTITY ||--o{ LIFE_GRAPH_MENTION : mentioned_in
    LIFE_GRAPH_ENTITY |o--o| LIFE_GRAPH_RELATION : relates
```

#### 2.2.3 AI 记忆

```mermaid
erDiagram
    CHAT_MEMORY_MESSAGE {
        bigint id PK
        varchar memory_id "通常为userId"
        varchar role "USER/AI/SYSTEM"
        text content
        tinyint is_summarized
        datetime summarized_at
    }

    MID_TERM_MEMORY {
        bigint id PK
        varchar user_id
        text summary "LLM提炼的摘要"
        double importance "遗忘曲线权重"
    }

    CHAT_MEMORY_MESSAGE ||--o{ MID_TERM_MEMORY : summarized_to
```

### 2.3 违背范式的设计说明

| 设计 | 违背范式 | 理由 |
|:---|:---|:---|
| diary.images 存储 JSON 数组 | 1NF (原子性) | 图片列表通常 1-9 张，JSON 数组查询虽有局限但可接受；拆分会导致 JOIN 性能下降 |
| situation_room.members 存储 JSON | 1NF | 成员数量 ≤8，JSON 存储避免关联表开销 |
| life_graph_entity.props 存储 JSON | 1NF | 不同类型实体属性差异大，动态 JSON 比 EAV 模式更灵活 |

---

## 3. 关键算法与技术

### 3.1 GraphRAG 实体关系抽取

```mermaid
flowchart TD
    A["日记内容"] --> B["LLM 实体关系抽取"]
    B --> C{"解析结果"}
    C -->|失败| Z[跳过]
    C -->|成功| D["删除旧关联"]
    D --> E["确保用户实体存在"]
    E --> F["实体消歧与Upsert"]
    F --> G["关系构建与Upsert"]
    G --> H["证据关联记录"]
```

**别名归一化规则**：转小写 + 去空格，如"张三" → "zhangsan"

### 3.2 中期记忆压缩 (双轨触发 + 双阈值)

```mermaid
flowchart TB
    subgraph Triggers["触发机制"]
        Event["事件驱动 MessageSavedEvent"]
        Scheduled["定时兜底 每30分钟"]
    end

    subgraph Check["双阈值检查"]
        Condition1{"未总结消息数 >= contextWindowSize×2"}
        Condition2{"未总结消息数 >= contextWindowSize 且最后消息 > 冷却期"}
    end

    Event --> Check
    Scheduled --> Check
    Condition1 -->|硬上限| Compress["立即压缩"]
    Condition2 -->|冷却期| Compress
```

### 3.3 灵魂匹配 Feed 算法

**排序公式**：`FinalScore = (热度分数 + 时间分数) × 情感亲和权重`

| 因子 | 计算方式 |
|:---|:---|
| 热度分数 | log(1 + resonanceCount) × 10 |
| 时间分数 | 100 × e^(-hoursAgo / 72)，72小时半衰期 |
| 情感亲和权重 | 用户历史共鸣过该情感 ? 1.5 : 1.0 |

### 3.4 MCP Server 工具调用流程

```mermaid
sequenceDiagram
    participant LLM as 外部 AI
    participant MCP as MCP Server
    participant gRPC as gRPC Client
    participant Backend as Yusi 后端
    participant DB as MySQL/Milvus

    LLM->>MCP: tools/call memorySearch
    MCP->>gRPC: SearchMemory(apiKey, query, maxResults)
    gRPC->>Backend: SearchMemory RPC
    Backend->>Milvus: 向量相似度检索
    Backend->>DB: 查询中期记忆
    DB-->>Backend: 记忆数据
    Milvus-->>Backend: 向量结果
    Backend-->>gRPC: SearchMemoryResponse
    gRPC-->>MCP: 搜索结果
    MCP-->>LLM: tools/call result
```

---

## 4. 核心模块设计

### 4.1 日记加密流程

```mermaid
flowchart TD
    subgraph DEFAULT["DEFAULT 模式 (服务端密钥)"]
        A1["输入明文"] --> A2["AES-GCM 加密"]
        A2 --> A3["存储密文"]
    end

    subgraph CUSTOM["CUSTOM 模式 (用户密钥)"]
        B1["输入明文"] --> B2["使用用户密钥加密"]
        B2 --> B3["存储密文 client_encrypted=true"]
    end

    subgraph Read["读取流程"]
        C1{"client_encrypted?"}
        C1 -->|false| C2["服务端 AES 解密"]
        C1 -->|true| C3["跳过解密 (端到端)"]
    end
```

**CUSTOM 模式密钥恢复流程**：

1. 用户设置密钥时，生成随机 AES-256 密钥
2. 使用服务端 RSA 公钥加密，存入 encrypted_backup_key
3. 用户忘记密钥时，通过云端备份 + 服务端 RSA 私钥解密恢复

### 4.2 情景室状态机

```mermaid
stateDiagram-v2
    [*] --> WAITING: 创建房间
    WAITING --> IN_PROGRESS: 满2人+房主开始
    WAITING --> CANCELLED: 房主取消
    IN_PROGRESS --> COMPLETED: 全员提交
    IN_PROGRESS --> CANCELLED: 投票解散
    COMPLETED --> [*]
    CANCELLED --> [*]
```

### 4.3 MCP Server 扩展工具

| 工具名称 | 功能 | 数据来源 |
|:---|:---|:---|
| web_search | 互联网实时信息搜索 | Bocha/Google/Serper/Tavily |
| diarySearch | 用户日记全文检索与解密 | MySQL |
| memorySearch | 综合记忆检索 (中期+短期+图谱) | MySQL + Milvus |

---

## 5. API 设计要点

### 5.1 统一响应格式

```java
public class Response<T> {
    private int code;       // 0=成功，非0=错误码
    private String message; // 错误描述
    private T data;        // 业务数据
}
```

### 5.2 缓存设计

| 缓存 Key 模式 | TTL | 说明 |
|:---|:---|:---|
| diary:detail:{diaryId} | 1h | 压缩存储 |
| diary:list:{userId}:{page}:{size} | 5min | 压缩存储 |
| plaza:feed:{userId}:{page}:{size}:{emotion} | 60s | 热门广场 Feed |
| @UpdateCache | 变更时删除 | 写操作自动失效 |

---

## 6. 安全设计

| 安全措施 | 实现方式 |
|:---|:---|
| 认证 | JWT Token (RS256 签名) |
| 密码存储 | BCrypt |
| 日记加密 | AES-GCM-256 |
| 云端密钥备份 | RSA-OAEP (服务端公钥加密) |
| 敏感词过滤 | DFA 算法 (sensitive-word 库) |
| SQL 注入 | JPA Parameterized Query |

---

## 7. 部署架构

```mermaid
flowchart TB
    subgraph Clients["客户端"]
        WebApp["Web App"]
        MobileApp["移动端 PWA"]
    end

    subgraph Infra["基础设施"]
        Nginx["Nginx (反向代理+负载均衡)"]
    end

    subgraph AppCluster["应用集群"]
        Yusi1["yusi-app-1 (Spring Boot)"]
        Yusi2["yusi-app-2 (Spring Boot)"]
        MCPServer["yusi-mcp (Go MCP Server)"]
    end

    subgraph Storage["存储层"]
        MySQL["MySQL (分库分表)"]
        Redis["Redis Cluster"]
        Milvus["Milvus (向量库)"]
    end

    WebApp --> Nginx
    MobileApp --> Nginx
    Nginx --> Yusi1
    Nginx --> Yusi2
    Yusi1 --> MySQL
    Yusi2 --> MySQL
    Yusi1 --> Redis
    Yusi2 --> Redis
    Yusi1 --> Milvus
    Yusi2 --> Milvus
    MCPServer --> Yusi1
    MCPServer --> Yusi2
    WebApp --> MCPServer
```

---

## 8. AI 模型治理框架

### 8.1 架构概览

```mermaid
flowchart TB
    subgraph Admin["管理后台"]
        ConfigUI["配置管理 UI"]
        Dashboard["监控大盘"]
        StrategySwitch["策略切换"]
    end

    subgraph Governance["治理核心"]
        ConfigCenter["ModelConfigCenter (配置中心)"]
        StateCenter["ModelStateCenter (状态中心)"]
        InstanceRegistry["ModelInstanceRegistry (实例注册)"]
        StrategyManager["GroupStrategyManager (策略管理)"]
    end

    subgraph Routing["智能路由层"]
        Router["ModelRouterService"]
        ProxyFactory["ModelProxyFactory"]
        RoutingHandler["RoutingInvocationHandler"]
    end

    subgraph Strategies["路由策略"]
        RoundRobin["ROUND_ROBIN 轮询"]
        WeightedRandom["WEIGHTED_RANDOM 加权随机"]
        LeastLatency["LEAST_LATENCY 最小延迟"]
        FailOver["FAIL_OVER 故障转移"]
    end

    subgraph Runtime["运行时"]
        ModelInstance1["ModelInstance 1"]
        ModelInstance2["ModelInstance 2"]
        ModelInstanceN["ModelInstance N"]
    end

    Admin -->|HTTP/Admin API| ConfigCenter
    Admin -->|HTTP/Admin API| Dashboard
    Admin -->|HTTP/Admin API| StateCenter
    Admin -->|HTTP/Admin API| StrategyManager

    ConfigCenter -->|发布配置| InstanceRegistry
    ConfigCenter -->|发布配置| Router
    StateCenter -->|状态订阅| Router
    StrategyManager -->|策略查询| Router

    Router --> ProxyFactory
    ProxyFactory --> RoutingHandler
    RoutingHandler --> Strategies
    Strategies --> Router

    Router -->|选择实例| ModelInstance1
    Router -->|选择实例| ModelInstance2
    Router -->|选择实例| ModelInstanceN
```

### 8.2 核心组件

| 组件 | 类 | 职责 |
|:---|:---|:---|
| 配置中心 | ModelConfigCenter | 配置热加载与 Redis 广播发布 |
| 实例注册表 | ModelInstanceRegistry | 模型实例动态创建，配置变更自动 reload |
| 状态中心 | ModelStateCenter | 熔断状态管理 (UP/HALF_OPEN/DOWN) |
| 策略管理器 | GroupStrategyManager | 分组策略 Redis 订阅 + 本地缓存 |
| 路由服务 | ModelRouterService | 根据 language/scene 解析分组 |
| 代理工厂 | ModelProxyFactory | 动态代理，集成熔断、重试 |

### 8.3 熔断机制 (三级状态机)

```mermaid
stateDiagram-v2
    [*] --> UP: 初始化
    UP --> DOWN: 连续失败 >= 阈值
    DOWN --> HALF_OPEN: 探测间隔到期
    HALF_OPEN --> UP: 连续成功 >= 恢复阈值
    HALF_OPEN --> DOWN: 探测失败
    DOWN --> [*]
    UP --> [*]
    HALF_OPEN --> [*]
```

| 状态 | 允许请求 | 健康评分 | 说明 |
|:---|:---|:---|:---|
| UP | 正常 | 1 - errorRate | 正常运行 |
| HALF_OPEN | 探测比例放行 | 0.5 | 断路器半开，尝试恢复 |
| DOWN | 拒绝 | ≤0.2 | 熔断中，阻止请求 |

### 8.4 路由策略

| 策略 | 类 | 说明 |
|:---|:---|:---|
| ROUND_ROBIN | RoundRobinSelectionStrategy | 轮询所有可用实例 |
| WEIGHTED_RANDOM | WeightedRandomSelectionStrategy | 按权重加权随机 |
| LEAST_LATENCY | LeastLatencySelectionStrategy | 选择平均延迟最低的实例 |
| FAIL_OVER | FailOverSelectionStrategy | 优先级排序 + 熔断过滤 |

### 8.5 热重载流程

```mermaid
sequenceDiagram
    participant Admin as 管理员
    participant API as ModelManagementController
    participant Config as ModelConfigCenter
    participant Redis as Redis
    participant Registry as ModelInstanceRegistry

    Admin->>API: PUT /api/model/config
    API->>Config: updateFromAdmin(request)
    Config->>Config: validate() 校验配置
    Config->>Config: apply(merged, publish=true)
    Config->>Redis: SET runtimeConfigKey
    Config->>Redis: PUBLISH configChannel
    Config->>Config: publishEvent(ModelConfigUpdatedEvent)
    Config->>Registry: onModelConfigUpdated()
    Registry->>Registry: reload(config)
    Note over Registry: 销毁旧实例，创建新实例
    API-->>Admin: 200 OK
```

### 8.6 监控指标

| 指标 | 计算方式 | 用途 |
|:---|:---|:---|
| healthScore | 1 - errorRate | 综合健康度，熔断阈值参考 |
| qps | totalRequests / 时间窗口 | 实例负载评估 |
| avgLatencyMs | 滑动平均 | 延迟敏感路由依据 |
| consecutiveFailures | 连续失败计数 | 快速触发熔断 |
| phase | UP/HALF_OPEN/DOWN | 熔断状态 |

### 8.7 后台管理 API

| 接口 | 方法 | 说明 |
|:---|:---|:---|
| /api/model/config | GET | 获取模型配置 (API Key 脱敏) |
| /api/model/config | PUT | 更新模型配置 (热生效) |
| /api/model/states | GET | 获取所有实例运行时状态 |
| /api/model/groups/{group}/strategy | GET | 获取分组路由策略 |
| /api/model/groups/strategy/switch | POST | 切换分组路由策略 |

---

## 9. 关键技术亮点

| 亮点 | 说明 |
|:---|:---|
| GraphRAG 实践 | 基于 MySQL 实现图存储 + LLM 实体抽取 + 归一化消歧，绕过专用图数据库依赖 |
| 双轨记忆压缩 | 事件驱动 + 定时任务双保险，确保 AI 对话历史不丢失 |
| 端到端加密 | CUSTOM 模式下日记内容全程加密，服务端无法解密 |
| AI 模型治理 | 多策略智能路由 + 三级熔断 + 配置热更新 + 监控大盘 |
| Redis 降级限流 | 分布式限流在 Redis 故障时自动降级为单机限流 |
| MCP 数字漫游 | 通过 MCP 协议扩展，外部 AI 可直接查询用户日记与记忆，实现"数字分身" |

---

## 附录：MCP Server 详细设计

### A.1 核心组件

| 组件 | 文件 | 职责 |
|:---|:---|:---|
| MCPServer | server.go | 工具注册表、生命周期管理 |
| MCPHandler | handler/mcp.go | HTTP/SSE 协议处理、JSON-RPC 路由 |
| SearchDiaryTool | tools/extension_tools.go | 日记检索工具 |
| SearchMemoryTool | tools/extension_tools.go | 综合记忆检索工具 |
| gRPC Client | internal/grpc/client.go | 与后端 Java 服务通信 |

### A.2 通信协议

```mermaid
sequenceDiagram
    participant Client as AI Client
    participant MCP as MCP Server
    participant Backend as Yusi gRPC

    Client->>MCP: POST /mcp {"jsonrpc": "2.0", "method": "tools/call"}
    Note over MCP: 解析 JSON-RPC 请求
    MCP->>Backend: gRPC SearchDiary/SearchMemory
    Backend-->>MCP: gRPC Response
    MCP->>MCP: 格式化 MCP CallToolResult
    MCP-->>Client: SSE stream data: {...}
```

### A.3 工具定义 (memorySearch)

```json
{
  "name": "memorySearch",
  "description": "搜索用户的记忆信息，包括日记、人生图谱、中期记忆和短期记忆上下文",
  "inputSchema": {
    "type": "object",
    "properties": {
      "query": {
        "type": "string",
        "description": "搜索关键词或问题"
      },
      "maxResults": {
        "type": "integer",
        "description": "最大返回结果数量（默认 10）"
      }
    },
    "required": ["query"]
  }
}
```
