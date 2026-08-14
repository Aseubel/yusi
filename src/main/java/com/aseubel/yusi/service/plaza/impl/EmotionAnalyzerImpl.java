package com.aseubel.yusi.service.plaza.impl;

import com.aseubel.yusi.common.constant.PromptKey;
import com.aseubel.yusi.common.constant.EmotionType;
import com.aseubel.yusi.service.ai.prompt.PromptManager;
import com.aseubel.yusi.service.ai.prompt.PromptSnapshot;
import com.aseubel.yusi.service.plaza.EmotionAnalyzer;
import com.aseubel.yusi.service.ai.model.ModelRouteContext;
import com.aseubel.yusi.service.ai.model.ModelRouteContextHolder;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


/**
 * 情感分析服务实现类
 * 直接调用 LLM API 进行情感分析，避免使用 langchain4j 的 AiServices 开销
 * 
 * @author Aseubel
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmotionAnalyzerImpl implements EmotionAnalyzer {

    // 使用专门的情感分析场景，避免与通用逻辑分析混用
    private final ChatModel chatModel;
    private final PromptManager promptManager;

    @Override
    public String analyzeEmotion(String content) {
        if (content == null || content.trim().isEmpty()) {
            return EmotionType.NEUTRAL.code();
        }

        try {
            PromptSnapshot snapshot = promptManager.getSnapshot(PromptKey.EMOTION_ANALYSIS);
            // 构建简洁的 prompt
            String prompt = buildEmotionPrompt(content, snapshot);

            // 直接调用专门的情感分析模型
            UserMessage userMessage = UserMessage.from(prompt);
            AiMessage aiMessage;
            try {
                ModelRouteContextHolder
                        .set(ModelRouteContext.builder()
                                .scene(PromptKey.EMOTION_ANALYSIS.getKey())
                                .prompt(snapshot)
                                .build());
                aiMessage = chatModel.chat(userMessage).aiMessage();
            } finally {
                ModelRouteContextHolder.clear();
            }

            String result = aiMessage.text();

            // 清理结果（去除空白和换行）
            String cleanedResult = result.trim().replaceAll("[\\n\\r]", "");

            // 验证返回的情感类别是否有效
            EmotionType emotion = EmotionType.fromModelValue(cleanedResult);
            log.debug("情感分析结果: {} -> {}", cleanedResult, emotion.code());
            return emotion.code();

        } catch (Exception e) {
            log.error("情感分析失败，使用默认值Neutral: {}", e.getMessage());
            return EmotionType.NEUTRAL.code();
        }
    }

    /**
     * 构建情感分析的 prompt
     * 使用极简格式减少 token 消耗，提升响应速度
     */
    private String buildEmotionPrompt(String content, PromptSnapshot snapshot) {
        String template = snapshot == null ? "" : snapshot.template();
        return template.replace("{{content}}", content);
    }
}
