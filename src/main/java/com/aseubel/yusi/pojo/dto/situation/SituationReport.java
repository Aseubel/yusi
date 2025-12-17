package com.aseubel.yusi.pojo.dto.situation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SituationReport {
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PersonalSketch {
        private String userId;
        private String sketch;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
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