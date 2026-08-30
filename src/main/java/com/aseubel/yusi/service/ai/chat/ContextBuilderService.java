package com.aseubel.yusi.service.ai.chat;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.common.constant.PromptKey;
import com.aseubel.yusi.config.MemoryConfigProperties;
import com.aseubel.yusi.pojo.entity.AgentPersonaConfig;
import com.aseubel.yusi.pojo.constant.ProactiveFrequency;
import com.aseubel.yusi.pojo.entity.MidTermMemory;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.pojo.entity.UserPersona;
import com.aseubel.yusi.repository.AgentPersonaConfigRepository;
import com.aseubel.yusi.repository.ChatMemoryMessageRepository;
import com.aseubel.yusi.repository.MidTermMemoryRepository;
import com.aseubel.yusi.repository.UserRepository;
import com.aseubel.yusi.service.ai.prompt.PromptManager;
import com.aseubel.yusi.service.ai.model.ModelTokenEstimator;
import com.aseubel.yusi.service.cognition.CognitiveConflictDetector;
import com.aseubel.yusi.service.memory.MemoryDecayService;
import com.aseubel.yusi.service.user.UserPersonaService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import dev.langchain4j.data.message.SystemMessage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 上下文构建服务
 * 负责组装 AI 对话的 System Message，注入：
 * 1. 基础 System Prompt (角色设定)
 * 2. 动态时间上下文
 * 3. 用户画像信息
 * 4. 记忆引导 (提示 AI 使用 Graph/Diary 工具)
 *
 * @author Aseubel
 * @date 2026/02/10
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContextBuilderService {

    private static final String CONTEXT_START = "<context>";
    private static final String CONTEXT_END = "</context>";
    private static final String USER_PROFILE_START = "<user_profile>";
    private static final String USER_PROFILE_END = "</user_profile>";
    private static final String USER_ID_START = "<user_id>";
    private static final String USER_ID_END = "</user_id>";
    private static final String NICKNAME_START = "<nickname>";
    private static final String NICKNAME_END = "</nickname>";
    private static final String AGENT_PERSONA_START = "<agent_persona>";
    private static final String AGENT_PERSONA_END = "</agent_persona>";
    private static final String MEMORY_GUIDELINES_START = "<memory_guidelines>";
    private static final String MEMORY_GUIDELINES_END = "</memory_guidelines>";
    private static final String MEMORY_GUIDELINES_CONTENT = """
            你拥有统一的记忆检索工具 [searchMemories]。
            当涉及过往经历、人际关系、特定事实或之前的对话细节时，请务必调用 [searchMemories] 进行查询。
            该工具会自动聚合日记、图谱和对话记忆。
            """;
    private static final String TIME_CONTEXT_START = "<time_context>";
    private static final String TIME_CONTEXT_END = "</time_context>";
    private static final String MID_MEMORY_START = "<mid_memory_context>";
    private static final String MID_MEMORY_END = "</mid_memory_context>";
    private static final int MAX_PERSONA_CODE_POINTS = 1200;
    private static final int MAX_NICKNAME_CODE_POINTS = 100;
    private static final int MAX_PROFILE_FIELD_CODE_POINTS = 400;
    private static final int MAX_CUSTOM_INSTRUCTIONS_CODE_POINTS = 800;
    private static final int MAX_CONFLICT_CODE_POINTS = 1200;

    private final UserRepository userRepository;
    private final PromptManager promptManager;
    private final ChatMemoryMessageRepository chatMemoryMessageRepository;
    private final UserPersonaService userPersonaService;
    private final AgentPersonaConfigRepository agentPersonaConfigRepository;
    private final MidTermMemoryRepository midTermMemoryRepository;
    private final MemoryDecayService memoryDecayService;
    private final CognitiveConflictDetector conflictDetector;
    private final ObjectMapper objectMapper;
    private final MemoryConfigProperties memoryConfigProperties;
    private final ModelTokenEstimator tokenEstimator;

    /**
     * 构建 System Message 内容
     * LangChain4j 的 systemMessageProvider 期望返回 String
     *
     * @param memoryId 用户ID
     * @return 完整的 System Message 字符串
     */
    public String buildSystemMessageStr(Object memoryId) {
        String userId = memoryId == null ? "" : memoryId.toString();

        String basePrompt = loadBasePrompt();
        if (basePrompt == null) {
            basePrompt = "";
        }
        log.debug("Building system message: operation=build_context, basePromptLength={}", basePrompt.length());

        StringBuilder systemMessage = new StringBuilder();
        systemMessage.append(basePrompt).append("\n\n");
        systemMessage.append(CONTEXT_START).append("\n");

        List<ContextSection> sections = List.of(
                new ContextSection("time", injectTimeContext(), true),
                new ContextSection("memory_guidelines", injectMemoryGuidelines(), true),
                new ContextSection("relationship_stage", injectRelationshipStage(userId), true),
                new ContextSection("agent_persona", injectAgentPersona(userId), false),
                new ContextSection("user_profile", injectUserProfile(userId), false),
                new ContextSection("mid_memory", injectMidMemoryContext(userId), false),
                new ContextSection("cognitive_conflicts", injectCognitiveConflicts(userId), false));

        int tokenBudget = Math.max(1, memoryConfigProperties.getContextTokenBudget());
        for (ContextSection section : sections) {
            if (StrUtil.isBlank(section.content())) {
                continue;
            }
            if (!section.required() && !fitsWithinBudget(systemMessage, section.content(), tokenBudget)) {
                log.debug("Context section omitted: operation=build_context, section={}, reason=token_budget",
                        section.name());
                continue;
            }
            systemMessage.append(section.content());
        }

        systemMessage.append(CONTEXT_END).append("\n");

        String result = systemMessage.toString();
        int estimatedTokens = tokenEstimator.estimateText(result);
        if (estimatedTokens > tokenBudget) {
            log.warn("System message core exceeds configured budget: operation=build_context, "
                    + "estimatedTokens={}, budget={}", estimatedTokens, tokenBudget);
        }
        log.debug("System message built: operation=build_context, estimatedTokens={}, length={}",
                estimatedTokens, result.length());
        return result;
    }

    /**
     * 构建 System Message 对象
     * 用于 LangChain4j 的 AI 服务
     *
     * @param memoryId 用户ID
     * @return SystemMessage 对象
     */
    public SystemMessage buildSystemMessage(Object memoryId) {
        String content = buildSystemMessageStr(memoryId);
        return SystemMessage.from(content);
    }

    /**
     * 注入时间上下文信息
     */
    private String injectTimeContext() {
        StringBuilder sb = new StringBuilder();
        sb.append("    ").append(TIME_CONTEXT_START).append("\n");
        sb.append("        ").append("<current_time>").append(DateUtil.now()).append("</current_time>").append("\n");
        sb.append("        ").append("<current_date>").append(DateUtil.date().toString()).append("</current_date>").append("\n");
        sb.append("        ").append("<timezone>").append(java.util.TimeZone.getDefault().getID()).append("</timezone>").append("\n");
        sb.append("    ").append(TIME_CONTEXT_END).append("\n");
        return sb.toString();
    }

    /**
     * 注入用户画像信息
     */
    private String injectUserProfile(String userId) {
        User user = userRepository.findByUserId(userId);
        if (user == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("    ").append(USER_PROFILE_START).append("\n");
        sb.append("        ").append(USER_ID_START).append(userId).append(USER_ID_END).append("\n");

        appendTag(sb, "nickname", user.getUserName(), MAX_NICKNAME_CODE_POINTS);

        // 注入用户画像/偏好 (UserPersona)
        UserPersona persona = userPersonaService.getUserPersona(userId);
        if (persona != null) {
            appendTag(sb, "preferred_name", persona.getPreferredName(), MAX_PROFILE_FIELD_CODE_POINTS);
            appendTag(sb, "location", persona.getLocation(), MAX_PROFILE_FIELD_CODE_POINTS);
            appendTag(sb, "interests", persona.getInterests(), MAX_PROFILE_FIELD_CODE_POINTS);
            appendTag(sb, "tone_preference", persona.getTone(), MAX_PROFILE_FIELD_CODE_POINTS);
            appendTag(sb, "custom_instructions", persona.getCustomInstructions(),
                    MAX_CUSTOM_INSTRUCTIONS_CODE_POINTS);
        }

        sb.append("    ").append(USER_PROFILE_END).append("\n");
        return sb.toString();
    }

    /**
     * 注入记忆引导
     */
    private String injectMemoryGuidelines() {
        StringBuilder sb = new StringBuilder();
        sb.append("    ").append(MEMORY_GUIDELINES_START).append("\n");
        sb.append("        ").append(MEMORY_GUIDELINES_CONTENT);
        sb.append("    ").append(MEMORY_GUIDELINES_END).append("\n");
        return sb.toString();
    }

    /**
     * 注入 Agent 人格配置，让 Agent 保持稳定的性格和陪伴风格。
     */
    private String injectAgentPersona(String userId) {
        AgentPersonaConfig config = agentPersonaConfigRepository.findByUserId(userId).orElse(null);
        if (config == null) {
            config = AgentPersonaConfig.builder().userId(userId).build();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("    ").append(AGENT_PERSONA_START).append("\n");

        String style = config.getPersonalityStyle();
        String personaInstruction = limitCodePoints(resolvePersonaInstruction(style), MAX_PERSONA_CODE_POINTS);
        sb.append("        ").append("<style>").append(personaInstruction).append("</style>").append("\n");

        if (ProactiveFrequency.fromCode(config.getProactiveFrequency()) != ProactiveFrequency.OFF) {
            sb.append("        ").append("<proactive>").append("你在合适的时机关心对方的状态，但始终保持舒适的距离感。")
                    .append("</proactive>").append("\n");
        }

        sb.append("    ").append(AGENT_PERSONA_END).append("\n");
        return sb.toString();
    }

    /**
     * 从 PromptManager 解析 JSON 格式的人格风格配置，支持管理后台热更新。
     */
    private String resolvePersonaInstruction(String style) {
        try {
            String personaJson = promptManager.getPrompt(PromptKey.AGENT_PERSONA);
            java.util.Map<String, String> styles = objectMapper.readValue(
                    personaJson, new TypeReference<java.util.Map<String, String>>() {});
            return styles.getOrDefault(style, styles.getOrDefault("default",
                    "你是一个温柔、善解人意的知己。"));
        } catch (Exception e) {
            log.warn("解析 agent-persona 提示词失败，使用默认风格: {}", e.getMessage());
            return "你是一个温柔、善解人意的知己。语气温暖而有边界感，懂得何时给建议、何时只是陪伴。你是对方可以完全放松做自己的存在。";
        }
    }

    /**
     * 注入用户近期状态摘要（中期记忆），让 Agent 了解用户当前阶段。
     */
    private String injectMidMemoryContext(String userId) {
        List<MidTermMemory> recentMemories = midTermMemoryRepository
                .findValidByUserId(userId, PageRequest.of(0, 10)).stream()
                // 懒遗忘：满足"低初始重要性 + 衰减后低于阈值"的记忆落库标记并过滤
                .filter(memory -> !memoryDecayService.checkAndMarkForgotten(memory))
                .toList();
        if (recentMemories.isEmpty()) {
            return "";
        }

        // 半衰期软衰减排序：旧记忆权重自然下沉，但不会被硬过滤
        List<MidTermMemory> sortedMemories = recentMemories.stream()
                .sorted((a, b) -> Double.compare(
                        memoryDecayService.effectiveImportance(b), memoryDecayService.effectiveImportance(a)))
                .limit(3)
                .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();
        StringBuilder memories = new StringBuilder();
        memories.append("        <description>以下是你对用户近期状态的了解，你可以在对话中自然地提及，但不要机械复述。</description>\n");
        int renderedMemoryCount = 0;
        for (MidTermMemory memory : sortedMemories) {
            String summary = limitCodePoints(memory.getSummary(), 150);
            if (StrUtil.isBlank(summary)) {
                continue;
            }
            // 注入上下文即视为"被想起"：强化记忆并重置衰减时钟
            memoryDecayService.reinforce(memory);
            double decayedImp = memoryDecayService.effectiveImportance(memory);
            memories.append("        ").append("<recent_insight importance=\"")
                    .append(String.format("%.2f", decayedImp))
                    .append("\">").append(summary).append("</recent_insight>").append("\n");
            renderedMemoryCount++;
        }
        if (renderedMemoryCount == 0) {
            return "";
        }
        sb.append("    ").append(MID_MEMORY_START).append("\n").append(memories);
        sb.append("    ").append(MID_MEMORY_END).append("\n");
        return sb.toString();
    }

    /**
     * 注入未解决的认知冲突，引导 Agent 在对话中自然地"注意到变化"（F11.3）。
     */
    private String injectCognitiveConflicts(String userId) {
        String conflictContext = conflictDetector.getUnresolvedContext(userId);
        if (StrUtil.isBlank(conflictContext)) {
            return "";
        }
        return "    <cognitive_conflicts>\n"
                + "        " + limitCodePoints(conflictContext, MAX_CONFLICT_CODE_POINTS) + "\n"
                + "    </cognitive_conflicts>\n";
    }

    // 在 ContextBuilderService 中注入关系阶段
    private String injectRelationshipStage(String userId) {
        // 获取用户的对话轮数 (以用户发言次数作为轮数)
        long chatTurns = chatMemoryMessageRepository.countByMemoryIdAndRole(userId, "user");

        StringBuilder sb = new StringBuilder();
        sb.append("    <relationship_stage>\n");
        if (chatTurns < 10) {
            sb.append("        你们刚刚认识，这是前几次交流。请保持友好、好奇但克制的距离感，不要假装你们有很久的过去，不要凭空捏造回忆。\n");
        } else if (chatTurns < 50) {
            sb.append("        你们已经比较熟悉了，可以像普通朋友一样自然交流。\n");
        } else {
            sb.append("        你们是非常亲密的灵魂知己，拥有深厚的共同记忆，可以极其自然、默契地互动。\n");
        }
        sb.append("    </relationship_stage>\n");
        return sb.toString();
    }

    private boolean fitsWithinBudget(StringBuilder current, String section, int budget) {
        String candidate = current.toString() + section + CONTEXT_END + "\n";
        return tokenEstimator.estimateText(candidate) <= budget;
    }

    private void appendTag(StringBuilder sb, String tag, String value, int maxCodePoints) {
        String bounded = limitCodePoints(value, maxCodePoints);
        if (StrUtil.isBlank(bounded)) {
            return;
        }
        sb.append("        <").append(tag).append(">").append(bounded)
                .append("</").append(tag).append(">\n");
    }

    private String limitCodePoints(String value, int maxCodePoints) {
        if (value == null || maxCodePoints <= 0) {
            return "";
        }
        int codePointCount = value.codePointCount(0, value.length());
        if (codePointCount <= maxCodePoints) {
            return value;
        }
        int retained = Math.max(0, maxCodePoints - 3);
        return value.substring(0, value.offsetByCodePoints(0, retained)) + "...";
    }

    private record ContextSection(String name, String content, boolean required) {
    }

    /**
     * 加载基础提示词
     */
    private String loadBasePrompt() {
        return promptManager.getPrompt(PromptKey.CHAT);
    }
}
