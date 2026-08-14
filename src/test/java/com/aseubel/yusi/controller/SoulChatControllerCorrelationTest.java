package com.aseubel.yusi.controller;

import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.pojo.dto.chat.SendMessageRequest;
import com.aseubel.yusi.pojo.entity.SoulConnection;
import com.aseubel.yusi.pojo.entity.SoulMatch;
import com.aseubel.yusi.pojo.entity.SoulMessage;
import com.aseubel.yusi.repository.SoulMatchRepository;
import com.aseubel.yusi.repository.SoulMessageRepository;
import com.aseubel.yusi.service.match.SoulConnectionLifecycleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SoulChatControllerCorrelationTest {

    private final SoulMessageRepository messageRepository = mock(SoulMessageRepository.class);
    private final SoulMatchRepository matchRepository = mock(SoulMatchRepository.class);
    private final SoulConnectionLifecycleService connectionLifecycleService = mock(SoulConnectionLifecycleService.class);
    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final WebSocketController webSocketController = mock(WebSocketController.class);

    @AfterEach
    void clearUser() {
        UserContext.clear();
    }

    @Test
    void sendMessageCopiesCurrentConnectionIdToPersistedMessage() {
        UserContext.setUserId("user-a");
        SoulMatch match = SoulMatch.builder()
                .id(7L)
                .userAId("user-a")
                .userBId("user-b")
                .build();
        when(matchRepository.findById(7L)).thenReturn(Optional.of(match));
        when(connectionLifecycleService.findByMatchId(7L))
                .thenReturn(Optional.of(SoulConnection.builder().id(99L).build()));
        when(messageRepository.save(any(SoulMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SoulChatController controller = new SoulChatController();
        ReflectionTestUtils.setField(controller, "messageRepository", messageRepository);
        ReflectionTestUtils.setField(controller, "matchRepository", matchRepository);
        ReflectionTestUtils.setField(controller, "connectionLifecycleService", connectionLifecycleService);
        ReflectionTestUtils.setField(controller, "messagingTemplate", messagingTemplate);
        ReflectionTestUtils.setField(controller, "webSocketController", webSocketController);

        SendMessageRequest request = new SendMessageRequest();
        request.setMatchId(7L);
        request.setContent("hello");
        Response<SoulMessage> response = controller.sendMessage(request);

        assertEquals(99L, response.getData().getConnectionId());
        ArgumentCaptor<SoulMessage> captor = ArgumentCaptor.forClass(SoulMessage.class);
        verify(messageRepository).save(captor.capture());
        assertEquals(99L, captor.getValue().getConnectionId());
    }
}
