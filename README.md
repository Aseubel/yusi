# Yusi - 灵魂叙事 (Soul Narrative)

Yusi 是一个深度社交后端服务，旨在通过 AI 分析用户的“叙事”和“情景行为”，实现基于深层性格与价值观的灵魂匹配。项目基于 Spring Boot 3.4.5 构建，集成了 LangChain4j 进行向量检索与大模型交互。

## 🌟 核心功能

### 1. 情景室 (The Situation Room)
- **多人实时协作**：支持 2-8 人创建房间，同步参与情景模拟。
- **AI 行为分析**：基于用户提交的行动与想法，AI 生成多维度的性格分析报告与合拍度矩阵。
- **实时状态流转**：使用 Redis 维护房间状态，支持 LMAX Disruptor 高性能事件处理（可选）。

### 2. AI 知己日记 (The Confidant Journal)
- **隐私保险库**：日记内容采用 AES/GCM 透明加密存储，确保绝对隐私。
- **RAG 增强对话**：基于向量数据库（Milvus/Zilliz）的检索增强生成，让 AI 拥有“记忆”，成为懂你的知己。
- **灵魂匹配与匿名畅聊**：
  - 基于 AI 推荐信的双向匹配机制。
  - **匿名聊天室**：匹配成功后开启限时/限次匿名对话，保护双方隐私，仅通过灵魂共鸣交流。

### 3. 系统可观测性与稳定性
- **分布式限流**：集成 Redisson RRateLimiter，支持 IP、用户、全局维度的精细化流量控制。
- **接口使用监控**：
  - 基于 Redis 的原子计数器，高性能记录接口调用频次。
  - 自动处理跨天数据，每 30 分钟异步同步至 MySQL 持久化。
  - 支持按用户、IP、接口名维度的统计分析。

---

## 🛠 技术栈

- **核心框架**：Spring Boot 3.4.5, Java 17
- **数据存储**：
  - MySQL 8.x (业务数据)
  - Redis (缓存、分布式锁、限流、计数器)
  - Milvus / Zilliz Cloud (向量数据)
- **AI & LLM**：LangChain4j, Qwen (通义千问) API
- **ORM & 数据库**：Spring Data JPA, Hibernate, HikariCP
- **中间件**：Redisson (分布式服务), Spring AOP (切面监控)
- **工具**：Lombok, Hutool, Jackson

---

## 🚀 快速开始

### 1. 环境准备
- **Java 17+**
- **Maven 3.9+**
- **MySQL 8.x**：创建数据库 `yusi`
- **Redis**：默认端口 `6379`

### 2. 数据库初始化
项目启动时会自动根据实体类更新表结构（`hibernate.ddl-auto: update` 或 `validate`）。
**接口监控表结构**：
```sql
CREATE TABLE IF NOT EXISTS `interface_daily_usage` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` VARCHAR(64) NOT NULL COMMENT 'User ID',
    `ip` VARCHAR(64) NOT NULL DEFAULT '' COMMENT 'Client IP',
    `interface_name` VARCHAR(128) NOT NULL COMMENT 'Interface/Method Name',
    `usage_date` DATE NOT NULL COMMENT 'Date of usage',
    `request_count` BIGINT NOT NULL DEFAULT 0 COMMENT 'Daily request count',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_user_ip_interface_date` (`user_id`, `ip`, `interface_name`, `usage_date`),
    INDEX `idx_date` (`usage_date`),
    INDEX `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User Interface Daily Usage Stats';
```

### 3. 配置说明
修改 `src/main/resources/application-dev.yml`：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/yusi?useSSL=false&serverTimezone=UTC
    username: <your_username>
    password: <your_password>
  data:
    redis:
      host: localhost
      port: 6379
```

设置环境变量（PowerShell 示例）：
```powershell
# AI 模型 API 密钥
$env:CHAT_MODEL_APIKEY = "sk-xxxxxxxxxxxx"
$env:CHAT_MODEL_BASEURL = "https://api.deepseek.com"
$env:CHAT_MODEL_NAME = "deepseek-chat"
$env:EMBEDDING_MODEL_APIKEY = "sk-xxxxxxxxxxxx"
$env:EMBEDDING_MODEL_BASEURL = "https://api.siliconflow.cn/v1"
$env:EMBEDDING_MODEL_NAME = "BAAI/bge-m3"

# ⚠️ 重要：服务端日记加密密钥（必需，Base64 的 32 字节 AES-256 Key）
$env:YUSI_ENCRYPTION_KEY = "BASE64_32_BYTES_AES_KEY"

# ⚠️ 重要：云端备份 RSA 密钥对（用于 CUSTOM+备份，不在网络传输明文用户密钥）
$env:YUSI_BACKUP_RSA_PUBLIC_KEY_SPKI_BASE64 = "BASE64_SPKI_PUBLIC_KEY"
$env:YUSI_BACKUP_RSA_PRIVATE_KEY_PKCS8_BASE64 = "BASE64_PKCS8_PRIVATE_KEY"

# 高德地图 API Key (后端地理服务代理)
$env:AMAP_API_KEY = "your_amap_api_key"
```

> **⚠️ 安全提醒**：服务端密钥必须使用强随机值并妥善保管；一旦丢失将无法解密既有数据。

### 4. 运行服务
```bash
# 编译
mvn clean package

# 运行
java -jar target/yusi-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```
服务默认端口：`20611`

---

## 📊 监控体系详解

### 接口请求监控
系统内置 `InterfaceMonitorAspect` 切面，自动拦截 Controller 层请求。
- **数据流**：请求 -> AOP 拦截 -> Redis `INCR` -> 定时任务 (30min) -> MySQL `UPSERT`
- **Redis Key 策略**：`yusi:interface:usage:{yyyy-MM-dd}`
- **数据落盘**：支持 `ON DUPLICATE KEY UPDATE`，确保高并发下的数据一致性。

### 限流策略
使用 `@RateLimiter` 注解进行流量控制：
```java
@RateLimiter(key = "chat", time = 60, count = 10, limitType = LimitType.USER)
@PostMapping("/send")
public Result sendMessage(...) { ... }
```
- **LimitType.IP**：针对来源 IP 限流
- **LimitType.USER**：针对用户 ID 限流
- **LimitType.DEFAULT**：全局限流

---

## 📂 目录结构

```
com.aseubel.yusi
├── common          # 通用组件 (Result, Exception, Utils)
├── config          # 配置类 (Redis, Web, Async, Security)
├── controller      # 控制器 (API 接口)
├── monitor         # 监控模块 (Aspect, Scheduled Task)
├── pojo            # 实体类 (Entity, DTO)
├── repository      # 数据访问层 (JPA Repository)
├── service         # 业务逻辑层
└── YusiApplication.java # 启动类
```

## 📝 常用 API

| 模块 | 方法 | 路径 | 描述 |
| :--- | :--- | :--- | :--- |
| **日记** | GET | `/api/diary/list` | 获取日记列表 |
| **日记** | POST | `/api/diary` | 写日记 (支持地理位置) |
| **日记** | GET | `/api/diary/footprints` | 获取足迹列表 (Epic 5) |
| **日记** | POST | `/api/ai/chat/stream` | 与 AI 知己对话 |
| **地理** | GET | `/api/geo/search` | POI 搜索代理 |
| **地理** | GET | `/api/geo/reverse` | 逆地理编码代理 |
| **地点** | GET | `/api/location/list` | 获取用户保存的地点 |
| **地点** | POST | `/api/location` | 添加常用/重要地点 |
| **情景室** | POST | `/api/room/create` | 创建情景房间 |
| **灵魂匹配** | POST | `/api/soul/match` | 获取匹配推荐 |
| **匿名聊天** | POST | `/api/soul/chat/send` | 发送匿名消息 |
