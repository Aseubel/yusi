package com.aseubel.yusi.service.plaza.impl;

import com.aseubel.yusi.service.plaza.EmotionAnalyzer;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;

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

    // 使用专门的情感分析模型，避免使用复杂的 situation-analysis 场景
    private final ChatModel emotionModel;

    // 有效的情感类别列表
    private static final Set<String> VALID_EMOTIONS = Set.of(
            "Joy", "Sadness", "Anxiety", "Love", "Anger",
            "Fear", "Hope", "Calm", "Confusion", "Neutral");

    @Override
    public String analyzeEmotion(String content) {
        if (content == null || content.trim().isEmpty()) {
            return "Neutral";
        }

        try {
            // 构建简洁的 prompt
            String prompt = buildEmotionPrompt(content);
            
            // 直接调用专门的情感分析模型
            UserMessage userMessage = UserMessage.from(prompt);
            AiMessage aiMessage = emotionModel.chat(userMessage).aiMessage();
            
            String result = aiMessage.text();
            
            // 清理结果（去除空白和换行）
            String cleanedResult = result.trim().replaceAll("[\\n\\r]", "");

            // 验证返回的情感类别是否有效
            if (VALID_EMOTIONS.contains(cleanedResult)) {
                log.debug("情感分析成功: {}", cleanedResult);
                return cleanedResult;
            }

            // 如果返回的不是标准类别，尝试部分匹配
            for (String validEmotion : VALID_EMOTIONS) {
                if (cleanedResult.toLowerCase().contains(validEmotion.toLowerCase())) {
                    log.debug("情感分析部分匹配: {} -> {}", cleanedResult, validEmotion);
                    return validEmotion;
                }
            }

            log.warn("AI返回了非标准情感类别: '{}', 使用默认值Neutral", cleanedResult);
            return "Neutral";

        } catch (Exception e) {
            log.error("情感分析失败，使用默认值Neutral: {}", e.getMessage());
            return "Neutral";
        }
    }

    /**
     * 构建情感分析的 prompt
     * 使用极简格式减少 token 消耗，提升响应速度
     */
    private String buildEmotionPrompt(String content) {
        return String.format("分析情感，只返回类别名：Joy/Sadness/Anxiety/Love/Anger/Fear/Hope/Calm/Confusion/Neutral\n\n内容：%s", content);
    }
}
