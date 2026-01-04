package com.aseubel.yusi.service.plaza.impl;

import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.pojo.contant.CardType;
import com.aseubel.yusi.pojo.contant.ResonanceType;
import com.aseubel.yusi.pojo.entity.SoulCard;
import com.aseubel.yusi.pojo.entity.SoulResonance;
import com.aseubel.yusi.repository.SoulCardRepository;
import com.aseubel.yusi.repository.SoulResonanceRepository;
import com.aseubel.yusi.service.plaza.SoulPlazaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class SoulPlazaServiceImpl implements SoulPlazaService {

    private final SoulCardRepository cardRepository;
    private final SoulResonanceRepository resonanceRepository;

    @Override
    public SoulCard submitToPlaza(String userId, String content, String originId, CardType type) {
        if (content == null || content.length() < 5) {
            throw new BusinessException("内容太短");
        }

        // Basic emotion analysis placeholder (In full version, call AI here)
        String emotion = "Neutral";

        SoulCard card = SoulCard.builder()
                .userId(userId)
                .content(content) // Anonymized content
                .originId(originId)
                .type(type)
                .emotion(emotion)
                .resonanceCount(0)
                .createdAt(LocalDateTime.now())
                .build();

        return cardRepository.save(card);
    }

    @Override
    public Page<SoulCard> getFeed(String userId, int page, int size, String emotion) {
        // Exclude own posts
        PageRequest pageRequest = PageRequest.of(page - 1, size);
        
        if (emotion != null && !emotion.isEmpty() && !emotion.equals("All")) {
            return cardRepository.findByUserIdNotAndEmotionOrderByCreatedAtDesc(userId, emotion, pageRequest);
        }
        
        // Note: Logic here is simple reverse chronological.
        // V2.1 should implement more complex 'Soul Matching' ranking.
        return cardRepository.findByUserIdNotOrderByCreatedAtDesc(userId, pageRequest);
    }

    @Override
    @Transactional
    public SoulResonance resonate(String userId, Long cardId, ResonanceType type) {
        SoulCard card = cardRepository.findById(cardId)
                .orElseThrow(() -> new BusinessException("Card not found"));

        if (card.getUserId().equals(userId)) {
            throw new BusinessException("不能与自己共鸣");
        }

        if (resonanceRepository.existsByCardIdAndUserId(cardId, userId)) {
            throw new BusinessException("已经共鸣过了");
        }

        SoulResonance resonance = SoulResonance.builder()
                .cardId(cardId)
                .userId(userId)
                .type(type)
                .createdAt(LocalDateTime.now())
                .build();

        resonanceRepository.save(resonance);

        // Update count
        card.setResonanceCount(card.getResonanceCount() + 1);
        cardRepository.save(card);

        return resonance;
    }
}
