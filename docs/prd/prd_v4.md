# 产品需求文档 (PRD): "Yusi - 灵魂叙事" (v4.0)

## 项目

Yusi (v4.0) — Agent Awakening

## 日期

2026年6月2日

---

## 0. 版本背景

### 0.1 已走过的路

| 版本 | 主题 | 核心交付 |
|:---|:---|:---|
| v1.0 | 灵魂叙事 MVP | 情景室、AI 日记、基础匹配 |
| v2.0 | 深度连接 | 灵魂广场、零知识加密（双密钥模式） |
| v3.0 | 人生图谱 | GraphRAG 知识图谱、实体关系抽取、时空足迹、情感图谱 |
| 统一 Agent 重构 | 架构升维 | 三层认知结构（lifeGraph / user-persona / mid-memory）、统一认知编排器、匹配画像聚合、Milvus 混合召回 + Agent 精排 |

### 0.2 当前架构状态

v4.0 的起点是一个已经完成"统一 Agent"架构重构的平台：

```
用户表达层          Agent 认知层           行为输出层
┌──────────┐      ┌──────────────┐      ┌──────────┐
│  diary   │━━━━━▶│  lifeGraph    │      │  ai-chat │
│  ai-chat │━━━━━▶│  user-persona │━━━━━▶│  match   │
│  plaza   │━━━━━▶│  mid-memory   │      │  insight │
└──────────┘      └──────────────┘      └──────────┘
```

- ✅ 三类输入源（diary / ai-chat / emotion-plaza）已接入统一认知编排器
- ✅ AgentCognitionOrchestrator 已实现，路由结果到三类认知结构
- ✅ MatchProfile 聚合与 Milvus 混合召回（Dense + Sparse + RRF）已上线
- ✅ 匹配精排（resonance / score / reason / timingReason / iceBreaker）已上线
- ✅ MCP Server 作为对外开放接口已就绪

### 0.3 为什么要做 v4.0

v3.0 + 统一 Agent 重构解决了"平台如何认知用户"的问题。但一个真正的 agent 智能体，不能只被动接收输入和分析——它需要：

- **主动**：在合适的时机主动发起有意义的互动
- **有性格**：拥有稳定、可信赖的"人格"，而非每次对话都是空白状态
- **能成长**：随陪伴时间增长而深化对用户的理解，展现出"越来越懂你"的感觉
- **会连接**：不仅做一对一匹配，还能感知群体中的共鸣可能

> **v4.0 的核心命题：让 Agent 从"分析工具"进化为"有主体性的数字知己"。**

---

## 1. 🎯 v4.0 战略目标

### 1.1 三条主线

| 主线 | 目标 | 一句话描述 |
|:---|:---|:---|
| **Agent 觉醒** | Agent 具备主动性、人格化、成长感 | "这个 AI 真的越来越懂我了" |
| **连接深化** | 匹配后体验闭环 + 多维度共鸣发现 | "不止是匹配，更是持续的陪伴" |
| **平台开放** | MCP 生态化，让外部 AI 也能接入 Yusi | "Yusi 的记忆能力可以赋能任何 AI" |

### 1.2 不做的事

- 不做社交网络（好友系统、动态流、评论）
- 不做内容社区运营（不追求 DAU/留存指标，追求连接质量）
- 不做视频/直播/语音房
- 不出卖用户数据做广告

---

## 2. 🚀 v4.0 功能范围

### Epic 8: "Agent 觉醒" — 主动型 AI 知己

**目标**：让 Agent 从"被动响应"进化为"主动陪伴"，具备人格连续性、主动关怀和成长轨迹。

| ID | 功能特性 | 描述 | 优先级 |
|:---|:---|:---|:---|
| **F8.1** | Agent 人格系统 | ✅ 已实现：AgentPersonaConfig 实体 + API + 对话人格注入 | P0 |
| **F8.2** | 主动问候与洞察 | ✅ 已实现：AgentProactiveService 每小时扫描 + 频率控制 + 静默时段 | P0 |
| **F8.3** | 周期性回顾 | ✅ Agent 每周/每月生成"灵魂周报/月报"，回顾用户的情感变化、重要事件和成长轨迹 | P0 |
| **F8.4** | 纪念日与触发器 | Agent 记住用户的重要日期和事件，在恰当时机提及（"去年今日你..."） | P1 |
| **F8.5** | Agent 成长可见化 | ✅ 用户可看到 Agent"对自己的了解程度"的可视化指标，随使用深入而变化 | P1 |
| **F8.6** | 情绪感知与调节 | Agent 能感知用户当前情绪状态（从对话中推断），调整回复策略（深度/轻松/陪伴） | P1 |

#### F8.1 Agent 人格系统 — 详细说明

当前问题：每次 AI 对话是独立的 chat session，用户感知不到 Agent 的"人格连续性"。

设计要求：

```
Agent 人格 = 基础人格（系统定义）+ 用户适配层（随互动演化）
```

**基础人格配置**：
- 默认定位：温柔、善解人意、有边界感的知己
- 可选风格：更活泼 / 更沉静 / 更理性
- 核心约束：不伪装人类、不越界、不对用户做评判

**用户适配层**（由 user-persona 驱动）：
- 根据用户偏好自动调整语气和陪伴风格
- 用户可在设置中微调（响应频率、语气温度、话题边界）
- 变更日志记录在 user-persona 中

**实现要点**：
- Agent 人格通过 system prompt + persona summary 注入每次对话
- 不再每次对话从空白开始，而是从 `user-persona` 加载"我对这个用户的了解"
- Chat memory 窗口内始终包含简短的 persona context

#### F8.2 主动问候与洞察 — 详细说明

当前问题：Agent 从不主动联系用户，只能等用户打开对话。

设计要求：

**触发条件**（由定时任务 + mid-memory 驱动）：
- 用户超过 N 天未互动（N 可配置，默认 3 天）
- 用户 mid-memory 显示情绪低落或处于转变期
- 有新的匹配推荐等待用户查看
- 纪念日或重要节点

**主动问候形式**：
- 站内通知（notification 表已存在）
- 问候内容由 Agent 根据 mid-memory 生成，非模板化文案
- 用户可选择关闭主动问候

**频率控制**：
- 默认每周最多 1 次主动问候
- 用户可在设置中调整频率（关闭 / 低频 / 正常）

#### F8.3 周期性回顾 — 详细说明

当前问题：用户写了很多日记、聊了很多天，但没有"回顾"入口来感知自己的变化。

**灵魂周报**（轻量版）：
- 每周生成，覆盖本周情绪趋势、重要话题、与 Agent 的互动亮点
- 由 Agent 基于 mid-memory + 本周 diary 生成
- 在 AI 对话中以自然语言呈现（"想看看你这周的灵魂周报吗？"）

**灵魂月报**（深度版）：
- 每月生成，包含情感变化曲线、重要事件回顾、成长洞察
- 可选择性分享到广场（匿名）

**实现要点**：
- 周报/月报通过定时任务 + LLM 生成
- 存储为结构化内容，支持前端渲染
- 生成后通过通知提醒用户

---

### Epic 9: "连接深化" — 匹配后体验与多维共鸣

**目标**：匹配不是终点，而是连接的开始。深化匹配后的体验闭环，引入更多维度的共鸣发现。

| ID | 功能特性 | 描述 | 优先级 |
|:---|:---|:---|:---|
| **F9.1** | 匹配后引导 | ✅ 已实现：ConnectionGuideService 生成破冰话题 + 情景推荐 | P0 |
| **F9.2** | 共鸣信号 | ✅ 已实现：ResonanceSignal 实体 + API + 双向共鸣检测 | P1 |
| **F9.3** | 情景室匹配入口 | 匹配成功的双方可一键创建私人情景室，通过结构化情景破冰 | P1 |
| **F9.4** | 群体共鸣发现 | Agent 从广场中发现"正在经历相似阶段"的 3-5 人小群体，提供匿名小群连接 | P2 |
| **F9.5** | 匹配反馈循环 | ✅ 已实现：MatchFeedback 记录 + 精排偏好上下文注入 | P0 |

#### F9.1 匹配后引导 — 详细说明

当前问题：匹配成功后直接进入匿名聊天，用户面对空白对话框不知说什么。

**引导内容**：
- 基于双方共鸣原因的破冰话题建议（如"聊聊你们最近都在思考的那个问题？"）
- 可选的情景室邀请（由 Agent 推荐 1-2 个适合双方的情景）
- "先匿名聊聊，感觉对了再开启情景室"的二阶段模式

**交互流程**：

```
匹配成功 → 展示推荐信 + 破冰引导
         → 用户选择：① 直接匿名聊天
                     ② 进入情景室（Agent 推荐情景）
                     ③ 先看看，稍后决定
```

#### F9.5 匹配反馈循环 — 详细说明

当前问题：匹配结果不会反馈给 Agent 认知层，Agent 不知道自己推荐得好不好。

**反馈信号**：
- 用户接受匹配 → 正面信号，增强该类匹配的偏好权重
- 用户跳过匹配 → 负面信号，降低该类匹配
- 匹配后互动深度（消息条数、互动时长）→ 质量信号
- 匹配后互相拉黑/举报 → 强负面信号

**实现要点**：
- 反馈写入 user-persona 的匹配偏好维度
- 精排 prompt 中包含"用户历史匹配偏好"上下文
- 不改变认知结构本身，只调整匹配时的权重

---

### Epic 10: "多模态感知" — 超越文本的理解

**目标**：Agent 的输入不再局限于文本，支持语音和图像的理解。

| ID | 功能特性 | 描述 | 优先级 |
|:---|:---|:---|:---|
| **F10.1** | 语音日记 | 用户可通过语音输入日记，Agent 转写后理解并存入认知层 | P1 |
| **F10.2** | 图片日记 | 日记支持附加图片，Agent 理解图片内容并纳入认知 | P1 |
| **F10.3** | 语音对话 | AI 对话支持语音输入/输出（ASR + TTS），降低输入门槛 | P2 |

#### F10.1 语音日记 — 设计要点

- 前端录音 → 上传至 OSS → 调用 ASR（如 FunASR，已有文档）→ 转写文本
- 转写文本进入正常日记写入链路（加密 + 认知摄取）
- 语音原文件保留，支持回听

#### F10.2 图片日记 — 设计要点

- 图片上传至 OSS，日记关联图片 URL
- 在认知摄取阶段，调用多模态模型（如 Qwen-VL）理解图片内容
- 图片理解结果（场景描述、情绪氛围）作为附加上下文进入 lifeGraph / mid-memory
- 注意隐私：图片内容理解后原始图片不参与 LLM 精排

---

### Epic 11: "认知进化" — 记忆系统升级

**目标**：三类认知结构已经建立边界，但记忆的质量、融合度和时效性需要提升。

| ID | 功能特性 | 描述 | 优先级 |
|:---|:---|:---|:---|
| **F11.1** | mid-memory 持久化 | ✅ 已实现：MidTermMemory 持久化 + validUntil 字段 + DB 迁移 | P0 |
| **F11.2** | 记忆衰减机制 | ✅ 已实现：30 天 TTL + MatchProfileAssembler/ContextBuilderService 过滤过期记忆 | P0 |
| **F11.3** | 认知冲突检测 | ✅ 当新输入与已有 lifeGraph / user-persona 存在矛盾时，Agent 标记冲突并请求"澄清" | P1 |
| **F11.4** | 跨源记忆融合 | ✅ diary + ai-chat + emotion-plaza 中多次出现的同一主题自动合并，而非分散存储 | P1 |
| **F11.5** | 遗忘机制 | 用户可主动要求 Agent"忘记"某段记忆（从认知层删除，原始日记保留） | P2 |

#### F11.1 mid-memory 持久化 — 详细说明

当前问题：MidMemoryUpdateService 已存在但未见 MidMemorySnapshot 实体，中期记忆可能只在内存或混在其他表中。

**要求**：
- 创建 `mid_memory_snapshot` 表（字段参考 perf_match_0413.md §17.3.B）
- MidMemoryUpdateService 写入 snapshot 而非仅维护内存
- 匹配画像聚合时从 DB 读取最新、有效的 mid-memory
- 支持按 `valid_until` 自动过滤过期记录

#### F11.3 认知冲突检测 — 详细说明

场景示例：
- 用户日记中多次写到"不喜欢社交"
- 但近期广场投递和聊天中频繁表达"想找人聊天"
- Agent 检测到冲突 → 在合适时机问："最近似乎有些变化？你之前提到不喜欢社交..."

**实现要点**：
- 在 CognitionRoutingService 中增加冲突检测逻辑
- 对比新输入与已有 user-persona / lifeGraph 的语义一致性
- 冲突标记存储为 mid-memory 中的特殊类型信号
- Agent 在对话中自然地"注意到变化"，而非机械报告冲突

---

### Epic 12: "平台开放" — MCP 生态化

**目标**：MCP 不仅是 Yusi 对外的接口，更是一个可扩展的平台，允许第三方开发工具接入。

| ID | 功能特性 | 描述 | 优先级 |
|:---|:---|:---|:---|
| **F12.1** | 开发者注册与 API Key 管理 | 提供开发者门户，支持注册、创建应用、管理 API Key | P1 |
| **F12.2** | MCP 工具市场 | 允许开发者提交自定义 MCP 工具（如第三方分析、可视化），用户可选择安装 | P2 |
| **F12.3** | 使用配额与计费 | API 调用配额管理，防止滥用 | P2 |
| **F12.4** | MCP 工具权限控制 | 用户可控制哪些外部 MCP 工具可访问自己的数据、访问哪些维度的数据 | P1 |

#### F12.1 API Key 管理 — 设计要点

- 在前端设置页面增加"开发者"入口
- 支持创建/吊销 API Key
- API Key 与权限范围绑定（只读记忆 / 可写日记 / 匹配查询等）
- 调用日志可查看

---

## 3. 🏗️ 架构要求

### 3.1 需要新增的服务

| 服务 | 职责 | 关联 Epic |
|:---|:---|:---|
| `AgentProactiveService` | 管理主动问候的触发条件与频率控制 | F8.2 |
| `SoulReportGenerator` | 生成周报/月报 | ✅ F8.3 |
| `ConnectionGuideService` | 匹配后破冰引导与情景推荐 | ✅ F9.1 |
| `ResonanceSignalService` | 广场共鸣信号管理 | ✅ F9.2 |
| `MatchFeedbackService` | 收集与处理匹配反馈信号 | ✅ F9.5 |
| `CognitiveConflictDetector` | 检测新旧认知冲突 | ✅ F11.3 |
| `MidMemoryFusionService` | 跨源记忆融合 | ✅ F11.4 |
| `AgentGrowthService` | Agent 成长可见化 | ✅ F8.5 |

### 3.2 需要新增的实体

| 实体 | 表名 | 说明 |
|:---|:---|:---|
| `MidMemorySnapshot` | `mid_memory_snapshot` | 持久化中期记忆快照 |
| `AgentPersonaConfig` | `agent_persona_config` | 用户对 Agent 人格的配置 |
| `SoulReport` | `soul_report` | 周报/月报内容 |
| `ResonanceSignal` | `resonance_signal` | 广场共鸣信号记录 |
| `MatchFeedback` | `match_feedback` | 匹配反馈信号 |
| `DeveloperApp` | `developer_app` | 开发者注册的应用 |
| `CognitiveConflict` | `cognitive_conflict` | 认知冲突标记 |

### 3.3 需要重构的部分

| 重构项 | 说明 | 原因 |
|:---|:---|:---|
| `MatchServiceImpl.getMatchStatus()` | 修复 nextMatchTime 为动态计算 | 硬编码"每日凌晨 2:00"已过时 |
| `ChatMemoryStore` | 对话开始时注入 persona context | 当前每次对话从零开始，无 Agent 人格连续性 |
| `DiaryController` 事件发布 | 确保所有事件在 Service 层发布 | 遵循架构分层原则（v3 重构指南中提出但需复查） |
| `EmotionPlaza` 认知接入 | 新建 `EmotionPlazaCognitionIngestService` | 事件已定义但缺少专门的 ingest 服务 |
| `MidMemoryUpdateService` | 从内存维护升级为 DB 持久化 | 对应 F11.1 |
| Prompt 模板管理 | 统一管理所有 Prompt，支持版本化和 A/B 测试 | 当前 Prompt 散落在 Assistant 接口和代码中 |

### 3.4 不做的大重构

- 不迁移数据库（保持 MySQL + Milvus + Redis）
- 不更换 AI 框架（保持 LangChain4j）
- 不动 MCP Server（Go 代码不动）
- 不改变加密架构（AES/GCM + 双密钥模式保持）

---

## 4. 📐 关键设计决策

### 4.1 Agent 人格 vs 用户隐私

Agent 越了解用户，就越能提供好的陪伴——但这也意味着更多用户信息被用于 AI 理解。

**原则**：
- 人格系统基于脱敏后的认知层（lifeGraph / user-persona / mid-memory），不直接使用原始日记
- 用户始终能看到"Agent 了解了我什么"并可请求删除特定认知
- 加密密钥管理保持不变：自定义密钥 + 不备份 = 零 AI 能力（不做改动）

### 4.2 主动性的边界

Agent 的主动性不能变成骚扰。

**边界**：
- 最大频率：每周 1 次主动问候（默认），用户可调
- 不主动提及未在日记/聊天中出现过的外部信息
- 不在深夜发送通知（遵循用户设置的静默时段）
- 用户可随时关闭所有主动功能

### 4.3 匹配质量 vs 匹配数量

当前每周一次匹配（周五晚 8 点），每次为每个用户推荐至多一个匹配对象。这是一个经过深思熟虑的设计选择。

**v4.0 保持不变**：匹配频率不变（每周一次），但提升每次匹配的质量和后体验。

---

## 5. 📋 实施路线图

### Phase 1: Agent 觉醒（核心）— ✅ 已完成

1. ✅ **F11.1 mid-memory 持久化** — MidTermMemory + validUntil 字段，MidMemoryUpdateService 写入 DB
2. ✅ **F11.2 记忆衰减机制** — valid_until 30 天 TTL，MatchProfileAssembler + ContextBuilderService 过滤过期记忆
3. ✅ **F8.1 Agent 人格系统** — AgentPersonaConfig 实体 + API + ContextBuilderService 注入人格到对话
4. ✅ **F8.2 主动问候与洞察** — AgentProactiveService 每小时扫描 + 静默时段 + 频率控制
5. ✅ **重构 ChatMemoryStore 注入 persona context** — injectMidMemoryContext + injectAgentPersona
6. ✅ **重构 MatchServiceImpl.getMatchStatus()** — nextMatchTime "每周五晚 20:00"
7. ✅ **runDailyMatching → runWeeklyMatching** 全局重命名
8. ✅ **DB 迁移** — V20260603 脚本 + init.sql 补全

**Phase 1 交付物**：用户能感受到 Agent "有性格"且"会主动关心自己"

### Phase 2: 连接深化 — ✅ 已完成

1. ✅ **F9.1 匹配后引导** — ConnectionGuideService 生成破冰话题 + MatchRecommendationResponse 新增 iceBreakers/suggestedScenario
2. ✅ **F9.5 匹配反馈循环** — MatchFeedback 实体 + MatchFeedbackService + 精排 prompt 注入偏好上下文
3. ✅ **F9.2 共鸣信号** — ResonanceSignal 实体 + API（发送/接收/未读/已读）+ 双向共鸣检测
4. **F9.3 情景室匹配入口**（P1，移至 Phase 3）

**Phase 2 交付物**：匹配不再是"推荐-接受-聊天"的线性流程，而是有引导、有反馈的闭环

### Phase 3: 认知进化 — 预计 2-3 周

1. ✅ **F8.3 周期性回顾**（P0）
2. ✅ **F11.3 认知冲突检测**（P1）
3. ✅ **F11.4 跨源记忆融合**（P1）
4. ✅ **F8.5 Agent 成长可见化**（P1）
5. 重构 `EmotionPlazaCognitionIngestService`

**Phase 3 交付物**：Agent 的"理解深度"可被用户感知

### Phase 4: 平台与多模态 — 预计 3-4 周

1. **F10.1 语音日记**（P1）
2. **F10.2 图片日记**（P1）
3. **F12.1 开发者注册与 API Key 管理**（P1）
4. **F12.4 MCP 工具权限控制**（P1）

**Phase 4 交付物**：输入模态扩展 + MCP 平台化起步

### Phase 5: 深度特性 — 按需

1. **F9.4 群体共鸣发现**（P2）
2. **F10.3 语音对话**（P2）
3. **F12.2 MCP 工具市场**（P2）
4. **F11.5 遗忘机制**（P2）
5. **F8.4 纪念日与触发器**（P1→P2 降级）

---

## 6. 🔧 即刻修复（不依赖 Phase）

以下问题在 v4.0 开发启动时即可修复，不需要等待完整 Phase：

| 问题 | 位置 | 状态 |
|:---|:---|:---|
| `getMatchStatus().nextMatchTime` 写死"每日凌晨 2:00" | `MatchServiceImpl.java:689` | ✅ 已修复 |
| `EmotionPlazaCognitionIngestService` 缺失 | `SoulPlazaServiceImpl.java` | ✅ 无需新建（事件发布已内联在 publishEmotionPlazaEvent） |
| `runDailyMatching()` 方法名过时 | `MatchServiceImpl.java:82` | ✅ 已重命名为 runWeeklyMatching |
| Prompt 散落各处 | `MatchAssistant.java` 等 | 🔧 后续重构（Phase 3 统一迁移至 PromptManager） |

---

## 7. ✅ 成功标准

v4.0 完成后应满足：

1. **Agent 有"人格"**：用户在对话中能感知 Agent 的稳定性格，而非每次"失忆"
2. **Agent 会"主动"**：在合适时机 Agent 主动发起关怀，用户感受到被关注而非骚扰
3. **匹配有"闭环"**：匹配 → 引导 → 互动 → 反馈形成完整闭环
4. **记忆有"生命"**：mid-memory 持久化、衰减、融合，不再丢失或混乱
5. **平台可"开放"**：外部开发者可通过 MCP 协议接入 Yusi 的能力
6. **认知可"解释"**：用户能看到 Agent 对自己的了解，且有权管理这些认知

---

## 8. 📎 附录

### A. 术语表

| 术语 | 定义 |
|:---|:---|
| Agent | 指 Yusi 平台本身——一个统一的 AI 智能体 |
| lifeGraph | Agent 对用户长期结构性信息的认知（人物、地点、事件、关系） |
| user-persona | Agent 对用户稳定偏好和风格的认知（兴趣、语气、边界） |
| mid-memory | Agent 对用户近期阶段性状态的认知（情绪、困扰、阶段） |
| 认知层 | lifeGraph + user-persona + mid-memory 的总称 |
| 匹配画像 | 从三类认知聚合出的、用于匹配场景的结构化摘要 |
| 共鸣 | 两个用户在精神频率上的匹配，不同于兴趣标签的相似 |

### B. 关联文档

- [开发哲学与产品理念](../design/philosophy.md)
- [后端详细设计](../design/backend-design.md)
- [前端详细设计](../design/frontend-design.md)
- [统一 Agent 认知架构与匹配重构](./perf_match_0413.md)

---

<p align="center">
  <i>v4.0 — Agent Awakening</i><br>
  <i>以 AI 为中心 · 认识自我 · 遇见同频</i>
</p>
