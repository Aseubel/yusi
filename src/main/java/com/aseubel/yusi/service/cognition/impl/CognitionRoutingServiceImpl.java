package com.aseubel.yusi.service.cognition.impl;

import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.common.constant.PromptKey;
import com.aseubel.yusi.pojo.dto.cognition.CognitionIngestCommand;
import com.aseubel.yusi.pojo.dto.cognition.CognitionRoutingResult;
import com.aseubel.yusi.service.ai.PromptManager;
import com.aseubel.yusi.service.ai.model.ModelRouteContext;
import com.aseubel.yusi.service.ai.model.ModelRouteContextHolder;
import com.aseubel.yusi.service.cognition.CognitionRoutingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CognitionRoutingServiceImpl implements CognitionRoutingService {

    @Qualifier("chatModel")
    private final ChatModel chatModel;
    private final PromptManager promptManager;
    private final ObjectMapper objectMapper;

    @Override
    public CognitionRoutingResult route(CognitionIngestCommand command) {
        if (command == null || StrUtil.isBlank(command.getMaskedText())) {
            return CognitionRoutingResult.builder().build();
        }
        try {
            String prompt = buildPrompt(command);
            AiMessage aiMessage;
            try {
                ModelRouteContextHolder.set(ModelRouteContext.builder()
                        .scene(PromptKey.COGNITION_ROUTING.getKey())
                        .language("zh")
                        .build());
                aiMessage = chatModel.chat(UserMessage.from(prompt)).aiMessage();
            } finally {
                ModelRouteContextHolder.clear();
            }

            String json = extractJsonObject(aiMessage.text());
            return objectMapper.readValue(json, CognitionRoutingResult.class);
        } catch (Exception e) {
            log.warn("认知分流抽取失败，回退为最小近期状态摘要: userId={}, sourceType={}",
                    command.getUserId(), command.getSourceType(), e);
            return CognitionRoutingResult.builder()
                    .midMemorySummary(truncate(command.getMaskedText(), 180))
                    .midMemoryImportance(command.getConfidenceHint() != null ? command.getConfidenceHint() : 0.5)
                    .build();
        }
    }

    private String buildPrompt(CognitionIngestCommand command) {
        return """
                %s

                输入源类型：%s
                标题：%s
                主题：%s
                地点：%s
                时间：%s

                文本：
                %s
                """.formatted(
                promptManager.getPrompt(PromptKey.COGNITION_ROUTING),
                StrUtil.blankToDefault(command.getSourceType(), ""),
                StrUtil.blankToDefault(command.getTitle(), ""),
                StrUtil.blankToDefault(command.getTopic(), ""),
                StrUtil.blankToDefault(command.getPlaceName(), ""),
                command.getTimestamp() != null ? command.getTimestamp() : "",
                command.getMaskedText());
    }

    private String extractJsonObject(String raw) {
        if (StrUtil.isBlank(raw)) {
            return "{}";
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return "{}";
        }
        return raw.substring(start, end + 1);
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
