# 工程治理优化记录

**日期：** 2026-08-02  
**范围：** 异步执行、任务事务边界、依赖版本、CI 校验和工程产物

## 已确认的现状

- 项目使用 Java 21，但没有启用虚拟线程；未发现 `spring.threads.virtual.enabled`、`newVirtualThreadPerTaskExecutor()` 或其他虚拟线程执行器配置。
- LangChain4j 核心当前为 `1.18.0`。社区 DashScope、Spring Boot 和 MCP 模块当前最新发布线为 `1.18.0-beta28`，其 POM 依赖核心 `1.18.0`，因此不能只把核心升级到 `1.18.1`。
- 线程池原先通过 `ThreadPoolTaskExecutor` 配置，但部分 `CompletableFuture` 使用 JDK 默认执行器，导致队列、上下文传递和关闭策略不统一。

## 本次决策

1. 使用共享 `ThreadPoolTaskExecutor` 承载显式异步任务，并通过 `TaskDecorator` 传递 MDC 和 `UserContext`。
2. 将线程池保活配置明确为秒，统一使用 `keep-alive-seconds`，并配置优雅停机等待。
3. Embedding 和 LifeGraph 任务使用独立 claim service：`FOR UPDATE SKIP LOCKED` 与标记处理中保留在短事务内，模型、Milvus 和图谱处理在事务外执行。
4. CI 在构建镜像前执行后端测试、前端 Vitest 和 TypeScript 类型检查。
5. 当前不启用虚拟线程。模型调用受供应商限流和队列背压约束，先保持有界线程池；后续如需评估虚拟线程，应单独建立隔离执行器并补充并发上限和指标。

## 后续观察项

- 为任务状态增加 `PROCESSING` 超时恢复和任务耗时/队列深度指标。
- 按 AI 调用、后台任务和低优先级维护任务拆分执行器，避免共享队列相互影响。
- 逐步拆分前端大文件，并统一 Axios 请求与流式 `fetch` 的认证、超时和错误处理。
