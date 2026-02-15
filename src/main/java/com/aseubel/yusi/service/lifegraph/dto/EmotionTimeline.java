package com.aseubel.yusi.service.lifegraph.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmotionTimeline {

    private List<EmotionPoint> emotionPoints;

    private List<EmotionTrigger> triggers;

    private EmotionSummary summary;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmotionPoint {
        private LocalDate date;
        private String primaryEmotion;
        private double intensity;
        private List<String> secondaryEmotions;
        private String diaryId;
        private String context;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmotionTrigger {
        private String triggerEntity;
        private String triggerType;
        private int occurrenceCount;
        private double avgIntensityChange;
        private List<String> relatedEmotions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmotionSummary {
        private String dominantEmotion;
        private double avgIntensity;
        private int totalEmotionEvents;
        private String emotionTrend;
        private List<String> frequentEmotions;
    }
}
