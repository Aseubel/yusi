package com.aseubel.yusi.service.ai.prompt;

import com.aseubel.yusi.pojo.entity.PromptTemplate;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.common.constant.PromptKey;
import com.aseubel.yusi.common.constant.PromptDefaults;
import com.aseubel.yusi.common.constant.PromptScope;
import com.aseubel.yusi.common.event.PromptUpdatedEvent;
import com.aseubel.yusi.common.utils.LowSensitivityLogSummary;
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

    private final Map<String, PromptSnapshot> promptCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("====== 开始初始化全局提示词管理器 (PromptManager) ======");
        for (PromptKey key : PromptKey.values()) {
            loadPrompt(key.getKey());
        }
        log.info("====== 全局提示词初始化完成 ======");
    }

    private String determineScope(String keyStr) {
        if (PromptKey.CHAT.getKey().equals(keyStr)
                || PromptKey.AGENT_PERSONA.getKey().equals(keyStr)
                || PromptKey.AGENT_PROACTIVE_GREETING.getKey().equals(keyStr)) {
            return PromptScope.GLOBAL.code();
        }
        if (PromptKey.LOGIC.getKey().equals(keyStr)) {
            return PromptScope.ROOM.code();
        }
        if (PromptKey.SOUL_MATCH.getKey().equals(keyStr)
                || PromptKey.SOUL_MATCH_LETTER.getKey().equals(keyStr)) {
            return PromptScope.MATCH.code();
        }
        return PromptScope.DIARY.code();
    }

    public void loadPrompt(String keyStr) {
        PromptTemplate dbTemplate = null;
        try {
            dbTemplate = promptService.getPromptTemplate(keyStr, PromptDefaults.LOCALE);
        } catch (Exception e) {
            log.warn("Prompt database load failed: operation=prompt_load_db, promptKey={}, exceptionType={}",
                    keyStr, LowSensitivityLogSummary.exceptionType(e));
        }

        if (dbTemplate != null && StrUtil.isNotBlank(dbTemplate.getTemplate())
                && dbTemplate.getTemplate().length() > 5) {
            promptCache.put(keyStr, new PromptSnapshot(
                    keyStr,
                    blankToNull(dbTemplate.getVersion()),
                    StrUtil.blankToDefault(dbTemplate.getLocale(), PromptDefaults.LOCALE),
                    dbTemplate.getTemplate()));
            log.info("成功挂载提示词 [{}] - 来源: [Database], 长度: {} 字符",
                    keyStr, dbTemplate.getTemplate().length());
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
            log.warn("Prompt classpath load failed: operation=prompt_load_classpath, promptKey={}, exceptionType={}",
                    keyStr, LowSensitivityLogSummary.exceptionType(e));
        }

        if (contentToUse == null) {
            contentToUse = getHardcodedFallback(keyStr);
            source = "Hardcoded Fallback";
        }

        promptCache.put(keyStr, new PromptSnapshot(
                keyStr, PromptDefaults.VERSION, PromptDefaults.LOCALE, contentToUse));
        log.info("成功挂载提示词 [{}] - 来源: [{}], 长度: {} 字符", keyStr, source, contentToUse.length());

        // Auto-initialize to Database so that it is visible in the admin dashboard!
        try {
            PromptTemplate template = PromptTemplate.builder()
                    .name(keyStr)
                    .template(contentToUse)
                    .version(PromptDefaults.VERSION)
                    .active(true)
                    .scope(determineScope(keyStr))
                    .locale(PromptDefaults.LOCALE)
                    .description(PromptDefaults.AUTO_INITIALIZED_DESCRIPTION)
                    .isDefault(true)
                    .priority(0)
                    .build();
            promptService.savePrompt(template, PromptDefaults.SYSTEM_UPDATER);
            log.info("自动初始化 Prompt [{}] 至数据库", keyStr);
        } catch (Exception e) {
            // Already exists or saving failed, we can ignore this safely
            log.debug("Prompt auto-initialization skipped: operation=prompt_auto_init, promptKey={}, exceptionType={}",
                    keyStr, LowSensitivityLogSummary.exceptionType(e));
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
                    你正在为用户构建“人生图谱”（GraphRAG）。请从输入的日记或用户本人发布的 Plaza 卡片中抽取宽范围的局部候选实体、长期生活关系和原文证据，并输出严格 JSON。

                    重要边界：
                    - 本次输出首先是局部候选上下文，不等于长期 LifeGraph 事实。服务端会再次校验实体类型、关系类型、证据、置信度和用户生活语义。
                    - 抽取范围可以宽，识别 Person、Event、Place、Work、Topic、Emotion、Item；标题、引用、背景知识或转述中的实体不因出现就成为用户事实。
                    - 长期自动升级边界是 User -> 直接重要人物 -> 该人物的属性或事件，不要从人物继续扩展其同事、朋友或其他人物。
                    - 不要使用 MENTIONED、MENTIONED_IN、SAID 或泛化 RELATED_TO 填充长期图谱。

                    输出要求：
                    1) 只输出一个 JSON 对象，不要输出任何额外文字
                    2) JSON 结构：
                    {
                      "entities": [
                        {
                          "type": "Person|Event|Place|Work|Emotion|Topic|Item|User",
                          "displayName": "原文中的称呼或新实体名称",
                          "nameNorm": "归一化名称；新人物使用原文称呼或全名，不要使用身份标签；用户使用 __USER__",
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
                          "type": "PARTNER_OF|FAMILY_OF|FRIEND_OF|COLLEAGUE_OF|MENTOR_OF|SIBLING_OF|PARENT_OF|CHILD_OF|LIKES|DISLIKES|BOUGHT_FOR|PARTICIPATED_IN|EXPERIENCED|HAPPENED_AT|TRIGGERED|WORKED_AT|LIVED_AT|CARED_FOR|HAS_BIRTHDAY|HAS_IMPORTANT_EVENT|VISITED|ATTENDED",
                          "confidence": 0.0,
                          "props": {},
                          "evidenceSnippet": "必填，来自原文，<=100字"
                        }
                      ],
                      "mentions": [
                        {
                          "entity": "nameNorm",
                          "snippet": "来自原文的短证据，<=100字",
                          "props": {}
                        }
                      ]
                    }

                    字段说明：
                    - summary: 必填，用一句话概括该实体对用户的意义
                    - emotion: 可选，该实体在上下文中引发的主要情绪
                    - importance: 0.1-1.0，评估该实体对用户的重要程度

                    关系判断：
                    1) 用户明确表达伴侣、家人、重要朋友等直接关系时，输出 User 与 Person 的关系。
                    2) 已有直接用户关系的人物可以与非 Person 的长期属性或事件建立关系，如“小美喜欢草莓”。
                    3) 用户对重要人物的明确照顾或赠与可以输出 User -> BOUGHT_FOR/CARED_FOR -> 该人物。
                    4) “小美的同事小王喜欢篮球”不应创建小王及其喜好；人物到人物的自动扩展不属于本次写入范围。
                    5) 每条长期关系必须有原文证据和方向；不确定时省略关系，不要用 RELATED_TO 代替。
                    6) summary 基于原文，不要编造；importance 表示长期回顾和个性化互动价值；mentions 没有可靠证据时省略。
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
        if (PromptKey.SOUL_MATCH.getKey().equals(keyStr)) {
            return "你是统一 AI Agent 的匹配精排器。请根据目标用户与候选用户的长期结构、稳定偏好、近期状态，\n" +
                    "判断双方在当前阶段是否值得被推荐给彼此。\n\n" +
                    "{{preferenceContext}}\n\n" +
                    "目标用户画像：\n{{userAProfile}}\n\n" +
                    "候选用户画像：\n{{userBProfile}}\n\n" +
                    "请严格输出 JSON，不要输出任何额外文字，格式如下：\n" +
                    "{\n  \"resonance\": true,\n  \"score\": 86,\n" +
                    "  \"reason\": \"一句话解释为什么两人有共鸣\",\n" +
                    "  \"timingReason\": \"一句话解释为什么是现在\",\n" +
                    "  \"iceBreaker\": \"一段用于破冰的推荐语\"\n}\n\n" +
                    "约束：\n1. resonance 为布尔值\n2. score 为 0-100 整数\n" +
                    "3. 不能泄露真实姓名与隐私细节\n4. reason、timingReason、iceBreaker 都必须是中文\n";
        }
        if (PromptKey.SOUL_MATCH_LETTER.getKey().equals(keyStr)) {
            return "请为你（用户A的AI知己）的用户撰写一封推荐信，向TA推荐另一位用户（用户B）。\n\n" +
                    "用户A（你的用户）的匹配画像：\n{{userAProfile}}\n\n" +
                    "用户B（推荐对象）的匹配画像：\n{{userBProfile}}\n\n" +
                    "已知本次匹配结论：\n- 共鸣原因：{{reason}}\n- 时机原因：{{timingReason}}\n- 破冰建议：{{iceBreaker}}\n\n" +
                    "任务：\n基于以上信息写一封更自然、更像统一Agent判断结果的匿名推荐信。\n\n" +
                    "要求：\n1. 不要提及真实姓名。\n2. 不要泄露隐私细节。\n" +
                    "3. 要体现\"为什么是这个人\"以及\"为什么是现在\"。\n" +
                    "4. 允许吸收 iceBreaker 的表达，但不要机械复读。\n" +
                    "5. 以\"向你推荐一位'灵魂伙伴'\"开头，120-180字。\n";
        }
        if (PromptKey.EMOTION_ANALYSIS.getKey().equals(keyStr)) {
            return "分析情感，只返回类别名：Joy/Sadness/Anxiety/Love/Anger/Fear/Hope/Calm/Confusion/Neutral\n\n内容：{{content}}";
        }
        if (PromptKey.AGENT_PERSONA.getKey().equals(keyStr)) {
            return "{\n" +
                    "  \"default\": \"你是一个温柔、善解人意的知己。语气温暖而有边界感，懂得何时给建议、何时只是陪伴。你是对方可以完全放松做自己的存在。\",\n" +
                    "  \"lively\": \"你是一个性格活泼、充满好奇心的陪伴者。语气轻快自然，适当使用表情和俏皮的表达。你在认真倾听的同时保持轻松愉快的氛围。\",\n" +
                    "  \"calm\": \"你是一个沉静、善于倾听的陪伴者。语气平和温柔，不急于表达观点，给对方充分的空间。你的存在本身就是一种安静的陪伴。\",\n" +
                    "  \"rational\": \"你是一个理性、善于分析的陪伴者。表达清晰有条理，能帮对方理清思路。你不冷漠，但更倾向于用逻辑和洞察来支持对方。\"\n" +
                    "}";
        }
        return "请作为一名 AI 助手回答问题。";
    }

    public String getPrompt(String keyStr) {
        PromptSnapshot snapshot = getSnapshot(keyStr);
        return snapshot == null || snapshot.template() == null ? "" : snapshot.template();
    }

    public String getPrompt(PromptKey key) {
        return getPrompt(key.getKey());
    }

    public PromptSnapshot getSnapshot(String keyStr) {
        return promptCache.get(keyStr);
    }

    public PromptSnapshot getSnapshot(PromptKey key) {
        return key == null ? null : getSnapshot(key.getKey());
    }

    private String blankToNull(String value) {
        return StrUtil.isBlank(value) ? null : value;
    }

    @EventListener
    public void handlePromptUpdated(PromptUpdatedEvent event) {
        if (StrUtil.isNotBlank(event.getPromptName())) {
            log.info("接收到提示词更新事件 [{}]，触发系统热重载...", event.getPromptName());
            loadPrompt(event.getPromptName());
        }
    }
}
