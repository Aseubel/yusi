package com.aseubel.yusi.service.cognition.impl;

import com.aseubel.yusi.common.constant.PromptKey;
import com.aseubel.yusi.service.cognition.ImageUnderstandingService;
import com.aseubel.yusi.service.ai.prompt.PromptManager;
import com.aseubel.yusi.service.ai.model.ModelRouteContext;
import com.aseubel.yusi.service.ai.model.ModelRouteContextHolder;
import com.aseubel.yusi.service.oss.OssService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LangChainImageUnderstandingService implements ImageUnderstandingService {

    private final ChatModel chatModel;
    private final OssService ossService;
    private final PromptManager promptManager;

    @Override
    public String describe(String userId, List<String> imageObjectKeys) {
        if (imageObjectKeys == null || imageObjectKeys.isEmpty()) {
            return null;
        }
        try {
            List<Content> contents = new ArrayList<>();
            contents.add(TextContent.from(promptManager.getPrompt(PromptKey.IMAGE_UNDERSTANDING)));
            for (String objectKey : imageObjectKeys) {
                if (objectKey != null && !objectKey.isBlank()) {
                    contents.add(ImageContent.from(URI.create(ossService.generateOwnedUrl(objectKey, userId))));
                }
            }
            if (contents.size() == 1) {
                return null;
            }
            ModelRouteContextHolder.set(ModelRouteContext.builder()
                    .scene(PromptKey.IMAGE_UNDERSTANDING.getKey())
                    .userId(userId)
                    .build());
            AiMessage message;
            try {
                message = chatModel.chat(UserMessage.from(contents)).aiMessage();
            } finally {
                ModelRouteContextHolder.clear();
            }
            return message == null ? null : message.text();
        } catch (Exception e) {
            log.warn("图片认知理解失败，继续文本认知链路: {}", e.getMessage());
            return null;
        }
    }
}
