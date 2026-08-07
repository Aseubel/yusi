package com.aseubel.yusi.service.ai.model;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Conservative, provider-independent token estimate used before routing.
 * A provider response remains the source of truth for usage and cost.
 */
@Component
public class ModelTokenEstimator {

    private static final int MESSAGE_OVERHEAD = 4;
    private static final int IMAGE_TOKEN_ESTIMATE = 256;

    public int estimate(ChatRequest request) {
        if (request == null) {
            return 0;
        }
        int estimate = estimateMessages(request.messages());
        List<ToolSpecification> tools = request.toolSpecifications();
        if (tools != null) {
            for (ToolSpecification tool : tools) {
                estimate = add(estimate, estimateText(String.valueOf(tool)));
            }
        }
        return estimate;
    }

    public Integer requestedOutputTokens(ChatRequest request) {
        if (request == null) {
            return null;
        }
        ChatRequestParameters parameters = request.parameters();
        Integer maxOutputTokens = parameters == null ? request.maxOutputTokens() : parameters.maxOutputTokens();
        if (parameters instanceof OpenAiChatRequestParameters openAiParameters
                && openAiParameters.maxCompletionTokens() != null) {
            maxOutputTokens = smallerPositive(maxOutputTokens, openAiParameters.maxCompletionTokens());
        }
        return maxOutputTokens;
    }

    int estimateMessages(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int estimate = 0;
        for (ChatMessage message : messages) {
            estimate = add(estimate, MESSAGE_OVERHEAD + estimateMessage(message));
        }
        return estimate;
    }

    private int estimateMessage(ChatMessage message) {
        if (message == null) {
            return 0;
        }
        if (message instanceof SystemMessage systemMessage) {
            return estimateText(systemMessage.text());
        }
        if (message instanceof UserMessage userMessage) {
            int estimate = 0;
            if (userMessage.contents() != null) {
                for (Content content : userMessage.contents()) {
                    estimate = add(estimate, estimateContent(content));
                }
            }
            return estimate;
        }
        if (message instanceof AiMessage aiMessage) {
            int estimate = estimateText(aiMessage.text());
            estimate = add(estimate, estimateText(aiMessage.thinking()));
            if (aiMessage.toolExecutionRequests() != null) {
                for (ToolExecutionRequest request : aiMessage.toolExecutionRequests()) {
                    estimate = add(estimate, estimateText(request.name()));
                    estimate = add(estimate, estimateText(request.arguments()));
                }
            }
            return estimate;
        }
        if (message instanceof ToolExecutionResultMessage toolResult) {
            return add(estimateText(toolResult.toolName()), estimateText(toolResult.text()));
        }
        return estimateText(message.toString());
    }

    private int estimateContent(Content content) {
        if (content instanceof TextContent textContent) {
            return estimateText(textContent.text());
        }
        if (content instanceof ImageContent) {
            return IMAGE_TOKEN_ESTIMATE;
        }
        return estimateText(String.valueOf(content));
    }

    private int estimateText(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int cjkCodePoints = 0;
        int otherCodePoints = 0;
        for (int codePoint : text.codePoints().toArray()) {
            if (isCjk(codePoint)) {
                cjkCodePoints++;
            } else {
                otherCodePoints++;
            }
        }
        return cjkCodePoints + (otherCodePoints + 3) / 4;
    }

    private boolean isCjk(int codePoint) {
        return (codePoint >= 0x3040 && codePoint <= 0x30ff)
                || (codePoint >= 0x3400 && codePoint <= 0x4dbf)
                || (codePoint >= 0x4e00 && codePoint <= 0x9fff)
                || (codePoint >= 0xac00 && codePoint <= 0xd7af)
                || (codePoint >= 0xf900 && codePoint <= 0xfaff);
    }

    private int add(int left, int right) {
        if (right <= 0 || left >= Integer.MAX_VALUE - right) {
            return left >= Integer.MAX_VALUE - right ? Integer.MAX_VALUE : left;
        }
        return left + right;
    }

    private Integer smallerPositive(Integer first, Integer second) {
        if (first == null || first <= 0) {
            return second == null || second <= 0 ? null : second;
        }
        if (second == null || second <= 0) {
            return first;
        }
        return Math.min(first, second);
    }
}
