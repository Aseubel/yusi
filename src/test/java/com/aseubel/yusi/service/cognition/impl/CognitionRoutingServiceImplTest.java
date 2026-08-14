package com.aseubel.yusi.service.cognition.impl;

import com.aseubel.yusi.common.constant.PromptKey;
import com.aseubel.yusi.pojo.dto.cognition.CognitionIngestCommand;
import com.aseubel.yusi.service.ai.model.ModelRouteContext;
import com.aseubel.yusi.service.ai.model.ModelRouteContextHolder;
import com.aseubel.yusi.service.ai.prompt.PromptManager;
import com.aseubel.yusi.service.ai.prompt.PromptSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CognitionRoutingServiceImplTest {

    @AfterEach
    void clearRouteContext() {
        ModelRouteContextHolder.clear();
    }

    @Test
    void routeUsesSamePromptSnapshotForRequestAndModelTraceContext() {
        ChatModel chatModel = mock(ChatModel.class);
        PromptManager promptManager = mock(PromptManager.class);
        AtomicReference<ModelRouteContext> capturedContext = new AtomicReference<>();
        AtomicReference<UserMessage> capturedMessage = new AtomicReference<>();
        when(promptManager.getSnapshot(PromptKey.COGNITION_ROUTING)).thenReturn(
                new PromptSnapshot("cognition-routing", "v7", "zh-CN", "route prompt"));
        when(chatModel.chat(any(UserMessage.class))).thenAnswer(invocation -> {
            capturedContext.set(ModelRouteContextHolder.get());
            capturedMessage.set(invocation.getArgument(0));
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("{}"))
                    .build();
        });

        CognitionRoutingServiceImpl service = new CognitionRoutingServiceImpl(
                chatModel, promptManager, new ObjectMapper());
        service.route(CognitionIngestCommand.builder()
                .userId("user-1")
                .maskedText("今天完成了一个重要任务")
                .build());

        assertNotNull(capturedContext.get());
        assertEquals("cognition-routing", capturedContext.get().getPromptKey());
        assertEquals("v7", capturedContext.get().getPromptVersion());
        assertEquals("zh-CN", capturedContext.get().getPromptLocale());
        assertTrue(capturedMessage.get().singleText().contains("route prompt"));
    }
}
