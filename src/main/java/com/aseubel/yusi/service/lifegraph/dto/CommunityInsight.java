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
public class CommunityInsight {

    private String communityId;

    private String communityName;

    private CommunityType type;

    private String description;

    private List<EntitySummary> entities;

    private int entityCount;

    private int relationCount;

    private double cohesion;

    private LocalDate firstActiveDate;

    private LocalDate lastActiveDate;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EntitySummary {
        private Long entityId;
        private String displayName;
        private String entityType;
        private int mentionCount;
        private double centralityScore;
    }

    public enum CommunityType {
        WORK,
        FAMILY,
        FRIENDS,
        HOBBY,
        OTHER
    }
}
