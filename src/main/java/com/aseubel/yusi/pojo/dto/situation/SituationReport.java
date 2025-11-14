package com.aseubel.yusi.pojo.dto.situation;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SituationReport {
    @Data
    @Builder
    public static class PersonalSketch {
        private String userId;
        private String sketch;
    }

    @Data
    @Builder
    public static class PairCompatibility {
        private String userA;
        private String userB;
        private int score;
        private String reason;
    }

    private String scenarioId;
    private List<PersonalSketch> personal;
    private List<PairCompatibility> pairs;
}