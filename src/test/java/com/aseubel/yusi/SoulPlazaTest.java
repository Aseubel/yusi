package com.aseubel.yusi;

import com.aseubel.yusi.pojo.entity.SoulCard;
import com.aseubel.yusi.repository.SoulCardRepository;
import com.aseubel.yusi.service.plaza.impl.SoulPlazaServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

class SoulPlazaTest {

    @Mock
    private SoulCardRepository cardRepository;

    @InjectMocks
    private SoulPlazaServiceImpl soulPlazaService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void getFeed_shouldFilterByEmotion_whenEmotionProvided() {
        String userId = "user1";
        String emotion = "Joy";
        int page = 1;
        int size = 10;

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

        soulPlazaService.getFeed(userId, page, size, emotion);

        verify(cardRepository).findByUserIdNotOrderByCreatedAtDesc(
                eq(userId), any(PageRequest.class));
    }
}
