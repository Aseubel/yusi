package com.aseubel.yusi.service.ai;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.common.constant.PromptKey;
import com.aseubel.yusi.pojo.entity.AgentPersonaConfig;
import com.aseubel.yusi.pojo.entity.MidTermMemory;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.pojo.entity.UserPersona;
import com.aseubel.yusi.repository.AgentPersonaConfigRepository;
import com.aseubel.yusi.repository.ChatMemoryMessageRepository;
import com.aseubel.yusi.repository.MidTermMemoryRepository;
import com.aseubel.yusi.repository.UserRepository;
import com.aseubel.yusi.service.cognition.CognitiveConflictDetector;
import com.aseubel.yusi.service.user.UserPersonaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import dev.langchain4j.data.message.SystemMessage;

import java.time.LocalDateTime;
import java.util.List;

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

    private final UserRepository userRepository;
    private final PromptManager promptManager;
    private final ChatMemoryMessageRepository chatMemoryMessageRepository;
    private final UserPersonaService userPersonaService;
    private final AgentPersonaConfigRepository agentPersonaConfigRepository;
    private final MidTermMemoryRepository midTermMemoryRepository;
    private final CognitiveConflictDetector conflictDetector;

    /**
     * 构建 System Message 内容
     * LangChain4j 的 systemMessageProvider 期望返回 String
     *
     * @param memoryId 用户ID
     * @return 完整的 System Message 字符串
     */
    public String buildSystemMessageStr(Object memoryId) {
        String userId = memoryId.toString();

        String basePrompt = loadBasePrompt();
        log.debug("Building system message for user: {}, basePrompt length: {}", userId, basePrompt.length());

        StringBuilder systemMessage = new StringBuilder();
        systemMessage.append(basePrompt).append("\n\n");
        systemMessage.append(CONTEXT_START).append("\n");

        injectTimeContext(systemMessage);
        injectAgentPersona(systemMessage, userId);
        injectUserProfile(systemMessage, userId);
        injectMidMemoryContext(systemMessage, userId);
        injectCognitiveConflicts(systemMessage, userId);
        injectMemoryGuidelines(systemMessage);
        injectRelationshipStage(systemMessage, userId);

        systemMessage.append(CONTEXT_END).append("\n");

        String result = systemMessage.toString();
        log.debug("System message built, total length: {}", result.length());
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
    private void injectTimeContext(StringBuilder sb) {
        sb.append("    ").append(TIME_CONTEXT_START).append("\n");
        sb.append("        ").append("<current_time>").append(DateUtil.now()).append("</current_time>").append("\n");
        sb.append("        ").append("<current_date>").append(DateUtil.date().toString()).append("</current_date>").append("\n");
        sb.append("        ").append("<timezone>").append(java.util.TimeZone.getDefault().getID()).append("</timezone>").append("\n");
        sb.append("    ").append(TIME_CONTEXT_END).append("\n");
    }

    /**
     * 注入用户画像信息
     */
    private void injectUserProfile(StringBuilder sb, String userId) {
        User user = userRepository.findByUserId(userId);
        if (user == null) {
            return;
        }

        sb.append("    ").append(USER_PROFILE_START).append("\n");
        sb.append("        ").append(USER_ID_START).append(userId).append(USER_ID_END).append("\n");

        if (StrUtil.isNotBlank(user.getUserName())) {
            sb.append("        ").append(NICKNAME_START).append(user.getUserName()).append(NICKNAME_END).append("\n");
        }

        // 注入用户画像/偏好 (UserPersona)
        UserPersona persona = userPersonaService.getUserPersona(userId);
        if (persona != null) {
            if (StrUtil.isNotBlank(persona.getPreferredName())) {
                sb.append("        ").append("<preferred_name>").append(persona.getPreferredName())
                        .append("</preferred_name>").append("\n");
            }
            if (StrUtil.isNotBlank(persona.getLocation())) {
                sb.append("        ").append("<location>").append(persona.getLocation()).append("</location>")
                        .append("\n");
            }
            if (StrUtil.isNotBlank(persona.getInterests())) {
                sb.append("        ").append("<interests>").append(persona.getInterests()).append("</interests>")
                        .append("\n");
            }
            if (StrUtil.isNotBlank(persona.getTone())) {
                sb.append("        ").append("<tone_preference>").append(persona.getTone()).append("</tone_preference>")
                        .append("\n");
            }
            if (StrUtil.isNotBlank(persona.getCustomInstructions())) {
                sb.append("        ").append("<custom_instructions>").append(persona.getCustomInstructions())
                        .append("</custom_instructions>").append("\n");
            }
        }

        sb.append("    ").append(USER_PROFILE_END).append("\n");
    }

    /**
     * 注入记忆引导
     */
    private void injectMemoryGuidelines(StringBuilder sb) {
        sb.append("    ").append(MEMORY_GUIDELINES_START).append("\n");
        sb.append("        ").append(MEMORY_GUIDELINES_CONTENT);
        sb.append("    ").append(MEMORY_GUIDELINES_END).append("\n");
    }

    /**
     * 注入 Agent 人格配置，让 Agent 保持稳定的性格和陪伴风格。
     */
    private void injectAgentPersona(StringBuilder sb, String userId) {
        AgentPersonaConfig config = agentPersonaConfigRepository.findByUserId(userId).orElse(null);
        if (config == null) {
            config = AgentPersonaConfig.builder().userId(userId).build();
        }

        sb.append("    ").append(AGENT_PERSONA_START).append("\n");

        String style = config.getPersonalityStyle();
        // TODO: 将人格 prompt 迁移至 PromptManager，通过 PromptKey 统一管理，支持多语言和热更新
        String personaInstruction = switch (style) {
            case "lively" -> "你是一个性格活泼、充满好奇心的陪伴者。语气轻快自然，适当使用表情和俏皮的表达。你在认真倾听的同时保持轻松愉快的氛围。";
            case "calm" -> "你是一个沉静、善于倾听的陪伴者。语气平和温柔，不急于表达观点，给对方充分的空间。你的存在本身就是一种安静的陪伴。";
            case "rational" -> "你是一个理性、善于分析的陪伴者。表达清晰有条理，能帮对方理清思路。你不冷漠，但更倾向于用逻辑和洞察来支持对方。";
            default -> "你是一个温柔、善解人意的知己。语气温暖而有边界感，懂得何时给建议、何时只是陪伴。你是对方可以完全放松做自己的存在。";
        };
        sb.append("        ").append("<style>").append(personaInstruction).append("</style>").append("\n");

        if (!"off".equalsIgnoreCase(config.getProactiveFrequency())) {
            sb.append("        ").append("<proactive>").append("你在合适的时机关心对方的状态，但始终保持舒适的距离感。")
                    .append("</proactive>").append("\n");
        }

        sb.append("    ").append(AGENT_PERSONA_END).append("\n");
    }

    /**
     * 注入用户近期状态摘要（中期记忆），让 Agent 了解用户当前阶段。
     */
    private void injectMidMemoryContext(StringBuilder sb, String userId) {
        List<MidTermMemory> recentMemories = midTermMemoryRepository
                .findValidByUserId(userId, LocalDateTime.now(), PageRequest.of(0, 3));
        if (recentMemories.isEmpty()) {
            return;
        }

        sb.append("    ").append(MID_MEMORY_START).append("\n");
        sb.append("        <description>以下是你对用户近期状态的了解，你可以在对话中自然地提及，但不要机械复述。</description>\n");
        for (MidTermMemory memory : recentMemories) {
            String summary = memory.getSummary();
            // TODO: 修复 emoji 代理对 (surrogate pairs) 截断问题，改用 codePointCount 安全截断
            if (summary != null && summary.length() > 150) {
                summary = summary.substring(0, 150) + "...";
            }
            sb.append("        ").append("<recent_insight importance=\"")
                    .append(String.format("%.2f", memory.getImportance() != null ? memory.getImportance() : 0.5))
                    .append("\">").append(summary).append("</recent_insight>").append("\n");
        }
        sb.append("    ").append(MID_MEMORY_END).append("\n");
    }

    /**
     * 注入未解决的认知冲突，引导 Agent 在对话中自然地"注意到变化"（F11.3）。
     */
    private void injectCognitiveConflicts(StringBuilder sb, String userId) {
        String conflictContext = conflictDetector.getUnresolvedContext(userId);
        if (conflictContext != null) {
            sb.append("    <cognitive_conflicts>\n");
            sb.append("        ").append(conflictContext).append("\n");
            sb.append("    </cognitive_conflicts>\n");
        }
    }

    // 在 ContextBuilderService 中注入关系阶段
    private void injectRelationshipStage(StringBuilder sb, String userId) {
        // 获取用户的对话轮数 (以用户发言次数作为轮数)
        long chatTurns = chatMemoryMessageRepository.countByMemoryIdAndRole(userId, "user");

        sb.append("    <relationship_stage>\n");
        if (chatTurns < 10) {
            sb.append("        你们刚刚认识，这是前几次交流。请保持友好、好奇但克制的距离感，不要假装你们有很久的过去，不要凭空捏造回忆。\n");
        } else if (chatTurns < 50) {
            sb.append("        你们已经比较熟悉了，可以像普通朋友一样自然交流。\n");
        } else {
            sb.append("        你们是非常亲密的灵魂知己，拥有深厚的共同记忆，可以极其自然、默契地互动。\n");
        }
        sb.append("    </relationship_stage>\n");
    }

    /**
     * 加载基础提示词
     */
    private String loadBasePrompt() {
        return promptManager.getPrompt(PromptKey.CHAT);
    }
}
