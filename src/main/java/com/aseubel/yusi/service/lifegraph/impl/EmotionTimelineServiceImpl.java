package com.aseubel.yusi.service.lifegraph.impl;

import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.pojo.entity.LifeGraphEntity;
import com.aseubel.yusi.pojo.entity.LifeGraphMention;
import com.aseubel.yusi.repository.DiaryRepository;
import com.aseubel.yusi.repository.LifeGraphEntityRepository;
import com.aseubel.yusi.repository.LifeGraphMentionRepository;
import com.aseubel.yusi.service.lifegraph.EmotionTimelineService;
import com.aseubel.yusi.service.lifegraph.dto.EmotionTimeline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmotionTimelineServiceImpl implements EmotionTimelineService {

    private final DiaryRepository diaryRepository;
    private final LifeGraphEntityRepository entityRepository;
    private final LifeGraphMentionRepository mentionRepository;

    private static final Map<String, Double> EMOTION_INTENSITY = Map.ofEntries(
            Map.entry("joy", 0.8),
            Map.entry("excitement", 0.9),
            Map.entry("happiness", 0.7),
            Map.entry("contentment", 0.5),
            Map.entry("calm", 0.3),
            Map.entry("neutral", 0.0),
            Map.entry("anxiety", 0.6),
            Map.entry("sadness", 0.7),
            Map.entry("anger", 0.8),
            Map.entry("fear", 0.7),
            Map.entry("frustration", 0.6),
            Map.entry("disappointment", 0.5),
            Map.entry("loneliness", 0.6),
            Map.entry("stress", 0.5),
            Map.entry("tired", 0.3)
    );

    private static final Map<String, String> EMOTION_CATEGORIES = Map.ofEntries(
            Map.entry("joy", "positive"),
            Map.entry("excitement", "positive"),
            Map.entry("happiness", "positive"),
            Map.entry("contentment", "positive"),
            Map.entry("calm", "positive"),
            Map.entry("anxiety", "negative"),
            Map.entry("sadness", "negative"),
            Map.entry("anger", "negative"),
            Map.entry("fear", "negative"),
            Map.entry("frustration", "negative"),
            Map.entry("disappointment", "negative"),
            Map.entry("loneliness", "negative"),
            Map.entry("stress", "negative"),
            Map.entry("tired", "neutral"),
            Map.entry("neutral", "neutral")
    );

    @Override
    public EmotionTimeline getEmotionTimeline(String userId, LocalDate startDate, LocalDate endDate) {
        List<Diary> diaries = diaryRepository.findAllByUserId(userId);

        List<Diary> filteredDiaries = diaries.stream()
                .filter(d -> d.getEntryDate() != null)
                .filter(d -> startDate == null || !d.getEntryDate().isBefore(startDate))
                .filter(d -> endDate == null || !d.getEntryDate().isAfter(endDate))
                .sorted(Comparator.comparing(Diary::getEntryDate))
                .collect(Collectors.toList());

        List<EmotionTimeline.EmotionPoint> emotionPoints = filteredDiaries.stream()
                .map(this::buildEmotionPoint)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<EmotionTimeline.EmotionTrigger> triggers = getEmotionTriggers(userId, 10);

        EmotionTimeline.EmotionSummary summary = buildEmotionSummary(emotionPoints);

        return EmotionTimeline.builder()
                .emotionPoints(emotionPoints)
                .triggers(triggers)
                .summary(summary)
                .build();
    }

    @Override
    public List<EmotionTimeline.EmotionTrigger> getEmotionTriggers(String userId, int limit) {
        List<LifeGraphEntity> emotionEntities = entityRepository.findByUserIdAndType(userId, LifeGraphEntity.EntityType.Emotion);

        if (emotionEntities.isEmpty()) {
            return Collections.emptyList();
        }

        List<LifeGraphEntity> allEntities = entityRepository.findTop50ByUserIdOrderByMentionCountDesc(userId);
        Map<Long, LifeGraphEntity> entityMap = allEntities.stream()
                .collect(Collectors.toMap(LifeGraphEntity::getId, e -> e));

        Map<Long, List<LifeGraphMention>> emotionMentions = new HashMap<>();
        for (LifeGraphEntity emotion : emotionEntities) {
            List<LifeGraphMention> mentions = mentionRepository
                    .findTop200ByUserIdAndEntityIdOrderByCreatedAtDesc(userId, emotion.getId());
            emotionMentions.put(emotion.getId(), mentions);
        }

        Map<String, EmotionTimeline.EmotionTrigger> triggerMap = new HashMap<>();

        for (LifeGraphEntity emotion : emotionEntities) {
            List<LifeGraphMention> mentions = emotionMentions.get(emotion.getId());
            if (mentions == null) continue;

            for (LifeGraphMention mention : mentions) {
                String diaryId = mention.getDiaryId();

                for (LifeGraphEntity entity : allEntities) {
                    if (entity.getType() == LifeGraphEntity.EntityType.Emotion) continue;

                    List<LifeGraphMention> entityMentions = mentionRepository
                            .findTop200ByUserIdAndEntityIdOrderByCreatedAtDesc(userId, entity.getId());

                    boolean sameDiary = entityMentions.stream()
                            .anyMatch(em -> diaryId.equals(em.getDiaryId()));

                    if (sameDiary) {
                        String triggerKey = entity.getDisplayName();
                        EmotionTimeline.EmotionTrigger trigger = triggerMap.computeIfAbsent(
                                triggerKey,
                                k -> EmotionTimeline.EmotionTrigger.builder()
                                        .triggerEntity(entity.getDisplayName())
                                        .triggerType(entity.getType().name())
                                        .occurrenceCount(0)
                                        .avgIntensityChange(0.0)
                                        .relatedEmotions(new ArrayList<>())
                                        .build()
                        );

                        trigger.setOccurrenceCount(trigger.getOccurrenceCount() + 1);
                        if (!trigger.getRelatedEmotions().contains(emotion.getDisplayName())) {
                            trigger.getRelatedEmotions().add(emotion.getDisplayName());
                        }
                    }
                }
            }
        }

        return triggerMap.values().stream()
                .sorted((a, b) -> Integer.compare(b.getOccurrenceCount(), a.getOccurrenceCount()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    private EmotionTimeline.EmotionPoint buildEmotionPoint(Diary diary) {
        String emotionStr = diary.getEmotion();
        if (emotionStr == null || emotionStr.isBlank()) {
            return null;
        }

        List<String> emotions = parseEmotions(emotionStr);
        if (emotions.isEmpty()) {
            return null;
        }

        String primaryEmotion = emotions.get(0);
        List<String> secondaryEmotions = emotions.size() > 1 
                ? emotions.subList(1, emotions.size()) 
                : Collections.emptyList();

        double intensity = calculateIntensity(primaryEmotion);

        return EmotionTimeline.EmotionPoint.builder()
                .date(diary.getEntryDate())
                .primaryEmotion(primaryEmotion)
                .intensity(intensity)
                .secondaryEmotions(secondaryEmotions)
                .diaryId(diary.getDiaryId())
                .context(extractContext(diary))
                .build();
    }

    private List<String> parseEmotions(String emotionStr) {
        if (emotionStr == null || emotionStr.isBlank()) {
            return Collections.emptyList();
        }

        return Arrays.stream(emotionStr.split("[,，、;；\\s]+", -1))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(e -> !e.isEmpty())
                .collect(Collectors.toList());
    }

    private double calculateIntensity(String emotion) {
        return EMOTION_INTENSITY.getOrDefault(emotion.toLowerCase(), 0.5);
    }

    private String extractContext(Diary diary) {
        String title = diary.getTitle();
        return title != null && !title.isBlank() ? title : null;
    }

    private EmotionTimeline.EmotionSummary buildEmotionSummary(List<EmotionTimeline.EmotionPoint> points) {
        if (points.isEmpty()) {
            return EmotionTimeline.EmotionSummary.builder()
                    .dominantEmotion("neutral")
                    .avgIntensity(0.0)
                    .totalEmotionEvents(0)
                    .emotionTrend("stable")
                    .frequentEmotions(Collections.emptyList())
                    .build();
        }

        Map<String, Long> emotionCounts = points.stream()
                .collect(Collectors.groupingBy(
                        EmotionTimeline.EmotionPoint::getPrimaryEmotion,
                        Collectors.counting()
                ));

        String dominantEmotion = emotionCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("neutral");

        double avgIntensity = points.stream()
                .mapToDouble(EmotionTimeline.EmotionPoint::getIntensity)
                .average()
                .orElse(0.0);

        String emotionTrend = calculateEmotionTrend(points);

        List<String> frequentEmotions = emotionCounts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        return EmotionTimeline.EmotionSummary.builder()
                .dominantEmotion(dominantEmotion)
                .avgIntensity(Math.round(avgIntensity * 100.0) / 100.0)
                .totalEmotionEvents(points.size())
                .emotionTrend(emotionTrend)
                .frequentEmotions(frequentEmotions)
                .build();
    }

    private String calculateEmotionTrend(List<EmotionTimeline.EmotionPoint> points) {
        if (points.size() < 3) {
            return "stable";
        }

        int positiveCount = 0;
        int negativeCount = 0;

        for (EmotionTimeline.EmotionPoint point : points) {
            String category = EMOTION_CATEGORIES.getOrDefault(
                    point.getPrimaryEmotion().toLowerCase(), 
                    "neutral"
            );
            if ("positive".equals(category)) {
                positiveCount++;
            } else if ("negative".equals(category)) {
                negativeCount++;
            }
        }

        int recentSize = Math.min(points.size() / 3, 10);
        List<EmotionTimeline.EmotionPoint> recentPoints = points.subList(
                points.size() - recentSize, 
                points.size()
        );

        int recentPositive = 0;
        int recentNegative = 0;
        for (EmotionTimeline.EmotionPoint point : recentPoints) {
            String category = EMOTION_CATEGORIES.getOrDefault(
                    point.getPrimaryEmotion().toLowerCase(), 
                    "neutral"
            );
            if ("positive".equals(category)) {
                recentPositive++;
            } else if ("negative".equals(category)) {
                recentNegative++;
            }
        }

        double overallRatio = (double) positiveCount / (positiveCount + negativeCount + 1);
        double recentRatio = (double) recentPositive / (recentPositive + recentNegative + 1);

        if (recentRatio > overallRatio + 0.15) {
            return "improving";
        } else if (recentRatio < overallRatio - 0.15) {
            return "declining";
        }
        return "stable";
    }
}