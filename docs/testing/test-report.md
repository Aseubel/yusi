# Yusi 测试报告

---

## 1. 测试概况

| 类别 | 数量 | 说明 |
|:---|:---|:---|
| 单元测试 | 3 | SoulPlazaServiceImpl 情感过滤逻辑 |
| 集成测试 | 1 | 情景室提交与审核流程 |
| 工具测试 | 1 | 压缩工具正则匹配 |
| 手动测试 | - | 核心流程验证 |

---

## 2. 主要测试用例

### 2.1 单元测试：广场 Feed 情感过滤

**测试类**：`SoulPlazaTest.java`

| 用例 | 输入 | 预期行为 |
|:---|:---|:---|
| 情感筛选 | emotion="Joy" | 调用 findByUserIdNotAndEmotionOrderByCreatedAtDesc |
| 全局展示 | emotion=null | 调用 findByUserIdNotOrderByCreatedAtDesc |
| 全局展示 | emotion="All" | 调用 findByUserIdNotOrderByCreatedAtDesc |

**结果**：✅ 3/3 通过

---

### 2.2 集成测试：情景室审核流程

**测试类**：`SituationScenarioTest.java`

```mermaid
flowchart LR
    A[提交情景<br/>status=0] --> B[审核通过<br/>status=4]
    B --> C[查询可见]
    C --> D[审核拒绝<br/>status=1]
    D --> E[查询不可见]
```

**测试步骤**：
1. 用户提交情景 → status=0 (待审核)
2. 管理员审核通过 → status=4 (人工通过)
3. 查询列表 → 包含该情景
4. 管理员审核拒绝 → status=1 (人工拒绝)
5. 查询列表 → 不包含该情景

**结果**：✅ 通过

---

### 2.3 手动测试：核心流程

| 流程 | 测试结果 |
|:---|:---|
| 用户注册/登录 | ✅ |
| 日记加密存储 (DEFAULT 模式) | ✅ |
| 日记解密读取 | ✅ |
| 日记编辑 | ✅ |
| 广场发布与 Feed 加载 | ✅ |
| 共鸣功能 | ✅ |
| 灵魂匹配推荐 | ✅ |
| 情景室创建/加入 | ✅ |
| AI 聊天 (WebSocket) | ✅ |
| 图片上传 (OSS) | ✅ |
| 主题切换 (明/暗) | ✅ |

---

## 3. 技术指标

### 3.1 运行性能

| 指标 | 实测值 | 说明 |
|:---|:---|:---|
| 应用启动时间 | ~8s | Spring Boot 3.4.5 + Java 21 |
| API 平均响应时间 | <200ms | 本地测试 (不含 AI 调用) |
| 数据库连接池 | HikariCP 5-10 连接 | 生产可调 |
| 并发处理能力 | 200 线程 | Tomcat 配置 |

### 3.2 安全性

| 措施 | 状态 |
|:---|:---|
| JWT Token 认证 | ✅ RS256 签名 |
| 密码 BCrypt 存储 | ✅ |
| 日记 AES-GCM 加密 | ✅ |
| 端到端加密 (CUSTOM 模式) | ✅ |
| RSA-OAEP 密钥备份 | ✅ |
| 敏感词过滤 | ✅ DFA 算法 |
| SQL 注入防护 | ✅ JPA 参数化查询 |

### 3.3 扩展性

| 维度 | 设计 |
|:---|:---|
| AI 模型 | LangChain4j 多模型动态路由 |
| 存储 | ShardingSphere 分库分表支持 |
| 缓存 | Redis + Redisson 分布式锁 |
| 向量检索 | Milvus 支持 |
| MCP 协议 | gRPC 扩展接入外部 AI |

### 3.4 部署

| 指标 | 状态 |
|:---|:---|
| Docker 容器化 | ✅ docker-compose 一键部署 |
| 前端构建 | ✅ Vite + PWA 支持 |
| 环境隔离 | ✅ Spring Profiles (dev/test/prod) |

### 3.5 可用性

| 维度 | 状态 |
|:---|:---|
| 限流 | ✅ Redis 分布式限流 + Guava 降级 |
| 熔断 | ✅ 三级熔断状态机 (AI 模型) |
| 监控 | ✅ 模型运行时状态上报 |
| 日志 | ✅ Logback 结构化日志 |
| 国际化 | ✅ i18next (zh/en) |

---

## 4. 已知问题

| 问题 | 影响 | 状态 |
|:---|:---|:---|
| 前端单元测试 | Vitest（当前覆盖流式聊天工具） | 持续补充 |
| 压力测试 | 未进行 | 建议生产前完成 |
| AI 模型性能基准 | 未建立 | 建议监控沉淀 |

---

## 5. LLM 网关路由治理验证（2026-08-05）

### 5.1 环境

- Java 21、Spring Boot 3.4.5、Maven Wrapper
- Node.js/pnpm、React 19、Vite
- 未使用真实 Provider API key；后端 focused suite 使用 mock Provider/Redis
- 前端开发服务：`http://localhost:5174`

### 5.2 自动化结果

| 命令 | 结果 |
|:---|:---|
| `./mvnw -Dtest=ModelConfigCenterTest,ChatModelProviderRegistryTest,ModelRoutePolicyMatcherTest,ModelRouterServiceTest,ModelInvocationErrorClassifierTest,ModelProxyFactoryTest,ModelTokenEstimatorTest,ModelUsageExtractorTest,ModelManagementControllerTest,AiControllerCancellationTest test` | ✅ 38 tests passed |
| `./mvnw -DskipTests compile` | ✅ BUILD SUCCESS |
| `pnpm --dir frontend test --run` | ✅ 4 files / 14 tests passed |
| `pnpm --dir frontend lint` | ✅ exit 0 |
| `pnpm --dir frontend build` | ✅ TypeScript/Vite/PWA build succeeded |
| `git diff --check` | ✅ no output |

新增回归覆盖：数据库 active 快照版本冲突、Redis 发布失败时本地版本不替换、失败审计追加顺序、全禁用 Chat 模型不得作为主 tier、密钥掩码不回传以及模型显示名称随治理请求保存。

### 5.3 静态与手动检查

- `application-dev.yml`、`application-prod.yml` 均包含 v2 `schema-version`、`default-route`、`tiers` 和 `routes`，ASR 仍保留 `SPEECH_TO_TEXT` 能力边界，YAML 未新增真实密钥。
- `frontend/src/pages/admin/ModelManagement.tsx` 及 `model-management/` 未命中 `rawConfig`、`Textarea` 或 `/model/config`；JSON 仅通过折叠面板导出当前 schema v2 快照。
- `src/main/java/.../controller` 与 `src/main/java/.../pojo/dto/model` 的 `apikey/apiKey` 扫描只命中 `apiKeyConfigured`，未暴露原始密钥字段。
- 已启动本地前端并打开 `/admin/models`；现有认证守卫将无管理员会话重定向至登录页，因此桌面/移动控制台点击、路由预览、保存冲突和实际快照刷新无法在当前环境完成。没有使用凭据绕过认证。

## 6. Gateway 预算预留与对账验证（2026-08-07）

### 6.1 自动化结果

| 命令 | 结果 |
|:---|:---|
| `./mvnw -Dtest=ChatModelProviderRegistryTest,ModelConfigCenterTest,ModelInvocationErrorClassifierTest,ModelProxyFactoryTest,ModelRoutePolicyMatcherTest,ModelRouterServiceTest,ModelTokenEstimatorTest,ModelUsageExtractorTest,ModelBudgetAdmissionTest,FailOverSelectionStrategyTest,RoundRobinSelectionStrategyTest,ModelManagementControllerTest,AiControllerCancellationTest test` | ✅ 48 tests passed |
| `./mvnw -DskipTests compile` | ✅ BUILD SUCCESS |
| `cmd.exe /c "C:\Users\YangZhiYao\.cache\codex-runtimes\codex-primary-runtime\dependencies\bin\fallback\pnpm.cmd test --run"` | ✅ 4 files / 14 tests passed |
| `cmd.exe /c "C:\Users\YangZhiYao\.cache\codex-runtimes\codex-primary-runtime\dependencies\bin\fallback\pnpm.cmd run build"` | ✅ TypeScript/Vite/PWA build succeeded |
| `git diff --check` | ✅ no output |

新增覆盖：Redis admission 的三维 request/token charge 及 reconcile、限流拒绝不调用 Provider 且不污染模型健康状态，以及同步与流式 attempt 的未知 usage 保守挂账。
