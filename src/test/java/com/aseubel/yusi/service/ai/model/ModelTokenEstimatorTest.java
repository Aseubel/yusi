package com.aseubel.yusi.service.ai.model;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModelTokenEstimatorTest {

    private final ModelTokenEstimator estimator = new ModelTokenEstimator();

    @Test
    void estimatesMessagesAndToolArgumentsBeforeProviderInvocation() {
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        SystemMessage.from("You are a helpful assistant."),
                        UserMessage.from("请查一下我的订单状态"),
                        AiMessage.from(ToolExecutionRequest.builder()
                                .id("call-1")
                                .name("lookup_order")
                                .arguments("{\"orderId\":\"A-100\"}")
                                .build())))
                .build();

        assertThat(estimator.estimate(request)).isGreaterThan(20);
    }

    @Test
    void usesRequestOutputLimitForBudgetReservation() {
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(UserMessage.from("hello")))
                .parameters(OpenAiChatRequestParameters.builder().maxOutputTokens(128).build())
                .build();

        assertThat(estimator.requestedOutputTokens(request)).isEqualTo(128);
    }
}
