package com.aseubel.yusi;

import com.aseubel.yusi.repository.SoulCardRepository;
import com.aseubel.yusi.repository.SoulResonanceRepository;
import com.aseubel.yusi.service.plaza.impl.SoulPlazaServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SoulPlazaTest {

    @Mock
    private SoulCardRepository cardRepository;

    @Mock
    private SoulResonanceRepository resonanceRepository;

    @InjectMocks
    private SoulPlazaServiceImpl soulPlazaService;

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
}
