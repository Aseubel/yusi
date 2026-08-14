package com.aseubel.yusi;

import com.aseubel.yusi.common.event.EmotionPlazaCognitionIngestEvent;
import com.aseubel.yusi.common.event.PlazaCardChangedEvent;
import com.aseubel.yusi.pojo.constant.CardType;
import com.aseubel.yusi.pojo.entity.SoulCard;
import com.aseubel.yusi.repository.SoulCardRepository;
import com.aseubel.yusi.repository.SoulResonanceRepository;
import com.aseubel.yusi.redis.service.IRedisService;
import com.aseubel.yusi.service.ai.mask.MaskResult;
import com.aseubel.yusi.service.ai.mask.SensitiveDataMaskService;
import com.aseubel.yusi.service.plaza.EmotionAnalyzer;
import com.aseubel.yusi.service.plaza.impl.SoulPlazaServiceImpl;
import com.aseubel.yusi.service.task.TaskExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SoulPlazaTest {

    @Mock
    private SoulCardRepository cardRepository;

    @Mock
    private SoulResonanceRepository resonanceRepository;

    @Mock
    private EmotionAnalyzer emotionAnalyzer;

    @Mock
    private IRedisService redissonService;

    @Mock
    private SensitiveDataMaskService sensitiveDataMaskService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private TaskExecutionService taskExecutionService;

    @InjectMocks
    private SoulPlazaServiceImpl soulPlazaService;

    @BeforeEach
    void setUp() {
        lenient().when(emotionAnalyzer.analyzeEmotion(any(String.class))).thenReturn("Calm");
        lenient().when(sensitiveDataMaskService.mask(any(String.class)))
                .thenAnswer(invocation -> MaskResult.noMask(invocation.getArgument(0)));
    }

    @Test
    void getFeed_shouldFilterByEmotion_whenEmotionProvided() {
        String userId = "user1";
        String emotion = "Joy";
        int page = 1;
        int size = 10;
        when(cardRepository.findByUserIdNotAndEmotionOrderByCreatedAtDesc(eq(userId), eq(emotion), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));

        soulPlazaService.getFeed(userId, page, size, emotion);

        verify(cardRepository).findByUserIdNotAndEmotionOrderByCreatedAtDesc(
                eq(userId), eq(emotion), any(PageRequest.class));
    }

    @Test
    void getFeed_shouldNotFilterByEmotion_whenEmotionIsNull() {
        String userId = "user1";
        String emotion = null;
        int page = 1;
        int size = 10;
        when(cardRepository.findByUserIdNotOrderByCreatedAtDesc(eq(userId), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));

        soulPlazaService.getFeed(userId, page, size, emotion);

        verify(cardRepository).findByUserIdNotOrderByCreatedAtDesc(
                eq(userId), any(PageRequest.class));
    }

    @Test
    void getFeed_shouldNotFilterByEmotion_whenEmotionIsAll() {
        String userId = "user1";
        String emotion = "All";
        int page = 1;
        int size = 10;
        when(cardRepository.findByUserIdNotOrderByCreatedAtDesc(eq(userId), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));

        soulPlazaService.getFeed(userId, page, size, emotion);

        verify(cardRepository).findByUserIdNotOrderByCreatedAtDesc(
                eq(userId), any(PageRequest.class));
    }

    @Test
    void submitPublishesPlazaLifeGraphEventAndKeepsEmotionEvent() {
        when(cardRepository.save(any(SoulCard.class))).thenAnswer(invocation -> {
            SoulCard card = invocation.getArgument(0);
            card.setId(42L);
            return card;
        });

        SoulCard saved = soulPlazaService.submitToPlaza(
                "user-1", "这是用户自己的长期生活记录", "diary-1", CardType.DIARY);

        assertEquals(42L, saved.getId());
        var events = publishedEvents();
        PlazaCardChangedEvent lifeGraphEvent = events.stream()
                .filter(PlazaCardChangedEvent.class::isInstance)
                .map(PlazaCardChangedEvent.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals(PlazaCardChangedEvent.Type.WRITE, lifeGraphEvent.getType());
        assertEquals("PLAZA", lifeGraphEvent.getCommand().getSourceType());
        assertEquals("42", lifeGraphEvent.getCommand().getSourceId());
        assertEquals("这是用户自己的长期生活记录", lifeGraphEvent.getCommand().getMaskedText());
        assertTrue(events.stream().anyMatch(EmotionPlazaCognitionIngestEvent.class::isInstance));
        verify(sensitiveDataMaskService, times(2)).mask("这是用户自己的长期生活记录");
    }

    @Test
    void updatePublishesPlazaModifyEventForTheSavedCard() {
        SoulCard card = SoulCard.builder()
                .id(42L)
                .userId("user-1")
                .content("旧的卡片内容")
                .createdAt(LocalDateTime.of(2026, 8, 13, 10, 0))
                .build();
        when(cardRepository.findById(42L)).thenReturn(Optional.of(card));
        when(cardRepository.save(any(SoulCard.class))).thenAnswer(invocation -> invocation.getArgument(0));

        soulPlazaService.updateCard("user-1", 42L, "修改后的长期生活内容");

        PlazaCardChangedEvent event = publishedEvents().stream()
                .filter(PlazaCardChangedEvent.class::isInstance)
                .map(PlazaCardChangedEvent.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals(PlazaCardChangedEvent.Type.MODIFY, event.getType());
        assertEquals("PLAZA", event.getCommand().getSourceType());
        assertEquals("42", event.getCommand().getSourceId());
        assertEquals("修改后的长期生活内容", event.getCommand().getMaskedText());
    }

    @Test
    void deletePublishesPlazaDeleteEventWithoutContent() {
        SoulCard card = SoulCard.builder()
                .id(42L)
                .userId("user-1")
                .content("需要删除的卡片内容")
                .build();
        when(cardRepository.findById(42L)).thenReturn(Optional.of(card));

        soulPlazaService.deleteCard("user-1", 42L);

        PlazaCardChangedEvent event = publishedEvents().stream()
                .filter(PlazaCardChangedEvent.class::isInstance)
                .map(PlazaCardChangedEvent.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals(PlazaCardChangedEvent.Type.DELETE, event.getType());
        assertEquals("PLAZA", event.getCommand().getSourceType());
        assertEquals("42", event.getCommand().getSourceId());
        assertFalse(event.getCommand().getMaskedText() != null);
        verify(resonanceRepository).deleteByCardId(42L);
        verify(cardRepository).delete(card);
    }

    private List<ApplicationEvent> publishedEvents() {
        var events = new ArrayList<ApplicationEvent>();
        org.mockito.Mockito.verify(eventPublisher, org.mockito.Mockito.atLeastOnce())
                .publishEvent(org.mockito.Mockito.any(ApplicationEvent.class));
        var captor = org.mockito.ArgumentCaptor.forClass(ApplicationEvent.class);
        org.mockito.Mockito.verify(eventPublisher,
                org.mockito.Mockito.atLeastOnce()).publishEvent(captor.capture());
        events.addAll(captor.getAllValues());
        return events;
    }
}
