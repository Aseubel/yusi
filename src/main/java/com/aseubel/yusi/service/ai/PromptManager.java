package com.aseubel.yusi.service.ai;

import com.aseubel.yusi.pojo.entity.PromptTemplate;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.common.constant.PromptKey;
import com.aseubel.yusi.common.event.PromptUpdatedEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 提示词统一管理与降级缓存管理器
 * 提供基于事件的热更新能力
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromptManager {

    private final PromptService promptService;

    // Cache structure: Key -> Prompt template
    private final Map<String, String> promptCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("====== 开始初始化全局提示词管理器 (PromptManager) ======");
        for (PromptKey key : PromptKey.values()) {
            loadPrompt(key.getKey());
        }
        log.info("====== 全局提示词初始化完成 ======");
    }

    private String determineScope(String keyStr) {
        if ("chat".equals(keyStr) || "agent-persona".equals(keyStr) || "agent-proactive-greeting".equals(keyStr)) {
            return "global";
        }
        if ("logic".equals(keyStr)) {
            return "room";
        }
        if ("soul-match".equals(keyStr)) {
            return "match";
        }
        return "diary"; // Default scope
    }

    public void loadPrompt(String keyStr) {
        String dbPrompt = null;
        try {
            dbPrompt = promptService.getPrompt(keyStr, "zh-CN");
        } catch (Exception e) {
            log.warn("从数据库加载 Prompt [{}] 失败: {}", keyStr, e.getMessage());
        }

        if (dbPrompt != null && StrUtil.isNotBlank(dbPrompt) && dbPrompt.length() > 5) {
            promptCache.put(keyStr, dbPrompt);
            log.info("成功挂载提示词 [{}] - 来源: [Database], 长度: {} 字符", keyStr, dbPrompt.length());
            return;
        }

        // Try classpath fallback based on key
        String classpathFileName = "prompts/" + keyStr + "-prompt.txt";
        String contentToUse = null;
        String source = null;

        try {
            ClassPathResource resource = new ClassPathResource(classpathFileName);
            if (resource.exists()) {
                String cpPrompt = IoUtil.read(resource.getInputStream(), StandardCharsets.UTF_8);
                if (StrUtil.isNotBlank(cpPrompt)) {
                    contentToUse = cpPrompt;
                    source = "Classpath/" + classpathFileName;
                }
            }
        } catch (Exception e) {
            log.warn("从 Classpath 读取提示词文件 [{}] 发生异常", classpathFileName, e);
        }

        if (contentToUse == null) {
            contentToUse = getHardcodedFallback(keyStr);
            source = "Hardcoded Fallback";
        }

        promptCache.put(keyStr, contentToUse);
        log.info("成功挂载提示词 [{}] - 来源: [{}], 长度: {} 字符", keyStr, source, contentToUse.length());

        // Auto-initialize to Database so that it is visible in the admin dashboard!
        try {
            PromptTemplate template = PromptTemplate.builder()
                    .name(keyStr)
                    .template(contentToUse)
                    .version("v1")
                    .active(true)
                    .scope(determineScope(keyStr))
                    .locale("zh-CN")
                    .description("System auto-initialized default prompt")
                    .isDefault(true)
                    .priority(0)
                    .build();
            promptService.savePrompt(template, "SYSTEM");
            log.info("自动初始化 Prompt [{}] 至数据库", keyStr);
        } catch (Exception e) {
            // Already exists or saving failed, we can ignore this safely
            log.debug("尝试自动将 Prompt [{}] 写入数据库时跳过或失败: {}", keyStr, e.getMessage());
        }
    }

    private String getHardcodedFallback(String keyStr) {
        if (PromptKey.CHAT.getKey().equals(keyStr)) {
            return "你是 Yusi，一位温暖、富有同理心的 AI 灵魂伴侣。";
        }
        if (PromptKey.MEMORY_EXTRACT.getKey().equals(keyStr)) {
            return "请你作为一位极其敏锐的观察者，阅读以下用户与 AI 的对话记录。\n" +
                    "你的任务是：提取出这段对话中用户最重要的信息、经历、情绪或观点。\n\n" +
                    "提取规则：\n" +
                    "1. 请以第三人称（或\"用户\"）的视角进行客观总结。\n" +
                    "2. 仅保留能够构成长久回忆的**关键事件**，忽略寒暄、无关紧要的闲聊等。\n" +
                    "3. 提取结果必须精简、具体。\n\n" +
                    "输出格式（只输出总结的结果，不要其他的任何废话）：\n";
        }
        if (PromptKey.GRAPHRAG_EXTRACT.getKey().equals(keyStr)) {
            return """
                    你正在为用户构建“人生图谱”（GraphRAG）。请从日记中抽取实体、关系与证据片段，并输出严格 JSON。

                    输出要求：
                    1) 只输出一个 JSON 对象，不要输出任何额外文字
                    2) JSON 结构：
                    {
                      "entities": [
                        {
                          "type": "Person|Event|Place|Emotion|Topic|Item",
                          "displayName": "原文中的称呼或新实体名称",
                          "nameNorm": "归一化名称（尽量映射到已知实体库的规范名；若为新实体则给出合理规范名）",
                          "aliases": ["别名1","别名2"],
                          "summary": "实体的一句话摘要，描述该实体在用户生活中的意义",
                          "emotion": "该实体关联的主要情绪（如：Joy/Sadness/Anxiety/Love/Anger/Fear/Hope/Calm/Confusion/Neutral）",
                          "importance": 0.5,
                          "confidence": 0.0,
                          "props": {}
                        }
                      ],
                      "relations": [
                        {
                          "source": "__USER__|nameNorm",
                          "target": "nameNorm",
                          "type": "RELATED_TO|HAPPENED_AT|TRIGGERED|PARTICIPATED|MENTIONED_IN",
                          "confidence": 0.0,
                          "props": {},
                          "evidenceSnippet": "可选，<=200字"
                        }
                      ],
                      "mentions": [
                        {
                          "entity": "nameNorm",
                          "snippet": "证据片段，<=200字",
                          "props": {}
                        }
                      ]
                    }

                    字段说明：
                    - summary: 必填，用一句话概括该实体对用户的意义
                    - emotion: 可选，该实体在上下文中引发的主要情绪
                    - importance: 0.1-1.0，评估该实体对用户的重要程度

                    抽取原则：
                    1) 若无法确定映射：优先使用已知实体库；仍不确定则创建新实体，但把可能别名放到 aliases
                    2) 关系置信度：LLM 自动抽取建议在 0.6-0.9 区间
                    3) summary 要具体，避免泛泛而谈
                    4) importance 要综合考虑：提及频率、情感强度、对用户生活的影响程度
                    """;
        }
        if (PromptKey.GRAPHRAG_MERGE_SUGGEST.getKey().equals(keyStr)) {
            return """
                    你将获得若干“疑似重复实体”的候选对。请评估每一对候选人是否指向同一个实际事物。如果是指代同一事物，请给出是否建议合并（YES/NO）、原因、推荐保留的规范名。

                    请务必只输出严格的 JSON 数组，格式如下：
                    [
                      {
                        "merge": "YES或NO",
                        "reason": "原因说明",
                        "recommendedMasterName": "推荐名"
                      }
                    ]
                    """;
        }
        if (PromptKey.COGNITION_ROUTING.getKey().equals(keyStr)) {
            return """
                    你是统一 AI Agent 的认知分流器。请根据输入文本，抽取两类信息：
                    1. 适合进入 user_persona 的稳定偏好信息
                    2. 适合进入 mid_memory 的近期状态信息

                    输出要求：
                    1. 只输出严格 JSON，不要输出任何额外文字
                    2. 如果某类信息不足，请返回空字符串或 null，不要编造

                    JSON 结构如下：
                    {
                      "preferredName": "",
                      "location": "",
                      "interests": "",
                      "tone": "",
                      "customInstructions": "",
                      "midMemorySummary": "",
                      "midMemoryImportance": 0.6,
                      "midMemoryCategory": ""
                    }

                    抽取原则与时间归一化说明：
                    - preferredName: 仅当用户明确表达希望被怎么称呼
                    - location: 仅当输入呈现较稳定的居住地/城市信息
                    - interests: 仅提取相对稳定的兴趣偏好
                    - tone/customInstructions: 仅提取长期有效的相处偏好和硬性约束（如禁忌、避讳话题）
                    - midMemorySummary: 总结用户当前阶段最值得记住的近期状态或具体事件。
                    - midMemoryImportance: 取值 0.1-1.0
                    - midMemoryCategory: 对近期状态进行分类，只选择以下三个值之一（如果无近期状态，设为 null 或 ""）：
                      * "EMOTION_OR_STATE"：瞬态情绪、心理感受或短期状态（如：今天很开心、工作累了、有些郁闷）
                      * "EVENT_OR_PLAN"：阶段性事件或近期具体计划（如：下周要去北京面试、明天过生日、最近在准备考试）
                      * "PREFERENCE_OR_HABIT"：尚不够稳定成为永久画像的中期偏好/习惯/长期目标（如：最近在学吉他、最近喜欢上喝抹茶）
                    - 不要泄露隐私细节，不要原样复述手机号、单位名、真实姓名。
                    - **时间归一化**：如果文本中包含相对时间词汇（如“昨天”、“上周”、“最近”、“下个月”、“明天”），请结合上面给出的“时间”字段，将其换算并归一化为绝对日期或时间段。
                    """;
        }
        if (PromptKey.COGNITIVE_CONFLICT.getKey().equals(keyStr)) {
            return "你是一个认知一致性检测器。请判断以下\"已有认知\"与\"新观察\"之间是否存在语义矛盾。\n\n" +
                    "已有认知（来自 user-persona / lifeGraph）：\n" +
                    "{{existingBelief}}\n\n" +
                    "新观察（来自最近的对话或日记洞察）：\n" +
                    "{{newObservation}}\n\n" +
                    "判断标准：\n" +
                    "1. 如果新观察直接与已有认知相反或明显矛盾 → hasConflict: true\n" +
                    "2. 如果新观察只是补充了新的侧面，不矛盾 → hasConflict: false\n" +
                    "3. 模糊的情况，倾向于 hasConflict: false\n\n" +
                    "请严格输出 JSON，不要输出任何额外文字：\n" +
                    "{\n" +
                    "  \"hasConflict\": false,\n" +
                    "  \"description\": \"一句话描述矛盾之处，供 Agent 在对话中自然地提及（hasConflict为false时填空字符串）\"\n" +
                    "}\n";
        }
        if (PromptKey.SOUL_WEEKLY_REPORT.getKey().equals(keyStr)) {
            return "你是用户的 AI 知己（小予）。请根据以下信息，为用户生成一份温暖、真诚的\"灵魂周报\"。\n\n" +
                    "用户本周概况：\n" +
                    "{{context}}\n\n" +
                    "要求：\n" +
                    "1. 以\"亲爱的，这是你本周的灵魂周报 🌙\"开头\n" +
                    "2. 包含以下板块（使用 Markdown 格式）：\n" +
                    "   - **本周情绪掠影**：本周的情绪趋势和氛围基调（1-2 句）\n" +
                    "   - **你关注的**：用户本周主要关注的话题/主题（2-3 点，以列表呈现）\n" +
                    "   - **小小的变化**：与之前相比，本周你身上的一些变化或成长（1-2 句）\n" +
                    "   - **我想对你说**：以知己的口吻，给用户一段温暖的回应（2-3 句）\n" +
                    "3. 语气自然温柔，有洞察但不居高临下\n" +
                    "4. 不要编造不存在的事实，只基于提供的信息\n" +
                    "5. 总字数控制在 300 字左右\n";
        }
        if (PromptKey.MEMORY_FUSION.getKey().equals(keyStr)) {
            return "你是统一 Agent 的记忆融合与冲突清理器。请评估以下两条关于用户的记忆/洞察：\n\n" +
                    "记忆 A（较新，创建时间：{{timeA}}）：\n" +
                    "{{insightA}}\n\n" +
                    "记忆 B（较旧，创建时间：{{timeB}}）：\n" +
                    "{{insightB}}\n\n" +
                    "请分析这两条记忆的关系并作出判断，可分为以下三种情况：\n" +
                    "1. 【语义相同/可合并】：两条记忆描述的是同一件事、同一个偏好或同一段情绪状态。此时应将其合并。\n" +
                    "   例如：“用户最近在学网球”与“用户上周开始练习网球”。\n" +
                    "   -> shouldMerge: true, isConflict: false, conflictAction: \"NONE\"\n" +
                    "2. 【排他性冲突/需覆写】：两条记忆存在绝对的、不可共存的冲突（通常由于状态发生更新，如常驻地变了、职业变了、关系状态变了）。由于记忆 A 较新，应以 A 为准，主动覆写/失效旧的记忆 B。\n" +
                    "   例如：A：“用户搬到了北京工作”；B：“用户住在上海”。\n" +
                    "   -> shouldMerge: false, isConflict: true, conflictAction: \"OVERWRITE_B\"\n" +
                    "3. 【无关联/独立共存】：两条记忆讲述的是完全不同的事情，且没有冲突。\n" +
                    "   -> shouldMerge: false, isConflict: false, conflictAction: \"NONE\"\n\n" +
                    "请严格输出 JSON 格式，不要包含任何 markdown 块或额外解释：\n" +
                    "{\n" +
                    "  \"shouldMerge\": false,\n" +
                    "  \"mergedSummary\": \"若 shouldMerge 为 true，填写合并后最精炼、准确的一句话摘要，需保留绝对时间（如果有）；否则为空字符串\",\n" +
                    "  \"isConflict\": false,\n" +
                    "  \"conflictAction\": \"NONE\", \n" +
                    "  \"reason\": \"作出此判断的简短依据\"\n" +
                    "}\n";
        }
        if (PromptKey.AGENT_PROACTIVE_GREETING.getKey().equals(keyStr)) {
            return "你是用户的 AI 知己（小予）。请根据用户的基本信息、画像以及近期中期记忆，为用户动态生成一条个性化、自然且温暖的主动关怀问候。\n\n" +
                    "用户基本信息：\n" +
                    "- 昵称：{{userName}}\n" +
                    "- 人格风格：{{personalityStyle}}\n\n" +
                    "近期中期记忆：\n" +
                    "{{midTermMemories}}\n\n" +
                    "要求：\n" +
                    "1. 根据用户设定的人格风格（如 lively-活泼、calm-温和、rational-理性，或其它）来调整语气。\n" +
                    "2. 结合中期记忆，提及其中的 1-2 点（如最近的烦心事、开心的体验、某件重要的事、情绪波动等），以自然的方式融入问候，表达你的关心和好奇。\n" +
                    "3. 不要太长，控制在 1-2 句（60字以内）。\n" +
                    "4. 语气一定要自然温暖，像真正的知己，避免套话或AI味。\n" +
                    "5. 直接返回生成的问候文本，不要包含任何多余的信息、Markdown标记或前缀。\n";
        }
        return "请作为一名 AI 助手回答问题。";
    }

    public String getPrompt(String keyStr) {
        return promptCache.getOrDefault(keyStr, "");
    }

    public String getPrompt(PromptKey key) {
        return getPrompt(key.getKey());
    }

    @EventListener
    public void handlePromptUpdated(PromptUpdatedEvent event) {
        if (StrUtil.isNotBlank(event.getPromptName())) {
            log.info("接收到提示词更新事件 [{}]，触发系统热重载...", event.getPromptName());
            loadPrompt(event.getPromptName());
        }
    }
}
