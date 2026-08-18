# Yusi 测试日志噪音清理设计

- 状态：待用户评审确认
- 日期：2026-08-18
- 所属范围：独立测试基础设施卫生切片
- 前置路线图：[Yusi Agent 产品与工程演进计划](../plans/2026-08-04-yusi-agent-product-roadmap.md)
- 相关约束：[Yusi Agent 产品事件与评测基线](2026-08-04-yusi-agent-product-event-contract.md)

## 1. 目标与边界

本切片只清理 Maven/JUnit 测试执行时的无效 stdout 和已盘点的预期失败日志噪音，目标是让
`.\mvnw.cmd -q test` 的输出能够集中表达真正的测试失败。实现只允许修改：

- `src/test/java` 中的测试代码；
- `src/test/resources` 中的测试 profile 日志配置；
- 本设计和对应实施计划。

本切片不改变任何生产行为、业务断言、评测契约、报告 writer、fixture 内容或 CI 归档方式，
不启动应用服务、数据库服务或外部依赖。完成后必须独立提交并停下来等待验收。

这不是 Phase 5 的生产敏感日志治理。Phase 5 要处理的是生产代码中的 query/正文日志和全量
敏感日志自检；本切片只让测试 profile 隐藏已知的测试失败分支 WARN，同时保留 ERROR。

## 2. 勘察结论

### 2.1 测试源码 stdout

当前 `src/test/java/com/aseubel/yusi/YusiApplicationTests.java` 有两类执行中的输出：

| 测试方法 | 当前输出 | 判断 |
| --- | --- | --- |
| `jpaTest` | `userRepository.findAll()` 和按 id 查询得到的 `User` 实体 | 遗留调试输出；可能触发实体 `toString`，删除 |
| `jpaSortTest` | 分页统计字段和 `Page` 内容 | 遗留调试输出；内容可能包含 `Diary` 实体，删除 |
| `ragTest` | 只有被注释的历史 `println` | 不产生运行时日志；不作为本切片的运行时问题 |

全量测试源码没有发现主动 `printStackTrace()`、`System.err` 或其他实体输出路径。实现时删除
上述两个 JUnit 方法中的 active `System.out.println`，不以新的日志替代，也不改动测试查询和
断言行为。

`src/test/java/com/aseubel/BinaryDataTest.java` 与 `src/test/java/com/aseubel/OssTest.java`
虽然含有 `System.out.println`，但它们是带 `main` 的手工反射探针，没有 JUnit 测试方法，不属于
默认 Surefire 测试执行路径；输出的是 SDK 方法签名而非用户/实体数据。本切片保留它们的手工
探针语义，不把它们误判成默认测试日志噪音。提交前的 stdout 自检会明确排除这两个 allowlist
文件，并单独确认 `YusiApplicationTests` 没有 active `System.out`。

### 2.2 异常与 WARN 来源盘点

对已有全量测试产物和已知失败分支做了只读归类；当前 Surefire XML 中可复现的业务定向采样为
`WARN` 6 条，另有 Spring/H2 上下文初始化产生的 Hibernate dialect 弃用 WARN。数量会随上下文
初始化次数变化，不作为产品契约；契约是来源和日志级别边界。

| logger 类别 | 来源测试/分支 | 日志形态 | 本切片处理 |
| --- | --- | --- | --- |
| `com.aseubel.yusi.service.agent.impl.AgentProactiveServiceImpl` | `AgentProactiveServiceImplTest` 的模型调用失败回退、通知失败工作流 | `WARN` 携带异常堆栈；定向采样 3 条 | test profile 设为 `ERROR`，压掉 WARN/堆栈；保留该类真正的 `ERROR` 扫描异常 |
| `com.aseubel.yusi.service.memory.MidTermMemoryLifecycleService` | `MidTermMemoryLifecycleServiceTest` 的向量清理故障分支；评测/生命周期路径复用同一服务 | `WARN` 携带异常对象 | test profile 设为 `ERROR`，保留 ERROR |
| `com.aseubel.yusi.service.ai.model.ModelProxyFactory` | `ModelProxyFactoryTest` 的模型失败/回退场景 | `WARN`，不携带完整堆栈 | test profile 设为 `ERROR` |
| `com.aseubel.yusi.common.utils.CompressUtils` | `CompressUtilsTest` 的损坏输入/解压失败场景 | `WARN`，只记录异常 message | test profile 设为 `ERROR` |
| `org.hibernate.orm.deprecation` | Spring Boot + H2 上下文初始化时的 H2 dialect 弃用提示 | 框架 `WARN`，不属于业务失败 | test profile 设为 `ERROR` |

当前 `src/test/resources/application-test.yml` 只有 `logging.level.root: warn`，没有按类别的
覆盖。因此预期失败路径的 WARN 与堆栈会进入测试输出。上表中的四个业务 logger 均来自明确
覆盖了失败分支的测试；不是通过全局关闭日志来掩盖未知问题。

### 2.3 配置选择

采用两层测试配置的定向阈值覆盖，不修改 `src/main/resources/logback-spring.xml`，也不把生产
logger 改成更高阈值。`application-test.yml` 覆盖 Spring Boot test profile；纯 Mockito 测试
不启动 Spring profile，因此由同样只存在于测试 classpath 的 `logback-test.xml` 提供一致兜底。
两层配置使用相同的 logger 白名单和阈值，避免测试类型不同导致日志策略漂移。

`application-test.yml` 的策略为：

```yaml
logging:
  level:
    root: warn
    com.aseubel.yusi.service.agent.impl.AgentProactiveServiceImpl: error
    com.aseubel.yusi.service.memory.MidTermMemoryLifecycleService: error
    com.aseubel.yusi.service.ai.model.ModelProxyFactory: error
    com.aseubel.yusi.common.utils.CompressUtils: error
    org.hibernate.orm.deprecation: error
```

选择 `ERROR` 而不是 `OFF` 的理由是：测试中故意触发的降级和依赖失败以 WARN 记录，应该安静；
真正的 ERROR 仍然需要出现在测试日志中，避免“降噪”变成吞错。`root` 仍保持 `WARN`，未盘点
的 logger 不被静默。`logback-test.xml` 只复制 root 和五个类别级别，不复制生产文件 appender，
避免测试写入 `data/log`。

## 3. 评测与低敏不变量

本切片不修改下列文件或逻辑：

- `src/test/java/com/aseubel/yusi/evaluation/**`；
- `src/test/resources/evaluation/**`；
- `src/test/java/com/aseubel/yusi/evaluation/OfflineEvaluationReportWriter.java`；
- `target/evaluation/*.json` 的生成路径、schema、suite id、fixture 和 actualSummary；
- CI 对 `target/evaluation` 的归档规则。

因此报告内容应保持不变。由于报告 writer 会刷新运行元数据，报告对比只忽略每个报告根节点
的 `generatedAt`，其余 JSON 结构和值必须与清理前一致；不能用包含时间戳的原始文件 hash
作为唯一依据。最终仍需确认报告没有新增用户 query、记忆正文、Prompt、工具参数/结果、密钥、
异常正文或堆栈。

测试日志清理也不得让测试输出更多敏感值。删除实体 `toString` 输出后，日志中不应出现用户
对象字段、Diary 内容或其它 fixture 正文；本切片不新增任何日志语句。

## 4. 非目标与 Phase 5 隔离

以下生产日志安全工作仍属于 roadmap Phase 5，不能在本切片勾选、实现或顺手重构：

| Phase 5 生产入口 | 本切片处理 |
| --- | --- |
| `McpGrpcServiceImpl` | 不修改 query/keyword 日志 |
| `DiarySearchTool` | 不修改用户搜索日志 |
| `LifeGraphTool` | 不修改图查询日志 |
| `MidTermMemorySearchService` | 不修改记忆搜索日志 |
| 全量日志敏感正文 grep、自定义低敏格式、可观测与告警 | 不做；留给 Phase 5 |

路线图当前 Phase 5 的“日志安全收敛”仍为未完成项。本切片只做测试 profile 的 logger 阈值和
测试调试输出清理，不能作为 Phase 5 或上线标准“日志中不再出现敏感正文”的证据。

上线后扩展 Backlog 不在本切片范围内，也不因测试日志变干净而启动任何条目。

## 5. 验收标准

1. `YusiApplicationTests` 不再有 active 实体或分页 `System.out` 输出；默认测试路径没有新的
   stdout 调试输出。
2. 已盘点的五类 logger 在 Spring test profile 和纯 JUnit test classpath 下都不再以 WARN 打印；
   真正的 ERROR 仍可见。
3. `BinaryDataTest`、`OssTest` 的手工反射探针是否保留有明确 allowlist 说明，且不被默认
   `.\mvnw.cmd -q test` 执行。
4. 所有现有评测测试与报告产物继续按原路径生成，除 `generatedAt` 外 JSON 语义不变。
5. `.\mvnw.cmd -q test` 全量通过，未启动应用服务或外部依赖。
6. `git diff --name-only` 证明没有 `src/main`、生产日志配置、fixture、evaluation writer、
   roadmap 或 backlog 变更；roadmap 自查确认 Phase 5 checkbox 未被勾选。
7. 完成独立提交后停止，等待用户验收；不合并 Phase 5 生产敏感日志收敛。
