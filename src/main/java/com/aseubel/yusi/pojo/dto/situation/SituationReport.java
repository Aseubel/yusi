package com.aseubel.yusi.pojo.dto.situation;

import com.aseubel.yusi.pojo.entity.SituationRoom;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    private List<PublicSubmission> publicSubmissions;

    public List<PublicSubmission> extractPublicSubmissions(SituationRoom room) {
        // Populate public submissions
        List<SituationReport.PublicSubmission> publicSubmissions = new ArrayList<>();
        if (room.getSubmissions() != null) {
            for (Map.Entry<String, String> entry : room.getSubmissions().entrySet()) {
                String uid = entry.getKey();
                String content = entry.getValue();
                Boolean isPublic = room.getSubmissionVisibility() != null ? room.getSubmissionVisibility().get(uid) : false;
                if (Boolean.TRUE.equals(isPublic)) {
                    publicSubmissions.add(SituationReport.PublicSubmission.builder()
                            .userId(uid)
                            .content(content)
                            .build());
                }
            }
        }
        this.setPublicSubmissions(publicSubmissions);
        return publicSubmissions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PublicSubmission {
        private String userId;
        private String content;
    }
}