package com.aseubel.yusi.service.lifegraph.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LifeGraphMergeSuggestion {
    private Long entityIdA;
    private Long entityIdB;
    private String nameA;
    private String nameB;
    private String type;
    private Double score;
    private String reason;
    private String recommendedMasterName;
}
