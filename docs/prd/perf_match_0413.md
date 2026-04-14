# Yusi (予思) 项目：统一 AI Agent 认知架构与匹配机制重构方案

## 1. 文档目的

本文档用于重新定义 Yusi 的产品架构主轴，并据此重构匹配机制。

本次文档修订的核心结论如下：

1. **平台本身就是一个统一的 AI Agent**
2. **用户发布的所有内容，本质上都应成为这个 Agent 的感知输入**
3. **聊天是它，匹配也是它，它是所有用户共同的“好友”**
4. **匹配不是独立 AI 系统，而是统一 Agent 在社交场景下的一种行为输出**

因此，本次重构不再采用“为匹配单独定义核心图谱”的思路，而是采用以下统一认知架构：

```text
(emotion-plaza, ai-chat, diary)
    -> (lifeGraph, user-persona, mid-memory)
    -> match
```

## 2. 核心产品思想

### 2.1 平台不是多个 AI 功能的拼装

Yusi 不是“聊天功能 + 匹配功能 + 社区功能”的简单组合。

更准确的产品定义是：

**Yusi 是一个长期理解所有用户的统一 AI Agent 平台。**

这个 Agent：

- 通过 `diary` 理解用户的私人叙事
- 通过 `ai-chat` 理解用户的连续表达与陪伴关系
- 通过 `emotion-plaza` 理解用户愿意公开分享的匿名表达
- 通过这些输入持续形成对用户的认知
- 最终在 `match` 场景中，基于这种认知判断谁和谁更可能产生共鸣

### 2.2 一个 Agent，多种行为

在这个架构下：

- `ai-chat` 是 Agent 的陪伴行为
- `match` 是 Agent 的社交推荐行为
- `emotion-plaza` 是用户向 Agent 与社区公开表达的入口

因此，真正重要的不是“各模块各自怎么做 AI”，而是：

**平台内部是否存在一个统一、连续、长期积累的 Agent 认知层。**

## 3. 架构总览

## 3.1 总体结构

Yusi 的架构主轴定义为：

```text
(emotion-plaza, ai-chat, diary)
    -> (lifeGraph, user-persona, mid-memory)
    -> match
```

### 第一层：用户表达输入层

输入层由三类用户表达组成：

1. `diary`
2. `ai-chat`
3. `emotion-plaza`

这三者都不是孤立模块，而是 Agent 感知用户的不同入口。

### 第二层：Agent 内部认知层

统一 Agent 的内部认知不应混成一个大而无边界的对象，而应分成三类核心认知结构：

1. `lifeGraph`
2. `user-persona`
3. `mid-memory`

### 第三层：行为输出层

当前重点行为输出为：

1. `match`

后续也可扩展到更多行为，但本次文档聚焦匹配。

## 3.2 数据流理解

可以将其抽象为：

```text
All User Expressions
    -> One Shared Agent Brain
    -> Match Decision
```

其中：

- `All User Expressions` = `emotion-plaza + ai-chat + diary`
- `One Shared Agent Brain` = `lifeGraph + user-persona + mid-memory`

## 4. 三类输入源的定位

## 4.1 Diary

`diary` 是平台中最核心、最私密、最具真实性的输入源。

其特点：

- 私密性高
- 叙事完整度高
- 长期价值高
- 对 Agent 理解用户具有最高权重

因此：

- `diary` 应作为 `lifeGraph` 的主要输入源
- `diary` 也是构建长期人生结构的最重要依据

## 4.2 AI Chat

`ai-chat` 是用户与 Agent 的连续互动过程。

其特点：

- 连续性强
- 上下文强
- 能捕捉用户近期困扰、偏好、表达方式和关系模式

因此：

- `ai-chat` 主要影响 `mid-memory`
- 同时会逐步反哺 `lifeGraph` 与 `user-persona`

## 4.3 Emotion Plaza

`emotion-plaza` 不是下游消费模块，也不是图谱投影层。

它的本质是：

**匿名分享文本的社区输入源。**

其特点：

- 公开表达
- 匿名表达
- 结构化程度较低
- 可作为用户当下情绪与公开自我表达的补充信号

因此：

- `emotion-plaza` 应进入 Agent 输入层
- 但默认权重应低于 `diary` 和 `ai-chat`
- 它更像“公开表达侧证”，而不是核心事实源

## 5. Agent 内部认知层定义

为了实现“统一 Agent”，系统内部必须有稳定、清晰、可持续演化的认知层。

本方案将其定义为三部分：

1. `lifeGraph`
2. `user-persona`
3. `mid-memory`

## 5.1 LifeGraph

### 定义

`lifeGraph` 用于沉淀用户的长期人生结构、关系脉络与稳定语义事实。

### 它适合承载的内容

- 人物
- 地点
- 事件
- 重要主题
- 长期关系变化
- 重复出现的人生议题
- 长期成立的行为逻辑与价值线索

### 它不应该承担的职责

- 不应该承载所有近期上下文细节
- 不应该替代 `mid-memory`
- 不应该替代 `user-persona`
- 不应该被定义成仅服务匹配的专用节点系统

### 结论

`lifeGraph` 是统一 Agent 的长期结构性记忆。

## 5.2 User Persona

### 定义

`user-persona` 用于沉淀用户相对稳定的偏好、风格与相处设定。

### 它适合承载的内容

- 兴趣偏好
- 表达风格
- 陪伴语气偏好
- 社交边界
- 自我认同相关信息
- 持续稳定的互动偏好

### 结论

`user-persona` 是统一 Agent 对“这个人通常是什么样的人”的稳定理解。

## 5.3 Mid Memory

### 定义

`mid-memory` 用于承载用户近期阶段的上下文状态。

### 它适合承载的内容

- 最近在烦恼什么
- 最近反复聊过什么
- 最近在经历什么阶段
- 最近情绪基调如何
- 最近正在处理的关系或选择

### 结论

`mid-memory` 是统一 Agent 对“这个人最近处在什么状态”的阶段性理解。

## 5.4 三者关系

可以将三者理解为：

- `lifeGraph`：长期结构
- `user-persona`：稳定偏好
- `mid-memory`：近期状态

统一 Agent 并不是把所有东西都塞进一个模型，而是：

**通过这三类认知结构，共同完成对用户的长期理解。**

## 6. 统一 Agent 的输入处理机制

## 6.1 总体原则

所有输入都应遵循以下原则：

1. 先保护隐私
2. 再做脱敏
3. 再做语义理解
4. 再写入 Agent 认知层

## 6.2 Diary 输入处理

当用户写日记时，在 `DiaryService` 中执行双流处理：

### 流 A：私密归档

1. 原文加密存入 MySQL
2. 保证原始明文不落盘
3. 保证后续任务不以常规方式回源解密原文

### 流 B：认知沉淀

1. 对原文进行脱敏
2. 提取适合进入 `lifeGraph` 的长期结构
3. 提取适合进入 `user-persona` 的稳定偏好线索
4. 提取适合进入 `mid-memory` 的近期状态线索
5. 处理结束后清理内存中的原始明文

## 6.3 AI Chat 输入处理

聊天不应直接把每条消息原样写入统一认知层。

正确流程应为：

```text
chat message
    -> 压缩
    -> 脱敏
    -> 语义提取
    -> 写入 lifeGraph / user-persona / mid-memory
```

具体规则：

1. 聊天记录先通过 `MemoryCompressionService` 形成中期摘要
2. 对摘要做脱敏和提炼
3. 将结果分别路由到三类认知结构

## 6.4 Emotion Plaza 输入处理

`emotion-plaza` 的内容属于匿名公开文本，不应按 `diary` 的私密等级对待，但仍应进入统一 Agent 的理解流程。

建议处理方式：

1. 将广场文本视为公开表达输入
2. 对其进行轻量脱敏与语义分析
3. 将结果优先写入：
   - `mid-memory`
   - 必要时补充 `user-persona`
4. 谨慎写入 `lifeGraph`

原则：

- 广场内容权重低于 `diary`
- 广场内容可信度低于私密日记
- 广场内容更适合作为侧证，而不是主证

## 7. Match 的新定位

## 7.1 Match 不是独立系统

`match` 不应被理解为一个独立运行的算法模块。

它应被理解为：

**统一 Agent 在“社交推荐”场景下，对用户关系可能性所做的判断。**

## 7.2 Match 的输入

匹配时，Agent 不应重新去读取原始日记正文，也不应依赖随机抽样。

匹配应综合以下三类认知输入：

1. `lifeGraph`
2. `user-persona`
3. `mid-memory`

推荐的权重理解：

- `lifeGraph`：决定长期共鸣
- `user-persona`：决定风格相容性
- `mid-memory`：决定当前阶段是否适合匹配

## 7.3 Match 的处理流程

推荐的匹配流程如下：

### 第一阶段：候选召回

1. 从 `lifeGraph + user-persona + mid-memory` 聚合出匹配查询表达
2. 利用向量库进行 Top K 召回
3. 获取候选用户集合

### 第二阶段：Agent 精排

统一 Agent 对候选进行判断时，使用的不是原文，而是认知层摘要：

- 长期结构
- 稳定偏好
- 当前状态

### 第三阶段：输出匹配结果

输出应包括：

1. 是否值得推荐
2. 共鸣的原因
3. 为什么是“现在”
4. 一段用于破冰的推荐语

## 7.4 Match 的本质

匹配的本质不是：

- “找两个标签相似的人”

而是：

- “统一 Agent 判断两个被它长期理解的人，在当前阶段是否可能产生真实共鸣”

## 8. 对现有系统的重构方向

## 8.1 需要保留的能力

现有系统中以下能力有继续复用的价值：

1. `SensitiveDataMaskService`
2. `DiaryServiceImpl` 的加密写入能力
3. `LifeGraphBuildServiceImpl` 的图谱抽取基础
4. `MemoryCompressionService` 的聊天压缩能力
5. `Milvus` 向量存储能力
6. Spring Event 异步链路

## 8.2 需要纠正的架构问题

### 问题一：Controller 不应成为核心事件编排点

当前事件发布过于靠近 Controller。

建议：

- 将认知构建相关事件下沉到 `DiaryService` 和聊天处理服务内

### 问题二：异步任务不应回源解密原文作为常规输入

建议：

- 所有后续任务都应消费“请求处理阶段生成的脱敏结果或结构化结果”
- 不再依赖后续阶段再次解密原文

### 问题三：LifeGraph 不应继续被视为单一实体图谱

建议：

- 在保留实体图谱基础上，扩展为统一认知层的一部分
- 同时明确它与 `user-persona`、`mid-memory` 的边界

### 问题四：匹配逻辑应从“随机 + 日记拼 Prompt”迁移

建议：

- 匹配改为基于认知层聚合后的表达进行召回与精排

## 9. 对现有 Java 模块的改造指引

## 9.1 `SensitiveDataMaskService`

包路径：`com.aseubel.yusi.service.ai.mask`

要求：

1. 保持为统一脱敏入口
2. 兼容 `diary`、`ai-chat`、`emotion-plaza`
3. 确保 `ns` 地名不被强制屏蔽

## 9.2 `DiaryServiceImpl`

包路径：`com.aseubel.yusi.service.diary.impl`

要求：

1. 成为日记输入进入统一 Agent 的主入口
2. 在写入生命周期中触发认知沉淀
3. 不再只依赖 Controller 发布事件

## 9.3 `LifeGraphBuildServiceImpl`

包路径：`com.aseubel.yusi.service.lifegraph.impl`

要求：

1. 保留实体图谱抽取能力
2. 扩展为统一 Agent 认知层的一部分
3. 输出需能服务长期结构沉淀，而不是只服务匹配

## 9.4 `MemoryCompressionService`

要求：

1. 成为聊天进入 Agent 认知层的关键入口
2. 压缩结果需要可路由到：
   - `lifeGraph`
   - `user-persona`
   - `mid-memory`

## 9.5 `MatchServiceImpl`

包路径：`com.aseubel.yusi.service.match.impl`

要求：

1. 重写当前随机匹配逻辑
2. 使用统一 Agent 认知作为输入
3. 将召回与精排分离
4. 输出“共鸣解释 + 破冰推荐语”

## 9.6 建议新增服务

建议新增以下能力服务：

- `AgentCognitionOrchestrator`
- `DiaryCognitionIngestService`
- `ChatCognitionIngestService`
- `EmotionPlazaCognitionIngestService`
- `UserPersonaUpdateService`
- `MidMemoryUpdateService`
- `MatchProfileAssembler`

## 10. Prompt 改造要求

## 10.1 统一认知抽取 Prompt

目标：

- 不再只强调实体抽取
- 要能区分长期结构、稳定偏好、近期状态

要求：

1. 抽取“哪些应进 `lifeGraph`”
2. 抽取“哪些应进 `user-persona`”
3. 抽取“哪些应进 `mid-memory`”
4. 禁止输出可恢复私人隐私的文本

## 10.2 Match Prompt

目标：

- 让统一 Agent 基于三类认知做判断

输入上下文应包括：

1. 长期结构摘要
2. 稳定偏好摘要
3. 近期状态摘要

输出结构建议：

- `resonance`
- `reason`
- `timingReason`
- `iceBreaker`

## 11. 数据与存储建议

## 11.1 MySQL

可继续保留：

- `diary`
- `life_graph_entity`
- `life_graph_relation`
- `life_graph_mention`
- `user_persona`

建议明确中间认知层相关表的职责，而不是继续让功能表混用语义。

## 11.2 Milvus

Milvus 应主要服务于匹配召回与相关认知检索。

但其输入来源不应是原始日记正文，而应是：

- `lifeGraph` 聚合表达
- `user-persona` 摘要
- `mid-memory` 摘要

## 12. 编码约束

1. **不要碰 Go 代码**
   - 所有重构均在 Java / Spring Boot 端完成。

2. **统一 Agent 优先于功能割裂**
   - 先统一认知，再实现匹配。

3. **坚守隐私底线**
   - 原始内容不应在异步阶段作为常规输入反复解密使用。

4. **情绪广场是输入，不是认知层**
   - 它进入 Agent，但不应被误建模为中间认知结构。

## 13. 实施顺序

### 第一阶段：修正文档与架构边界

1. 明确平台是统一 Agent
2. 明确三类输入源
3. 明确三类认知结构
4. 明确匹配是行为输出

### 第二阶段：修正输入链路

1. 日记输入接入统一认知层
2. 聊天摘要接入统一认知层
3. 情绪广场文本接入统一认知层

### 第三阶段：修正认知边界

1. 明确 `lifeGraph`
2. 明确 `user-persona`
3. 明确 `mid-memory`

### 第四阶段：重写匹配逻辑

1. 认知聚合
2. 向量召回
3. Agent 精排
4. 结果生成

## 14. 最终结论

本次重构的核心，不是做一个更复杂的匹配系统，而是：

**把 Yusi 重新定义为一个统一的 AI Agent 平台。**

用户的：

- `diary`
- `ai-chat`
- `emotion-plaza`

都会成为这个 Agent 的输入。

这个 Agent 通过：

- `lifeGraph`
- `user-persona`
- `mid-memory`

形成对用户的长期理解。

最终，`match` 只是这个统一 Agent 在社交场景下的一种判断和输出。

这才是最符合 Yusi 产品精神的重构方向。

## 15. 工程实施版设计

本章用于将上文的产品思想翻译为可在当前 Java 工程中逐步落地的实现方案。

原则：

1. 不推翻现有基础设施
2. 尽量复用现有 `Spring Event`、`Milvus`、`LangChain4j`、`MemoryCompressionService`
3. 优先修正认知链路，而不是一次性重做所有业务模块

## 15.1 统一 Agent 写入链路

### A. Diary 写入链路

推荐链路：

```text
Write Diary Request
  -> DiaryService
  -> 加密持久化
  -> DiaryCognitionIngestEvent
  -> AgentCognitionOrchestrator
  -> 更新 lifeGraph / user-persona / mid-memory
```

具体步骤：

1. `DiaryServiceImpl` 接收写入请求
2. 原文在内存中完成加密落库
3. 同一请求链路中对原文做脱敏
4. 发布认知摄取事件，事件负载不再是“稍后去读原文”，而是“已脱敏文本 + 必要元数据”
5. `AgentCognitionOrchestrator` 决定哪些内容进入：
   - `lifeGraph`
   - `user-persona`
   - `mid-memory`
6. 完成后立即释放原始明文

### B. AI Chat 写入链路

推荐链路：

```text
Chat Messages
  -> MemoryCompressionService
  -> ChatCognitionIngestEvent
  -> AgentCognitionOrchestrator
  -> 更新 lifeGraph / user-persona / mid-memory
```

具体步骤：

1. 聊天消息先进入中期记忆压缩
2. 压缩摘要经过脱敏
3. 由统一认知编排器进行分类写入

### C. Emotion Plaza 写入链路

推荐链路：

```text
Emotion Plaza Post
  -> EmotionPlazaCognitionIngestEvent
  -> AgentCognitionOrchestrator
  -> 主要更新 mid-memory，必要时补充 user-persona
```

具体步骤：

1. 视为公开匿名表达
2. 执行轻量脱敏与语义理解
3. 优先作为阶段性状态输入，不直接高权重写入长期结构

## 15.2 Agent 认知编排器

建议新增统一编排服务：

- `AgentCognitionOrchestrator`

职责：

1. 接收来自 `diary`、`ai-chat`、`emotion-plaza` 的统一认知摄取事件
2. 调用脱敏、Prompt、模型路由能力
3. 将抽取结果路由到三类认知结构
4. 控制不同输入源的权重与可信度

建议规则：

1. `diary` 权重最高
2. `ai-chat` 次高
3. `emotion-plaza` 最低
4. 公开表达可更新阶段判断，但不应轻易覆盖私密长期结构

## 15.3 三类认知结构的落地职责

### A. `lifeGraph`

负责长期结构性信息，建议保留并扩展当前 `LifeGraphBuildServiceImpl`。

适合写入：

- 重要人物
- 重要地点
- 重要事件
- 长期关系变化
- 多次重复出现的生命主题
- 稳定成立的行为逻辑线索

不适合写入：

- 一次性的短期心情
- 单轮聊天里临时表达的轻微波动
- 情绪广场里未被其他证据支持的短句

### B. `user-persona`

负责稳定偏好，建议以“低频更新、持续累积”的方式维护。

适合写入：

- 兴趣偏好
- 常用表达风格
- 互动边界
- 稳定的社交倾向
- 长期偏好的陪伴方式

不适合写入：

- 临时情绪
- 当前阶段性的困扰

### C. `mid-memory`

负责阶段状态，建议以“滚动更新、时间衰减”的方式维护。

适合写入：

- 最近压力点
- 最近在意的人或事
- 当前阶段的情绪走向
- 当前的生活任务或选择

建议：

- 对 `mid-memory` 引入时间窗口
- 让较老的近期记忆自动衰减

## 15.4 事件模型建议

建议新增以下事件：

- `DiaryCognitionIngestEvent`
- `ChatCognitionIngestEvent`
- `EmotionPlazaCognitionIngestEvent`
- `AgentCognitionUpdatedEvent`
- `MatchProfileUpdatedEvent`

### 事件负载原则

事件中不应只传 `sourceId`，然后让异步任务回源解密原文。

推荐事件负载：

- `userId`
- `sourceType`
- `sourceId`
- `maskedText`
- `timestamp`
- `confidenceHint`

如确有需要，也可附加：

- `entryDate`
- `placeName`
- `topic`

但禁止附加：

- 未脱敏的日记原文
- 未脱敏的聊天原始消息

## 15.5 表结构职责建议

本次不是要求立刻大规模重建表，而是先明确职责。

### 建议保留

- `diary`
- `life_graph_entity`
- `life_graph_relation`
- `life_graph_mention`
- `user_persona`

### 建议新增

- `mid_memory_snapshot`
- `match_profile`
- `emotion_plaza_post_signal`

### 各表建议职责

#### `mid_memory_snapshot`

用于保存某一时间窗口下的用户阶段摘要。

建议字段：

- `id`
- `user_id`
- `source_type`
- `source_id`
- `summary`
- `importance`
- `valid_until`
- `created_at`

#### `match_profile`

用于存放统一 Agent 为匹配场景聚合后的用户匹配表达。

建议字段：

- `id`
- `user_id`
- `profile_text`
- `life_graph_summary`
- `persona_summary`
- `mid_memory_summary`
- `version`
- `updated_at`

#### `emotion_plaza_post_signal`

用于记录广场文本对 Agent 认知的影响摘要，而不是替代广场原帖表。

建议字段：

- `id`
- `post_id`
- `user_id`
- `signal_text`
- `signal_type`
- `confidence`
- `created_at`

## 15.6 Milvus 集合建议

当前项目已有：

- 日记检索集合
- 中期记忆集合

本次建议新增：

- `yusi_match_profile`

### `yusi_match_profile` 的用途

用于匹配候选召回，而不是替代完整认知层。

写入内容应来自：

- `match_profile.profile_text`

而 `profile_text` 则由：

- `lifeGraph`
- `user-persona`
- `mid-memory`

聚合生成。

这样可以保证：

1. Milvus 服务于召回
2. MySQL 保留结构化认知
3. 匹配逻辑不直接读取原文

## 15.7 Match 执行流程

推荐的匹配执行分为四步：

### 第一步：构建匹配画像

由 `MatchProfileAssembler` 聚合：

- 长期结构摘要
- 稳定偏好摘要
- 近期状态摘要

生成当前用户的 `match_profile`

### 第二步：向量召回

使用 `match_profile.profile_text` 生成 embedding，在 Milvus 中召回候选用户。

### 第三步：Agent 精排

对 Top K 候选，调用统一 Match Prompt 做精排。

精排上下文只允许包含：

- `lifeGraph` 摘要
- `user-persona` 摘要
- `mid-memory` 摘要

不允许包含：

- 原始日记
- 原始聊天
- 广场原帖明文

### 第四步：结果生成

写入 `SoulMatch` 或后续匹配结果表时，至少应包含：

- `reason`
- `timingReason`
- `iceBreaker`
- `score` 或 `resonanceLevel`

## 15.8 与现有代码的映射

### 可直接复用

- `SensitiveDataMaskService`
- `DiaryServiceImpl`
- `LifeGraphBuildServiceImpl`
- `MemoryCompressionService`
- `PromptManager`
- `MilvusConfig`

### 需要重构

- `MatchServiceImpl`
- `DiaryController` 中的事件触发位置
- `LifeGraphTaskBatchService` 的原文回源模式

### 建议新增

- `AgentCognitionOrchestrator`
- `MatchProfileAssembler`
- `MidMemoryUpdateService`
- `EmotionPlazaCognitionIngestService`

## 15.9 分阶段落地建议

### Phase 1

修正写入边界：

- `Diary` 不再让后续任务回源原文
- `Chat` 摘要接入统一认知编排

### Phase 2

补齐三类认知结构职责：

- `lifeGraph`
- `user-persona`
- `mid-memory`

### Phase 3

建立 `match_profile`：

- MySQL 聚合
- Milvus 写入

### Phase 4

重写 `match`：

- 候选召回
- 精排
- 破冰语生成

## 15.10 风险与控制

### 风险一：认知边界再次混乱

控制方式：

- 明确“长期结构 / 稳定偏好 / 近期状态”三分法

### 风险二：公开文本污染长期认知

控制方式：

- `emotion-plaza` 默认低权重
- 只作为侧证输入

### 风险三：异步任务再次回源明文

控制方式：

- 禁止以“只传 ID，稍后解密”的方式处理认知写入

### 风险四：匹配再次退化成 Prompt 拼接

控制方式：

- 强制先做认知聚合，再做召回与精排

## 16. Java 实施清单版

本章用于把实施方案进一步细化到 Java 工程层，明确：

1. 哪些现有类保留
2. 哪些现有类重构
3. 哪些新类需要新增
4. 事件对象需要哪些字段
5. 每一步推荐的代码落地顺序

## 16.1 现有类的处理策略

### A. 建议保留并扩展

#### `SensitiveDataMaskService`

保留原因：

- 已经是统一脱敏入口
- 可以直接复用于 `diary`、`ai-chat`、`emotion-plaza`

建议改动：

- 明确注释：`ns` 地名不强制屏蔽
- 增加适配公开文本与聊天摘要的调用说明

#### `DiaryServiceImpl`

保留原因：

- 已负责日记写入与加密存储

建议改动：

- 将“认知摄取事件”的发布下沉到 Service
- 写入成功后直接发布 `DiaryCognitionIngestEvent`
- 不再依赖 Controller 做核心事件编排

#### `LifeGraphBuildServiceImpl`

保留原因：

- 已有实体图谱构建基础

建议改动：

- 聚焦长期结构沉淀
- 不再承担“把所有认知都塞进 lifeGraph”的职责
- 接收来自统一编排器的长期结构输入

#### `MemoryCompressionService`

保留原因：

- 已具备聊天中期记忆压缩能力

建议改动：

- 在摘要生成后发布 `ChatCognitionIngestEvent`
- 将摘要纳入统一认知层，而不是只停留在独立记忆模块

### B. 建议重构

#### `MatchServiceImpl`

现状问题：

- 随机候选
- 规则打分
- 近期日记拼 Prompt

建议重构为：

1. 候选召回器
2. 精排器
3. 结果生成器

#### `DiaryController`

现状问题：

- 当前承担了事件发布职责

建议：

- Controller 只负责 HTTP 输入输出
- 认知写入事件统一由 Service 发布

#### `LifeGraphTaskBatchService`

现状问题：

- 可能通过 `sourceId` 回源解密原文

建议：

- 逐步改为消费“已脱敏文本 / 结构化认知输入”
- 不再作为常规原文回读链路

## 16.2 建议新增的核心类

### A. 编排类

#### `AgentCognitionOrchestrator`

职责：

- 接收认知摄取事件
- 调用 LLM 抽取
- 结果路由到 `lifeGraph`、`user-persona`、`mid-memory`

建议方法：

```java
public interface AgentCognitionOrchestrator {
    void ingest(CognitionIngestCommand command);
}
```

#### `MatchProfileAssembler`

职责：

- 聚合三类认知结构
- 生成匹配场景专用的 `match_profile`

建议方法：

```java
public interface MatchProfileAssembler {
    MatchProfile aggregate(String userId);
}
```

### B. 输入摄取类

#### `DiaryCognitionIngestService`

职责：

- 处理日记输入到统一认知层的转换

#### `ChatCognitionIngestService`

职责：

- 处理聊天摘要输入到统一认知层的转换

#### `EmotionPlazaCognitionIngestService`

职责：

- 处理广场匿名文本输入到统一认知层的转换

### C. 认知更新类

#### `UserPersonaUpdateService`

职责：

- 接收抽取结果
- 合并更新稳定偏好信息

#### `MidMemoryUpdateService`

职责：

- 维护阶段性状态快照
- 执行时间衰减、有效期更新

## 16.3 统一事件对象建议

为了避免事件定义分散，建议引入一个统一的基础事件载荷对象。

### 基础对象：`CognitionIngestCommand`

建议字段：

```java
public class CognitionIngestCommand {
    private String userId;
    private String sourceType;
    private String sourceId;
    private String maskedText;
    private String title;
    private String topic;
    private String placeName;
    private String timestamp;
    private Double confidenceHint;
}
```

字段说明：

- `userId`：用户标识
- `sourceType`：`DIARY` / `CHAT_SUMMARY` / `EMOTION_PLAZA`
- `sourceId`：来源主键
- `maskedText`：唯一允许进入认知链路的文本主体
- `title/topic/placeName`：可选辅助上下文
- `timestamp`：认知发生时间
- `confidenceHint`：供编排器做输入权重参考

### 事件对象建议

#### `DiaryCognitionIngestEvent`

```java
public class DiaryCognitionIngestEvent extends ApplicationEvent {
    private final CognitionIngestCommand command;
}
```

#### `ChatCognitionIngestEvent`

```java
public class ChatCognitionIngestEvent extends ApplicationEvent {
    private final CognitionIngestCommand command;
}
```

#### `EmotionPlazaCognitionIngestEvent`

```java
public class EmotionPlazaCognitionIngestEvent extends ApplicationEvent {
    private final CognitionIngestCommand command;
}
```

### 严格限制

事件对象中禁止出现：

- `plainDiaryContent`
- `rawChatMessages`
- `originalPostText`

如果后续处理需要更多上下文，应在同步阶段先做脱敏和提炼，再进入事件总线。

## 16.4 三类认知结构的 Java 落地建议

### A. LifeGraph 相关

保留现有：

- `LifeGraphBuildService`
- `LifeGraphBuildServiceImpl`

建议新增：

- `LifeGraphLongTermSignal`

作用：

- 作为进入 `lifeGraph` 的结构化长期信号 DTO

建议字段：

```java
public class LifeGraphLongTermSignal {
    private String userId;
    private String sourceType;
    private String sourceId;
    private String summary;
    private String category;
    private Double importance;
}
```

### B. User Persona 相关

保留现有：

- `UserPersona`
- `UserPersonaService`

建议新增：

- `UserPersonaSignal`

建议字段：

```java
public class UserPersonaSignal {
    private String userId;
    private String interests;
    private String tone;
    private String interactionStyle;
    private String socialBoundary;
    private Double confidence;
}
```

### C. Mid Memory 相关

建议新增：

- `MidMemorySnapshot`
- `MidMemoryUpdateService`
- `MidMemoryRepository`

建议字段：

```java
public class MidMemorySnapshot {
    private String userId;
    private String summary;
    private String sourceType;
    private String sourceId;
    private Double importance;
    private LocalDateTime validUntil;
}
```

## 16.5 Match Profile 的组装规则

`match_profile` 不是新的认知层，而是匹配场景下的聚合结果。

建议组装顺序：

1. 读取 `lifeGraph` 的长期摘要
2. 读取 `user-persona` 的稳定偏好摘要
3. 读取 `mid-memory` 的近期状态摘要
4. 按统一模板拼成 `profile_text`

推荐模板结构：

```text
长期结构：
...

稳定偏好：
...

近期状态：
...
```

### `MatchProfileAssembler` 的建议流程

```java
public MatchProfile aggregate(String userId) {
    String lifeGraphSummary = ...;
    String personaSummary = ...;
    String midMemorySummary = ...;
    String profileText = ...;
    return new MatchProfile(...);
}
```

## 16.6 `MatchServiceImpl` 的重构清单

建议把当前单体逻辑拆成以下步骤：

### Step 1：加载候选用户

- 从 `userService.getMatchEnabledUsers()` 获取候选范围

### Step 2：确保用户画像存在

- 若 `match_profile` 过期或缺失，先触发重新组装

### Step 3：向量召回

- 使用 `yusi_match_profile` 做 Top K 召回

### Step 4：业务过滤

- 过滤：
  - 今天已匹配
  - 历史已匹配
  - 用户主动关闭匹配

### Step 5：Agent 精排

- 将候选的三类认知摘要交给 Match Prompt

### Step 6：结果落库

- 保存 `SoulMatch`
- 记录 `reason / timingReason / iceBreaker`

## 16.7 推荐的代码落地顺序

为了降低风险，推荐按以下顺序实施：

### 第一批

- 调整 `DiaryServiceImpl`
- 引入 `CognitionIngestCommand`
- 新增 `DiaryCognitionIngestEvent`
- 新增 `AgentCognitionOrchestrator`

### 第二批

- 将 `MemoryCompressionService` 接到统一认知链路
- 新增 `ChatCognitionIngestEvent`
- 新增 `MidMemoryUpdateService`

### 第三批

- 接入 `emotion-plaza`
- 新增 `EmotionPlazaCognitionIngestEvent`
- 新增 `EmotionPlazaCognitionIngestService`

### 第四批

- 新增 `match_profile`
- 新增 `MatchProfileAssembler`
- 新增 Milvus 集合 `yusi_match_profile`

### 第五批

- 重写 `MatchServiceImpl`
- 替换随机匹配与原文拼 Prompt

## 16.8 最小可行版本建议

如果希望先做 MVP，建议只做以下闭环：

1. `Diary -> AgentCognitionOrchestrator`
2. `Chat Summary -> AgentCognitionOrchestrator`
3. 写入：
   - `lifeGraph`
   - `user-persona`
   - `mid-memory`
4. 组装 `match_profile`
5. 用 Milvus 做候选召回
6. 用 LLM 做精排

先不要在第一期做复杂的广场信号融合和过多的认知细分。

## 16.9 成功标准

本次重构完成后，应满足以下标准：

1. `Diary`、`AI Chat`、`Emotion Plaza` 都进入统一 Agent 输入层
2. `lifeGraph`、`user-persona`、`mid-memory` 的边界清晰
3. `MatchServiceImpl` 不再直接读取原始日记正文做匹配
4. 异步链路不再常规回源解密原文
5. `match` 成为统一 Agent 的认知输出，而不是孤立功能逻辑

## 17. 可直接开工编码版

本章目标是将方案进一步细化到“开发任务可直接拆分”的程度。

本章包括：

1. 建议包结构
2. 建议新增文件清单
3. Entity / Repository / Service 对应关系
4. Prompt 输出 JSON Schema
5. 关键方法改造点

## 17.1 建议包结构

建议在现有包结构基础上新增以下目录：

```text
com.aseubel.yusi
├─ common
│  └─ event
│     ├─ DiaryCognitionIngestEvent.java
│     ├─ ChatCognitionIngestEvent.java
│     ├─ EmotionPlazaCognitionIngestEvent.java
│     ├─ AgentCognitionUpdatedEvent.java
│     └─ MatchProfileUpdatedEvent.java
├─ pojo
│  ├─ dto
│  │  ├─ cognition
│  │  │  ├─ CognitionIngestCommand.java
│  │  │  ├─ LifeGraphLongTermSignal.java
│  │  │  ├─ UserPersonaSignal.java
│  │  │  ├─ MidMemorySignal.java
│  │  │  └─ MatchProfileAggregate.java
│  │  └─ match
│  │     ├─ MatchRerankContext.java
│  │     └─ MatchRerankResult.java
│  └─ entity
│     ├─ MidMemorySnapshot.java
│     └─ MatchProfile.java
├─ repository
│  ├─ MidMemorySnapshotRepository.java
│  └─ MatchProfileRepository.java
├─ service
│  ├─ cognition
│  │  ├─ AgentCognitionOrchestrator.java
│  │  ├─ DiaryCognitionIngestService.java
│  │  ├─ ChatCognitionIngestService.java
│  │  └─ EmotionPlazaCognitionIngestService.java
│  ├─ persona
│  │  └─ UserPersonaUpdateService.java
│  ├─ memory
│  │  └─ MidMemoryUpdateService.java
│  └─ match
│     ├─ MatchProfileAssembler.java
│     ├─ MatchRecallService.java
│     └─ MatchRerankService.java
```

说明：

- 不要求一步全部建完
- 但建议按“事件、DTO、Entity、Repository、Service”分层，避免继续把逻辑堆进 `impl`

## 17.2 建议新增文件清单

### 第一批必须新增

- `CognitionIngestCommand.java`
- `DiaryCognitionIngestEvent.java`
- `ChatCognitionIngestEvent.java`
- `AgentCognitionOrchestrator.java`
- `MatchProfile.java`
- `MatchProfileRepository.java`
- `MatchProfileAssembler.java`

### 第二批建议新增

- `MidMemorySnapshot.java`
- `MidMemorySnapshotRepository.java`
- `MidMemoryUpdateService.java`
- `UserPersonaUpdateService.java`

### 第三批按广场接入时新增

- `EmotionPlazaCognitionIngestEvent.java`
- `EmotionPlazaCognitionIngestService.java`

## 17.3 Entity 与 Repository 映射建议

### A. `MatchProfile`

建议映射：

- Entity：`MatchProfile`
- Repository：`MatchProfileRepository`
- Service：`MatchProfileAssembler`

建议字段：

```java
@Entity
@Table(name = "match_profile")
public class MatchProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", unique = true, nullable = false)
    private String userId;

    @Column(name = "profile_text", columnDefinition = "TEXT")
    private String profileText;

    @Column(name = "life_graph_summary", columnDefinition = "TEXT")
    private String lifeGraphSummary;

    @Column(name = "persona_summary", columnDefinition = "TEXT")
    private String personaSummary;

    @Column(name = "mid_memory_summary", columnDefinition = "TEXT")
    private String midMemorySummary;

    @Column(name = "version")
    private Long version;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
```

Repository 建议最少包含：

```java
public interface MatchProfileRepository extends JpaRepository<MatchProfile, Long> {
    Optional<MatchProfile> findByUserId(String userId);
}
```

### B. `MidMemorySnapshot`

建议映射：

- Entity：`MidMemorySnapshot`
- Repository：`MidMemorySnapshotRepository`
- Service：`MidMemoryUpdateService`

建议字段：

```java
@Entity
@Table(name = "mid_memory_snapshot")
public class MidMemorySnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "source_id")
    private String sourceId;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "importance")
    private Double importance;

    @Column(name = "valid_until")
    private LocalDateTime validUntil;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
```

Repository 建议最少包含：

```java
public interface MidMemorySnapshotRepository extends JpaRepository<MidMemorySnapshot, Long> {
    List<MidMemorySnapshot> findTop10ByUserIdAndValidUntilAfterOrderByCreatedAtDesc(
            String userId, LocalDateTime now);
}
```

## 17.4 DTO 设计建议

### A. `MatchProfileAggregate`

用途：

- 作为 `MatchProfileAssembler` 内部聚合结果 DTO

建议字段：

```java
public class MatchProfileAggregate {
    private String userId;
    private String lifeGraphSummary;
    private String personaSummary;
    private String midMemorySummary;
    private String profileText;
}
```

### B. `MatchRerankContext`

用途：

- 作为传给 Match 精排服务的上下文对象

建议字段：

```java
public class MatchRerankContext {
    private String targetUserId;
    private String candidateUserId;
    private String targetLifeGraphSummary;
    private String targetPersonaSummary;
    private String targetMidMemorySummary;
    private String candidateLifeGraphSummary;
    private String candidatePersonaSummary;
    private String candidateMidMemorySummary;
}
```

### C. `MatchRerankResult`

用途：

- 作为 LLM 精排后的结构化结果

建议字段：

```java
public class MatchRerankResult {
    private Boolean resonance;
    private Integer score;
    private String reason;
    private String timingReason;
    private String iceBreaker;
}
```

## 17.5 Prompt 输出 Schema 建议

为了避免继续依赖“自由文本 + 正则解析”，建议 Prompt 输出尽量结构化。

### A. 统一认知抽取 Prompt 输出

建议输出 JSON：

```json
{
  "lifeGraphSignals": [
    {
      "summary": "用户长期重视稳定关系，对亲密连接非常谨慎",
      "category": "RELATION_PATTERN",
      "importance": 0.92
    }
  ],
  "personaSignals": [
    {
      "interests": "摄影, 电影",
      "tone": "温柔陪伴",
      "interactionStyle": "慢热",
      "socialBoundary": "高边界感",
      "confidence": 0.88
    }
  ],
  "midMemorySignals": [
    {
      "summary": "用户近期在职业选择上有明显犹豫",
      "importance": 0.85,
      "validHours": 168
    }
  ]
}
```

### B. Match Prompt 输出

建议输出 JSON：

```json
{
  "resonance": true,
  "score": 86,
  "reason": "双方都重视稳定关系，并且都更偏向慢热表达，容易形成低压共鸣。",
  "timingReason": "两人当前都处于需要被理解但不希望高压社交推进的阶段。",
  "iceBreaker": "也许你们都不是会很快打开自己的人，但正因为如此，反而更容易理解彼此的节奏。"
}
```

### C. Emotion Plaza 输入理解 Prompt 输出

建议输出 JSON：

```json
{
  "shouldAffectPersona": false,
  "shouldAffectLifeGraph": false,
  "midMemorySignal": {
    "summary": "用户近期在公开表达中多次呈现疲惫与抽离感",
    "importance": 0.62,
    "validHours": 72
  }
}
```

## 17.6 `DiaryServiceImpl` 的逐点改造建议

建议改造目标：

- 让 `DiaryServiceImpl` 成为日记进入统一 Agent 的唯一业务入口

具体改造点：

1. 在 `addDiary()` 成功保存后发布 `DiaryCognitionIngestEvent`
2. 事件载荷中传入 `maskedText`，而不是只传 `diaryId`
3. 保留原始加密落库逻辑
4. 保存后尽快清理 `plainContent`

建议伪代码：

```java
public Diary addDiary(Diary diary) {
    // 1. 加密写库
    // 2. 脱敏得到 maskedText
    // 3. publish DiaryCognitionIngestEvent
    // 4. 返回结果
}
```

## 17.7 `MemoryCompressionService` 的逐点改造建议

建议改造目标：

- 让聊天摘要自然进入统一认知层

具体改造点：

1. 摘要完成后执行脱敏
2. 构造 `CognitionIngestCommand`
3. 发布 `ChatCognitionIngestEvent`
4. 由统一编排器决定更新 `lifeGraph / user-persona / mid-memory`

## 17.8 `MatchServiceImpl` 的逐方法改造建议

### 当前需要删除或废弃的逻辑

- 随机洗牌作为主匹配策略
- 基于原始近期日记内容直接做打分
- 以“一个整数分数”作为唯一 AI 输出

### 推荐保留的逻辑

- 今日匹配次数控制
- 历史匹配排重
- 匹配开关判断

### 推荐新增的方法

```java
private MatchProfile ensureMatchProfile(String userId);
private List<String> recallCandidates(String userId, MatchProfile profile);
private MatchRerankResult rerank(String userId, String candidateUserId);
private SoulMatch persistMatchResult(...);
```

## 17.9 开发任务拆分建议

为了方便直接进入开发，可以拆成以下任务单：

### Task 1

新增认知基础 DTO 与事件：

- `CognitionIngestCommand`
- `DiaryCognitionIngestEvent`
- `ChatCognitionIngestEvent`

### Task 2

改造 `DiaryServiceImpl`，让日记接入统一认知链路

### Task 3

改造 `MemoryCompressionService`，让聊天摘要接入统一认知链路

### Task 4

新增 `MidMemorySnapshot` 相关 Entity / Repository / Service

### Task 5

新增 `MatchProfile` 相关 Entity / Repository / Assembler

### Task 6

新增 `AgentCognitionOrchestrator`

### Task 7

重写 `MatchServiceImpl`

### Task 8

补充 `emotion-plaza` 接入逻辑

## 17.10 建议的提交顺序

推荐按以下提交顺序推进，便于回滚和验证：

1. `feat: add cognition ingest command and events`
2. `feat: connect diary pipeline to unified cognition flow`
3. `feat: connect chat summary pipeline to unified cognition flow`
4. `feat: add mid memory snapshot persistence`
5. `feat: add match profile aggregation and storage`
6. `refactor: rewrite match service based on unified agent cognition`
7. `feat: connect emotion plaza input to cognition flow`
