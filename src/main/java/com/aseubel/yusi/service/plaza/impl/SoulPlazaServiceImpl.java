package com.aseubel.yusi.service.plaza.impl;

import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.pojo.contant.CardType;
import com.aseubel.yusi.pojo.contant.ResonanceType;
import com.aseubel.yusi.pojo.entity.SoulCard;
import com.aseubel.yusi.pojo.entity.SoulResonance;
import com.aseubel.yusi.repository.SoulCardRepository;
import com.aseubel.yusi.repository.SoulResonanceRepository;
import com.aseubel.yusi.redis.annotation.QueryCache;
import com.aseubel.yusi.redis.annotation.UpdateCache;
import com.aseubel.yusi.redis.service.IRedisService;
import com.aseubel.yusi.service.plaza.EmotionAnalyzer;
import com.aseubel.yusi.service.plaza.SoulPlazaService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SoulPlazaServiceImpl implements SoulPlazaService {

    private final SoulCardRepository cardRepository;
    private final SoulResonanceRepository resonanceRepository;
    private final EmotionAnalyzer emotionAnalyzer;
    private final IRedisService redissonService;

    // 有效的情感类别列表
    private static final Set<String> VALID_EMOTIONS = Set.of(
            "Joy", "Sadness", "Anxiety", "Love", "Anger",
            "Fear", "Hope", "Calm", "Confusion", "Neutral");

    @Override
    @UpdateCache(key = "'plaza:feed:' + #userId + ':*'", evictOnly = true)
    public SoulCard submitToPlaza(String userId, String content, String originId, CardType type) {
        if (content == null || content.length() < 5) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "内容太短");
        }

        // 使用AI进行情感分析
        String emotion = analyzeContentEmotion(content);
        log.info("情感分析结果 - userId: {}, emotion: {}", userId, emotion);

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

    /**
     * 使用AI分析内容的情感倾向
     * 
     * @param content 待分析的内容
     * @return 情感类别（Joy, Sadness, Anxiety, Love, Anger, Fear, Hope, Calm, Confusion,
     *         Neutral）
     */
    private String analyzeContentEmotion(String content) {
        try {
            String result = emotionAnalyzer.analyzeEmotion(content);

            // 清理结果（去除空白和换行）
            String cleanedResult = result.trim().replaceAll("[\\n\\r]", "");

            // 验证返回的情感类别是否有效
            if (VALID_EMOTIONS.contains(cleanedResult)) {
                return cleanedResult;
            }

            // 如果返回的不是标准类别，尝试部分匹配
            for (String validEmotion : VALID_EMOTIONS) {
                if (cleanedResult.toLowerCase().contains(validEmotion.toLowerCase())) {
                    return validEmotion;
                }
            }

            log.warn("AI返回了非标准情感类别: '{}', 使用默认值Neutral", cleanedResult);
            return "Neutral";

        } catch (Exception e) {
            log.error("情感分析失败，使用默认值Neutral: {}", e.getMessage());
            return "Neutral";
        }
    }

    @Override
    @QueryCache(key = "'plaza:feed:' + #userId + ':' + #page + ':' + #size + ':' + (#emotion == null ? 'All' : #emotion)", ttl = 60)
    public Page<SoulCard> getFeed(String userId, int page, int size, String emotion) {
        // Exclude own posts
        PageRequest pageRequest = PageRequest.of(page - 1, size);
        Page<SoulCard> result;

        if (emotion != null && !emotion.isEmpty() && !emotion.equals("All")) {
            result = cardRepository.findByUserIdNotAndEmotionOrderByCreatedAtDesc(userId, emotion, pageRequest);
        } else {
            // 实现灵魂匹配排序算法
            // 综合考虑：共鸣数量、时间衰减、情感多样性
            result = getSoulMatchedFeed(userId, pageRequest);
        }

        if (result.hasContent()) {
            List<Long> cardIds = result.getContent().stream().map(SoulCard::getId).collect(Collectors.toList());
            List<SoulResonance> resonances = resonanceRepository.findByUserIdAndCardIdIn(userId, cardIds);
            Set<Long> resonatedCardIds = resonances.stream().map(SoulResonance::getCardId).collect(Collectors.toSet());

            result.getContent().forEach(card -> {
                card.setIsResonated(resonatedCardIds.contains(card.getId()));
            });
        }

        return result;
    }

    /**
     * 灵魂匹配Feed算法
     * 
     * 排序因素：
     * 1. 热度分数 = 共鸣数量 × 共鸣权重
     * 2. 时间衰减 = 越新的帖子分数越高
     * 3. 情感亲和力 = 基于用户历史共鸣的情感偏好进行加权
     * 
     * 最终分数 = (热度分数 + 时间分数) × 情感亲和权重
     */
    private Page<SoulCard> getSoulMatchedFeed(String userId, PageRequest pageRequest) {
        // 获取较大范围的帖子用于排序（取3倍分页大小以确保排序质量）
        int fetchSize = Math.min(pageRequest.getPageSize() * 3, 100);
        PageRequest fetchRequest = PageRequest.of(0, fetchSize);

        // 获取候选帖子
        List<SoulCard> candidates = cardRepository
                .findByUserIdNotOrderByCreatedAtDesc(userId, fetchRequest)
                .getContent();

        if (candidates.isEmpty()) {
            return new PageImpl<>(List.of(), pageRequest, 0);
        }

        // 获取用户的情感偏好（基于共鸣历史）
        List<SoulResonance> userResonances = resonanceRepository.findByUserId(userId);
        Set<String> preferredEmotions = userResonances.stream()
                .map(resonance -> cardRepository.findById(resonance.getCardId()))
                .filter(Optional::isPresent)
                .map(opt -> opt.get().getEmotion())
                .collect(Collectors.toSet());

        LocalDateTime now = LocalDateTime.now();

        // 计算每个帖子的灵魂匹配分数并排序
        List<SoulCard> rankedCards = candidates.stream()
                .sorted(Comparator.comparingDouble((SoulCard card) -> {
                    // 热度分数：共鸣数量（对数变换避免极端值）
                    double popularityScore = Math.log1p(card.getResonanceCount()) * 10;

                    // 时间衰减分数：24小时内满分，之后指数衰减
                    long hoursAgo = ChronoUnit.HOURS.between(card.getCreatedAt(), now);
                    double timeScore = 100 * Math.exp(-hoursAgo / 72.0); // 72小时半衰期

                    // 情感亲和权重：如果用户曾对该情感类型的帖子共鸣过，给予加权
                    double emotionAffinity = preferredEmotions.contains(card.getEmotion()) ? 1.5 : 1.0;

                    // 最终分数
                    return (popularityScore + timeScore) * emotionAffinity;
                }).reversed())
                .collect(Collectors.toList());

        // 分页处理
        int start = pageRequest.getPageNumber() * pageRequest.getPageSize();
        int end = Math.min(start + pageRequest.getPageSize(), rankedCards.size());

        if (start >= rankedCards.size()) {
            return new PageImpl<>(List.of(), pageRequest, rankedCards.size());
        }

        List<SoulCard> pagedCards = rankedCards.subList(start, end);
        return new PageImpl<>(pagedCards, pageRequest, rankedCards.size());
    }

    @Override
    @Transactional
    @UpdateCache(key = "'plaza:feed:' + #userId + ':*'", evictOnly = true)
    public SoulResonance resonate(String userId, Long cardId, ResonanceType type) {
        SoulCard card = cardRepository.findById(cardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Card not found"));

        if (card.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不能与自己共鸣");
        }

        if (resonanceRepository.existsByCardIdAndUserId(cardId, userId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "已经共鸣过了");
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

    @Override
    @QueryCache(key = "'plaza:my:' + #userId + ':' + #page + ':' + #size", ttl = 60)
    public Page<SoulCard> getMyCards(String userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page - 1, size);
        return cardRepository.findByUserIdOrderByCreatedAtDesc(userId, pageRequest);
    }

    @Override
    @Transactional
    @UpdateCache(key = "'plaza:my:' + #userId + ':*'", evictOnly = true)
    public SoulCard updateCard(String userId, Long cardId, String content) {
        SoulCard card = cardRepository.findById(cardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "卡片不存在"));

        if (!card.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权修改此卡片");
        }

        if (content == null || content.length() < 5) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "内容太短");
        }

        // 重新分析情绪
        String emotion = analyzeContentEmotion(content);
        card.setContent(content);
        card.setEmotion(emotion);

        return cardRepository.save(card);
    }

    @Override
    @Transactional
    @UpdateCache(key = "'plaza:my:' + #userId + ':*'", evictOnly = true)
    public void deleteCard(String userId, Long cardId) {
        SoulCard card = cardRepository.findById(cardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "卡片不存在"));

        if (!card.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权删除此卡片");
        }

        // 同时删除关联的共鸣
        resonanceRepository.deleteByCardId(cardId);
        cardRepository.delete(card);
    }
}
