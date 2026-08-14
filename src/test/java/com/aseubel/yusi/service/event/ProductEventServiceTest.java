package com.aseubel.yusi.service.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.aseubel.yusi.pojo.entity.ProductEvent;
import com.aseubel.yusi.pojo.entity.ProductEventScope;
import com.aseubel.yusi.repository.ProductEventRepository;
import com.aseubel.yusi.repository.ProductEventScopeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductEventServiceTest {

    @Mock
    private ProductEventRepository eventRepository;

    @Mock
    private ProductEventScopeRepository scopeRepository;

    @Test
    void sameIdempotencyKeyReturnsExistingEventWithoutSecondInsert() {
        AtomicReference<ProductEvent> persisted = new AtomicReference<>();
        when(eventRepository.findByIdempotencyKey("message:1"))
                .thenAnswer(invocation -> Optional.ofNullable(persisted.get()));
        when(eventRepository.save(any(ProductEvent.class)))
                .thenAnswer(invocation -> {
                    ProductEvent event = invocation.getArgument(0);
                    persisted.set(event);
                    return event;
                });

        ProductEventService service = service();
        ProductEventCommand command = command("message:1", Map.of("messageId", 1L));

        ProductEvent first = service.record(command);
        ProductEvent second = service.record(command);

        assertEquals(first.getEventId(), second.getEventId());
        verify(eventRepository, times(1)).save(any(ProductEvent.class));
        verify(scopeRepository, times(1)).saveAll(any());
    }

    @Test
    void rejectsSensitivePayloadKeysBeforePersistence() {
        ProductEventService service = service();

        assertThrows(IllegalArgumentException.class,
                () -> service.record(command("message:2", Map.of("content", "private text"))));

        verify(eventRepository, times(0)).save(any(ProductEvent.class));
    }

    @Test
    void createsParticipantScopesForConnectionEvent() {
        when(eventRepository.findByIdempotencyKey("connection:1")).thenReturn(Optional.empty());
        when(eventRepository.save(any(ProductEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ProductEventService service = service();

        ProductEvent event = service.record(ProductEventCommand.builder()
                .eventName("connection.accepted")
                .source("connection")
                .sensitivity("RESTRICTED")
                .userId("user-a")
                .actorUserId("user-a")
                .matchId(7L)
                .connectionId(99L)
                .idempotencyKey("connection:1")
                .scopeUserIds(Set.of("user-a", "user-b"))
                .payload(Map.of("action", "ACCEPT"))
                .build());

        assertEquals("connection.accepted", event.getEventName());
        ArgumentCaptor<Iterable<ProductEventScope>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(scopeRepository).saveAll(captor.capture());
        assertEquals(2, ((Iterable<?>) captor.getValue()).spliterator().getExactSizeIfKnown());
    }

    private ProductEventService service() {
        return new ProductEventService(eventRepository, scopeRepository, new ObjectMapper());
    }

    private ProductEventCommand command(String idempotencyKey, Map<String, Object> payload) {
        return ProductEventCommand.builder()
                .eventName("chat.message_created")
                .source("chat")
                .sensitivity("RESTRICTED")
                .userId("user-1")
                .actorUserId("user-1")
                .idempotencyKey(idempotencyKey)
                .scopeUserIds(Set.of("user-1"))
                .payload(payload)
                .build();
    }
}
