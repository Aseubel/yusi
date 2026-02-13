package com.aseubel.yusi.service.ai;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.common.constant.PromptKey;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 上下文构建服务
 * 
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

    private final UserRepository userRepository;
    private final PromptService promptService;

    public String buildSystemMessage(Object memoryId) {
        String userId = memoryId.toString();
        
        StringBuilder sb = new StringBuilder();
        String basePrompt = loadBasePrompt();
        sb.append(basePrompt).append("\n\n");

        sb.append("<context>\n");
        
        // 1. Inject Time
        sb.append("    <current_time>").append(DateUtil.now()).append("</current_time>\n");

        // 2. Inject User Info
        User user = userRepository.findByUserId(userId);
        if (user != null) {
            sb.append("    <user_profile>\n");
            sb.append("        <user_id>").append(userId).append("</user_id>\n");
            if (StrUtil.isNotBlank(user.getUserName())) {
                sb.append("        <nickname>").append(user.getUserName()).append("</nickname>\n");
            }
            sb.append("    </user_profile>\n");
        }

        // 3. Inject Memory Guidelines (as part of context instructions)
        sb.append("    <memory_guidelines>\n");
        sb.append("        你拥有完整的长期记忆能力。请务必：\n");
        sb.append("        1. 涉及过往经历或回忆 -> 使用 [searchDiary] 工具检索 <memory_fragments>\n");
        sb.append("        2. 涉及人际关系或实体背景 -> 使用 [searchLifeGraph] 工具查询 <graph_relations>\n");
        sb.append("        3. 综合检索结果和对话历史进行回答。\n");
        sb.append("    </memory_guidelines>\n");
        
        sb.append("</context>\n");

        return sb.toString();
    }

    private String loadBasePrompt() {
        try {
            String dbPrompt = promptService.getPrompt(PromptKey.CHAT.getKey());
            if (dbPrompt != null && dbPrompt.length() > 10) {
                return dbPrompt;
            }
        } catch (Exception e) {
            log.warn("Failed to load prompt from DB: {}", e.getMessage());
        }

        try {
            ClassPathResource resource = new ClassPathResource("chat-prompt.txt");
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "你是 Yusi，一位温暖、富有同理心的 AI 灵魂伴侣。";
        }
    }
}
