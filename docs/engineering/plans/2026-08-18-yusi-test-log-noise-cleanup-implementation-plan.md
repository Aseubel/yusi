# Yusi 测试日志噪音清理实施计划

> **For inline execution:** 本计划只在设计文档经过用户评审确认后执行。当前阶段只提交本计划，未获确认前不修改测试代码、测试配置或生产代码。项目纪律禁止启用子 agent 和 auto-review。

**Goal:** 删除默认 JUnit 测试中的遗留实体输出，并通过 test profile 与 test classpath 的定向 logger 阈值收敛已盘点的预期失败 WARN/异常堆栈；保持生产代码、评测报告和上线后扩展范围不变。

**Architecture:** `YusiApplicationTests` 只保留测试行为，不再打印查询结果；`application-test.yml` 和 `logback-test.xml` 均保持 `root: warn`，仅将五个已盘点的 logger category 提升到 `error`。前者覆盖 Spring Boot 测试 profile，后者覆盖不启动 Spring 的纯 Mockito 测试；两者共同保留真正的 ERROR。手工反射探针不纳入默认 Maven 日志清理路径。

**Tech Stack:** Java 21、Spring Boot 3.4、JUnit 5、Maven Surefire、Logback、H2 test profile、现有 `target/evaluation` 与 `OfflineEvaluationReportWriter`。

## Global Constraints

- 生产代码零修改：不得修改 `src/main/java`、`src/main/resources`、生产 logback、业务 logger 文案或异常处理。
- 测试源和测试配置之外不扩展范围；不修改 `src/test/resources/evaluation`、evaluation 测试、fixture、`OfflineEvaluationReportWriter`、CI 或报告 schema。
- 只删除 `YusiApplicationTests` 的 active `System.out.println`；不添加替代日志或把实体转换成另一种可打印文本。
- `BinaryDataTest` 与 `OssTest` 是非默认执行的手工 `main` 反射探针，保留并写入范围自检 allowlist；不把它们当作 JUnit 默认噪音。
- `application-test.yml` 和 `logback-test.xml` 的 `root: warn` 保持不变；新增的类别级别统一为 `error`，不得使用 `off` 静默真正 ERROR。
- 定向收敛的 logger 仅为：`AgentProactiveServiceImpl`、`MidTermMemoryLifecycleService`、`ModelProxyFactory`、`CompressUtils`、`org.hibernate.orm.deprecation`。
- 任何 Trace、报告、fixture、日志不得持久化或打印用户 query、记忆正文、Prompt、工具参数/结果、fixture 正文、密钥、异常正文或堆栈；清理后不得新增任何日志输出。
- 评测报告只允许 `generatedAt` 运行元数据变化；结构和值必须做 JSON 语义对比，不能把报告内容改动藏在时间戳差异中。
- 不启动应用服务、Milvus、Redis、MySQL、OSS 或模型服务；只运行 Maven 测试命令。
- 实现完成后必须运行全量 `.\mvnw.cmd -q test`，独立提交并停下等待验收。
- 提交前必须自查 roadmap：Phase 5 “日志安全收敛”仍保持未勾选；不得把本切片记为生产日志安全完成，也不得启动 post-release backlog。

## File Map

修改：

- `src/test/java/com/aseubel/yusi/YusiApplicationTests.java`：删除两个 JUnit 测试中的 active 实体/分页输出。
- `src/test/resources/application-test.yml`：增加 Spring test profile 的五个定向 logger level。
- `src/test/resources/logback-test.xml`：新增纯 JUnit 测试使用的同等五类 logger level 和 console appender。
- `docs/engineering/specs/2026-08-18-yusi-test-log-noise-cleanup-design.md`：本切片设计文档。
- `docs/engineering/plans/2026-08-18-yusi-test-log-noise-cleanup-implementation-plan.md`：本实施计划。

不修改：

- `src/main/**` 与 `src/main/resources/logback-spring.xml`；
- `src/test/java/com/aseubel/BinaryDataTest.java`、`src/test/java/com/aseubel/OssTest.java`；
- `src/test/java/com/aseubel/yusi/evaluation/**`、`src/test/resources/evaluation/**`；
- `target/evaluation/**` 生成物不提交；
- roadmap、post-release backlog、CI 配置和报告 writer。

---

## Task 1: Establish the report and logging baseline

**Purpose:** 在实现前保存可比较的状态，避免把测试运行时间或日志清理误判为评测报告变更。

- [x] **Step 1: Confirm the clean scope and roadmap state.**

运行：

```powershell
git status --short --branch
git diff --name-only
rg -n '^### Phase 5|日志安全收敛|post-release|上线后扩展' docs/engineering/plans/2026-08-04-yusi-agent-product-roadmap.md docs/engineering/plans/2026-08-17-yusi-post-release-expansion-backlog.md
```

预期：当前工作区只有本计划切片允许的既有状态；Phase 5 日志 checkbox 仍是 `[ ]`；Backlog
没有被取回或勾选。若发现与本切片无关的用户改动，保留并单独记录，不回滚。

- [x] **Step 2: Snapshot existing evaluation reports semantically.**

在不修改文件的前提下读取 `target/evaluation/*.json`，对每个 JSON 深拷贝并移除根节点
`generatedAt`；递归排序 object 属性名但保留 array 元素顺序，再记录文件名、suite、summary
和 canonical JSON hash。这样 `Map.ofEntries` 导致的属性序列化顺序变化不会被误判为报告变化。
不得把报告正文、fixture 值或任何敏感字段复制到日志或新文件；没有现存报告时只记录 baseline
absent。

同时对 Task 4 将扫描的全部路径（`application-test.yml`、`logback-test.xml` 和
`YusiApplicationTests.java`）读取当前 `secret|password` 命中行，只在内存中保存文件路径和
规范化行签名作为 baseline allowlist，不输出命中行内容、不写入新文件。当前 yml allowlist
对应既有配置路径：`spring.datasource.password`、`redis.sdk.config.password`、
`milvus.password`、`yusi.jwt.secret`、`yusi.oss.access-key-secret`、
`aliyun.dm.accessKeySecret`；`YusiApplicationTests` 中既有的测试密码字面量也属于 baseline。
Task 4 只把当前命中集合减去这份 baseline 集合后的新增项判为违规。

实现可使用临时 PowerShell 对象完成比较，临时文件在命令结束后删除。不要提交
`target/evaluation`，也不要修改 `OfflineEvaluationReportWriter`。

- [x] **Step 3: Record the known logger inventory.**

以设计文档的五类 logger 为准，使用现有 Surefire XML 或一次定向测试输出只统计 `WARN/ERROR`
级别和 logger 名，不回显原始 message、用户字段或堆栈。验证来源能对应：主动问候模型/通知
失败、向量清理失败、模型路由失败、压缩损坏输入和 Hibernate H2 dialect 弃用提示。

预期：不存在新的未盘点业务 logger；若出现新类别，暂停实现并回到设计评审，不通过全局
`root: error` 掩盖。

---

## Task 2: Remove the default JUnit stdout noise

**Files:**

- Modify: `src/test/java/com/aseubel/yusi/YusiApplicationTests.java`

- [x] **Step 1: Remove only active debug output.**

删除 `jpaTest` 中打印 `findAll()` 和 `findById()` 结果的两行；删除 `jpaSortTest` 中打印分页
统计和 content 的五行。保留 repository save/delete、排序查询和其它测试 setup，使测试覆盖和
生命周期不变。注释中的旧 `ragTest` 代码不产生输出，不因本任务引入业务改动。

- [x] **Step 2: Verify the manual probe boundary.**

确认 `BinaryDataTest` 和 `OssTest` 仍仅含 `main` 反射探针，没有 JUnit `@Test` 方法；默认
Surefire 不会调用它们的 `main`。不要通过删除手工探针输出来制造“全仓库无 println”的假目标。

- [x] **Step 3: Run a source-level stdout check.**

```powershell
rg -n '^\s*System\.(out|err)\.|printStackTrace\(' src/test/java -g '!**/BinaryDataTest.java' -g '!**/OssTest.java'
```

预期：无 active 命中；若命中新的测试输出，先判断是否是本切片范围内的调试噪音再处理。该
检查不扫描注释中的 `System.out`，也不打印测试数据。

---

## Task 3: Add test-profile and test-classpath logger thresholds

**Files:**

- Modify: `src/test/resources/application-test.yml`
- Create: `src/test/resources/logback-test.xml`

- [x] **Step 1: Add category-level `error` overrides to the Spring test profile.**

在现有 `logging.level.root: warn` 下增加：

```yaml
    com.aseubel.yusi.service.agent.impl.AgentProactiveServiceImpl: error
    com.aseubel.yusi.service.memory.MidTermMemoryLifecycleService: error
    com.aseubel.yusi.service.ai.model.ModelProxyFactory: error
    com.aseubel.yusi.common.utils.CompressUtils: error
    org.hibernate.orm.deprecation: error
```

保持 YAML 缩进和 profile 结构，不修改 `spring.jpa.show-sql` 或其它测试依赖配置。五个类别
分别压掉已盘点 WARN；`ERROR` 仍然可见。

- [x] **Step 2: Add the equivalent test-only Logback configuration.**

创建 `src/test/resources/logback-test.xml`，只包含一个 console appender、`root WARN` 和
与 Step 1 完全相同的五个 `logger level="ERROR"` 节点；不引用生产文件的 rolling/file
appender，不写入 `data/log`，不改变 logger message 或 pattern 中的业务字段。由于 Logback 的
优先级，`logback-test.xml` 会优先于同一 test classpath 上的 `logback-spring.xml`，因此会接管
全部测试进程，包括 `@SpringBootTest`，这些测试不再走生产 `logback-spring.xml`。测试专用
console pattern 的变化是有意的，只影响测试输出，不代表生产日志格式变更。

预期：纯 Mockito 测试在没有 Spring `ApplicationContext` 的情况下，也会按相同类别阈值过滤
WARN；未知 logger 的 WARN 不被全局吞掉，真正 ERROR 仍然输出。

- [x] **Step 3: Verify both configuration paths.**

必须同时运行一个现有 `@SpringBootTest` 评测/生命周期测试和一个定向纯 Mockito 失败分支测试，
使用 Surefire XML 或捕获的控制台输出只提取 logger category 与 level。不得把完整日志回显到
终端。验证 `@SpringBootTest` 和纯 Mockito 两条路径都使用 test-only 配置，已盘点 WARN 被压掉，
且预期 ERROR 没有被吞掉。

---

## Task 4: Verify report invariants and full test behavior

- [x] **Step 1: Run focused noise tests without starting services.**

```powershell
.\mvnw.cmd -q -Dtest=AgentProactiveServiceImplTest,MidTermMemoryLifecycleServiceTest,ModelProxyFactoryTest,CompressUtilsTest test
```

预期：测试通过；已盘点的 WARN 不再输出，若出现 ERROR 必须保留并解释，不能改阈值继续隐藏。

- [x] **Step 2: Run the full Maven suite.**

```powershell
.\mvnw.cmd -q test
```

预期：退出码为 `0`；所有既有评测测试照常执行，`target/evaluation` 报告照常生成；不启动
应用服务或外部依赖。

- [x] **Step 3: Compare evaluation reports after the suite.**

对 Task 1 的每个 baseline report 再次移除 `generatedAt`，递归排序 object 属性名、保留 array
顺序后逐个比较 canonical JSON。预期：所有既有 report 除 `generatedAt` 外完全一致；文件数量、
suite、summary、case、versions 和固定 violation code 不变。若 baseline 不存在，则至少确认
本次没有新增 evaluation test 或 report writer 变更，并列出全量生成的报告名供验收。

执行记录：连续全量回放均为 PASS，所有 case/status/assertion/actualSummary 数值一致；早期
原始 hash 差异仅来自 object 属性序列化顺序，按本步骤 canonical 规则不构成报告语义变化。

- [x] **Step 4: Re-run the low-sensitivity source/output checks.**

```powershell
rg -n '^\s*System\.(out|err)\.|printStackTrace\(' src/test/java -g '!**/BinaryDataTest.java' -g '!**/OssTest.java'
rg -n 'query|keyword|rawText|plainContent|toolArguments|toolResult|secret|password' src/test/resources/application-test.yml src/test/resources/logback-test.xml src/test/java/com/aseubel/yusi/YusiApplicationTests.java
```

第一条预期无 active 测试输出；第二条只允许命中已有测试配置键或代码结构，不得出现由本切片
新增的用户数据、fixture 正文、Prompt、工具参数/结果、密钥或异常正文。对第二条命中结果先
按 Task 1 Step 2 保存的 baseline allowlist 排除 `application-test.yml` 既有六个配置路径；只要
出现 baseline 之外的新增 `secret|password` 命中就失败。不得向 `application-test.yml` 新增
密钥键值，也不得把 allowlist 的命中值回显到日志。日志采样只输出类别统计，不输出原始内容。

---

## Task 5: Scope, roadmap self-check, and independent commit

- [x] **Step 1: Inspect changed files and diff hygiene.**

```powershell
git diff --check
git status --short
git diff --name-only
```

预期变更只落在本计划的测试代码、测试 profile、test-only Logback、设计文档和实施计划；禁止
出现 `src/main`、生产 logback、evaluation、fixture、CI、roadmap 或 backlog 文件。

- [x] **Step 2: Self-check the roadmap before commit.**

```powershell
git diff -- docs/engineering/plans/2026-08-04-yusi-agent-product-roadmap.md
rg -n '日志安全收敛|Phase 5' docs/engineering/plans/2026-08-04-yusi-agent-product-roadmap.md
```

预期：roadmap 无 diff；Phase 5 “日志安全收敛”仍为未完成。若 roadmap 被其他并行工作改动，
保留用户改动并重新核对，不能把它并入本切片或擅自补勾。

- [x] **Step 3: Commit only this slice.**

```powershell
git add src/test/java/com/aseubel/yusi/YusiApplicationTests.java src/test/resources/application-test.yml src/test/resources/logback-test.xml docs/engineering/specs/2026-08-18-yusi-test-log-noise-cleanup-design.md docs/engineering/plans/2026-08-18-yusi-test-log-noise-cleanup-implementation-plan.md
git commit -m 'test: reduce test log noise'
```

提交前必须确认全量 Maven 测试已通过且报告语义对比完成。提交后停止，不进入 Phase 5 生产日志
治理，不开始任何新切片，等待用户验收。

## Acceptance Checklist

- [x] 设计文档和实施计划已评审确认。
- [x] `YusiApplicationTests` active 实体/分页输出已删除，手工反射探针边界已记录。
- [x] 五个已盘点 logger 在 Spring test profile 和纯 JUnit test classpath 均收敛到 `error`，未修改生产日志。
- [x] 全量 `.\mvnw.cmd -q test` 通过。
- [x] 既有 `target/evaluation/*.json` 除 `generatedAt` 外语义不变，产物未被手工修改。
- [x] roadmap 对应项已在提交前自查且 Phase 5 未误勾选。
- [x] 独立提交完成后停止等待验收。
