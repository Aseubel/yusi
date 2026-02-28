package com.aseubel.yusi.service.ai;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.common.constant.PromptKey;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.repository.UserRepository;
import com.aseubel.yusi.service.ai.MidTermMemorySearchService;
import com.aseubel.yusi.service.ai.PromptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import dev.langchain4j.data.message.SystemMessage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

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
    private static final String CURRENT_TIME_START = "<current_time>";
    private static final String CURRENT_TIME_END = "</current_time>";
    private static final String USER_PROFILE_START = "<user_profile>";
    private static final String USER_PROFILE_END = "</user_profile>";
    private static final String USER_ID_START = "<user_id>";
    private static final String USER_ID_END = "</user_id>";
    private static final String NICKNAME_START = "<nickname>";
    private static final String NICKNAME_END = "</nickname>";
    private static final String MEMORY_GUIDELINES_START = "<memory_guidelines>";
    private static final String MEMORY_GUIDELINES_END = "</memory_guidelines>";
    private static final String MEMORY_GUIDELINES_CONTENT = """
            你拥有完整的长期记忆能力。请务必：
            1. 涉及过往经历或回忆 -> 使用 [searchDiary] 工具检索 <memory_fragments>
            2. 涉及人际关系或实体背景 -> 使用 [searchLifeGraph] 工具查询 <graph_relations>
            3. 综合检索结果和对话历史进行回答。
            """;

    private final UserRepository userRepository;
    private final PromptService promptService;
    private final MidTermMemorySearchService midTermMemorySearchService;

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
        systemMessage.append("    ").append(CURRENT_TIME_START).append(DateUtil.now()).append(CURRENT_TIME_END)
                .append("\n");

        injectUserProfile(systemMessage, userId);
        injectMidTermMemories(systemMessage, userId);
        injectMemoryGuidelines(systemMessage);

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

        sb.append("    ").append(USER_PROFILE_END).append("\n");
    }

    /**
     * 注入近期记忆（中期记忆）
     */
    private void injectMidTermMemories(StringBuilder sb, String userId) {
        String midTermMemories = midTermMemorySearchService.getRecentMemories(userId, 5);
        if (StrUtil.isNotBlank(midTermMemories)) {
            sb.append("    <mid_term_memories>\n");
            sb.append("        ").append(midTermMemories.replace("\n", "\n        ")).append("\n");
            sb.append("    </mid_term_memories>\n");
        }
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
     * 加载基础提示词
     * 优先从数据库加载，其次从 classpath 加载，最后使用降级方案
     *
     * @return 基础提示词内容
     */
    private String loadBasePrompt() {
        String dbPrompt = loadFromDatabase();
        if (dbPrompt != null) {
            return dbPrompt;
        }

        String classpathPrompt = loadFromClasspath();
        if (classpathPrompt != null) {
            return classpathPrompt;
        }

        log.warn("Using fallback prompt");
        return "你是 Yusi，一位温暖、富有同理心的 AI 灵魂伴侣。";
    }

    /**
     * 从数据库加载提示词
     */
    private String loadFromDatabase() {
        try {
            String prompt = promptService.getPrompt(PromptKey.CHAT.getKey());
            if (isValidPrompt(prompt)) {
                log.info("Loaded base prompt from DB, length: {}", prompt.length());
                return prompt;
            }
        } catch (Exception e) {
            log.warn("Failed to load prompt from DB: {}, falling back to classpath resource", e.getMessage());
        }
        return null;
    }

    /**
     * 从 classpath 加载提示词
     */
    private String loadFromClasspath() {
        try {
            ClassPathResource resource = new ClassPathResource("chat-prompt.txt");
            String prompt = resource.getContentAsString(StandardCharsets.UTF_8);
            if (isValidPrompt(prompt)) {
                log.info("Loaded base prompt from classpath, length: {}", prompt.length());
                return prompt;
            }
        } catch (IOException e) {
            log.warn("Failed to load prompt from classpath: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 验证提示词是否有效
     */
    private boolean isValidPrompt(String prompt) {
        return prompt != null && prompt.length() > 10;
    }
}
