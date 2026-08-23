package com.aseubel.yusi.config.ai;

import com.aseubel.yusi.common.event.MessageSavedEvent;
import com.aseubel.yusi.pojo.entity.ChatMemoryMessage;
import com.aseubel.yusi.repository.ChatMemoryMessageRepository;
import com.aseubel.yusi.service.ai.chat.ContextBuilderService;
import com.aseubel.yusi.service.ai.model.ModelRouteContext;
import com.aseubel.yusi.service.ai.model.ModelRouteContextHolder;
import com.aseubel.yusi.service.oss.OssService;
import com.aseubel.yusi.redis.service.IRedisService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersistentChatMemoryStoreCorrelationTest {

    @Mock
    private ChatMemoryMessageRepository messageRepository;

    @Mock
    private IRedisService redisService;

    @Mock
    private ContextBuilderService contextBuilderService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private OssService ossService;

    @AfterEach
    void clearRouteContext() {
        ModelRouteContextHolder.clear();
    }

    @Test
    void persistsCurrentAgentRunOnChatMemoryRowsAndSavedEvent() {
        when(messageRepository.findByMemoryIdOrderByCreatedAtDesc(eq("user-1"), any(Pageable.class)))
                .thenReturn(List.of());
        PersistentChatMemoryStore store = new PersistentChatMemoryStore(
                messageRepository, redisService, contextBuilderService, eventPublisher, ossService);
        ModelRouteContextHolder.set(ModelRouteContext.builder()
                .userId("user-1")
                .runId("chat-run-1")
                .build());

        store.updateMessages("user-1", List.of(UserMessage.from("hello")));
        store.updateMessages("user-1", List.of(AiMessage.from("hi")));

        ArgumentCaptor<ChatMemoryMessage> messageCaptor = ArgumentCaptor.forClass(ChatMemoryMessage.class);
        verify(messageRepository, org.mockito.Mockito.times(2)).save(messageCaptor.capture());
        assertEquals("chat-run-1", messageCaptor.getAllValues().get(0).getRunId());
        assertEquals("chat-run-1", messageCaptor.getAllValues().get(1).getRunId());

        ArgumentCaptor<MessageSavedEvent> eventCaptor = ArgumentCaptor.forClass(MessageSavedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals("chat-run-1", eventCaptor.getValue().getRunId());
    }
}
