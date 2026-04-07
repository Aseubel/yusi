# 本地NLP实体识别脱敏方案

## 1. 背景与问题

Yusi项目在调用外部大模型API时存在隐私泄露风险——服务端需要将明文内容发送给第三方LLM服务，敏感信息（人名、地名、电话、邮箱等）会暴露给第三方。

**解决思路**：引入本地NLP实体识别，在调用外部API前对敏感信息脱敏，API返回后还原，实现"敏感信息不出域"。

---

## 2. 技术选型：HanLP

| 特性 | 说明 |
|------|------|
| 纯Java实现 | 无需Python环境，与Spring Boot无缝集成 |
| 低资源消耗 | 单核CPU + 512MB内存即可运行 |
| 毫秒级延迟 | 适合实时处理场景 |
| 丰富的实体类型 | 支持人名(NR)、地名(NS)、机构名(NT)等 |
| 可扩展 | 支持自定义词典 |

**Maven依赖**：
```xml
<dependency>
    <groupId>com.hankcs</groupId>
    <artifactId>hanlp</artifactId>
    <version>portable-1.8.4</version>
</dependency>
```

---

## 3. 架构设计：Model 层统一拦截

### 3.1 核心思路

在 `ModelProxyFactory` 的动态代理中拦截所有 `ChatRequest`，对**所有消息**（SystemPrompt、聊天历史、用户消息、Tool结果）统一脱敏后发给外部LLM，收到响应后统一还原。

```
LangChain4j 组装完整 ChatRequest
    │  含 SystemPrompt + 聊天历史 + 用户消息 + Tool结果
    ▼
ModelProxyFactory.invokeWithMasking()
    │  extractAllText() → mask() → maskMessages()
    │  所有消息中的 "张三" → "[人名_1]"
    ▼
外部 LLM API  ← 只看到 [人名_1] [电话_1] [地名_1]
    │  LLM 回复: "你和[人名_1]在[地名_1]…"
    ▼
wrapStreamingHandler / unmaskChatResponse
    │  unmask: [人名_1] → "张三"
    ▼
LangChain4j / 用户  ← 收到原始文本
```

### 3.2 为什么选择 Model 层拦截

| 曾考虑的方案 | 问题 | 最终方案的优势 |
|-------------|------|---------------|
| 在 `@Tool` 方法中脱敏 + ThreadLocal 传递映射表 | Tool 异步执行，与 SSE 流不在同一线程 | 无线程依赖 |
| 只对 `searchMemories` 返回值脱敏 | 聊天历史中的同一实体未脱敏，语义割裂 | 所有消息统一脱敏 |
| 在 Controller 层拦截 | 无法拦截 LangChain4j 内部组装的消息 | 拦截最终 HTTP 调用前的完整 ChatRequest |

### 3.3 不脱敏的场景

| 场景 | 原因 |
|------|------|
| Embedding 向量化 | 脱敏会破坏语义向量质量，且 Embedding API 只返回数值向量 |
| 情景室对话 | 处理公共场景数据和匿名输入，无隐私风险 |

---

## 4. 实现详解

### 4.1 文件清单

| 文件 | 类型 | 说明 |
|------|------|------|
| `SensitiveEntityType.java` | NEW | 敏感实体类型枚举（6种） |
| `MaskResult.java` | NEW | 脱敏结果，含脱敏文本 + 内存映射表 |
| `SensitiveDataMaskService.java` | NEW | HanLP NER + 正则脱敏/还原服务 |
| `ModelProxyFactory.java` | MODIFY | Model 层动态代理拦截脱敏 |

### 4.2 实体类型定义

```java
public enum SensitiveEntityType {
    PERSON("nr", "人名"),
    LOCATION("ns", "地名"),
    ORGANIZATION("nt", "机构"),
    PHONE("PHONE", "电话"),     // 正则识别
    EMAIL("EMAIL", "邮箱"),     // 正则识别
    ID_CARD("ID_CARD", "身份证"); // 正则识别
}
```

占位符格式：`[类型名_序号]`，如 `[人名_1]`、`[电话_1]`。

### 4.3 脱敏服务

`SensitiveDataMaskService` 组合两种识别方式：
- **HanLP NER**：识别人名(nr)、地名(ns)、机构名(nt)
- **正则表达式**：识别手机号、邮箱、身份证号

```java
@Service
public class SensitiveDataMaskService {

    /** 脱敏：返回脱敏文本 + 映射表 */
    public MaskResult mask(String text) { ... }

    /** 还原：将占位符替换回原始值 */
    public String unmask(Map<String, String> mappingTable, String maskedText) { ... }
}
```

**映射表**存储为请求级内存 `Map<String, String>`（占位符 → 原始值），无需 Redis 持久化：
```json
{
  "[人名_1]": "张三",
  "[地名_1]": "北京大学",
  "[电话_1]": "13800138000"
}
```

**降级策略**：脱敏过程中任何异常都会被捕获，返回原文，不影响主业务流程。

### 4.4 Model 层拦截（核心）

在 `ModelProxyFactory.RoutingInvocationHandler` 中新增 `invokeWithMasking()` 方法：

**同步模型（ChatModel）**：
1. 提取 `ChatRequest` 中所有消息文本 → 拼接 → `mask()` 得到映射表
2. 用映射表替换每条消息中的敏感实体（反转映射：`原始值 → 占位符`）
3. 委托给实际模型
4. 对 `ChatResponse.aiMessage().text()` 调用 `unmask()` 还原

**流式模型（StreamingChatModel）**：
1-2 同上
3. 包装 `StreamingChatResponseHandler`：
   - `onPartialResponse(token)` → `unmask(token)` → 传给原始 handler
   - `onCompleteResponse(response)` → `unmask(response)` → 传给原始 handler
4. 委托给实际模型

**消息类型处理**：

| 消息类型 | 处理方式 |
|---------|---------|
| `SystemMessage` | 替换 `text()` |
| `UserMessage` | 替换 `TextContent`，保留 `ImageContent` |
| `AiMessage` | 替换 `text()`，保留 `toolExecutionRequests` |
| `ToolExecutionResultMessage` | 替换 `text()` |

---

## 5. 脱敏效果示例

```
输入: "今天和张三在北京大学见面了，他的电话是13800138000"

HanLP + 正则识别:
  - 张三 (NR - 人名)
  - 北京大学 (NS - 地名)
  - 13800138000 (正则 - 电话)

脱敏输出:
  "今天和[人名_1]在[地名_1]见面了，他的电话是[电话_1]"

LLM 回复(脱敏):
  "听起来你和[人名_1]在[地名_1]度过了愉快的时光！"

还原输出:
  "听起来你和张三在北京大学度过了愉快的时光！"
```

---

## 6. 风险与缓解

| 风险 | 缓解措施 |
|------|----------|
| 语义损失：脱敏后LLM理解能力下降 | 仅替换实体名称，保留上下文语义 |
| 识别漏检：HanLP未识别某些敏感信息 | 正则补充（电话/邮箱/身份证），持续优化 |
| 流式token中占位符被截断（如`[人名`和`_1]`分两次到达） | `unmask` 对单次 token 做全量字符串替换，即使部分匹配不影响最终输出 |
| 性能开销 | HanLP毫秒级处理，正则 O(n)，影响可忽略 |

---

## 变更记录

| 版本 | 日期 | 变更内容 |
|------|------|----------|
| v1.0 | 2025-04-06 | 初始设计（Redis映射表 + 逐模块集成） |
| v2.0 | 2025-04-07 | 重构为 Model 层统一拦截，去除 Redis 依赖，内存映射 |
