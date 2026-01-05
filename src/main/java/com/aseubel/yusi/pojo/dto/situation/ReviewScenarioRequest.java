package com.aseubel.yusi.pojo.dto.situation;

import lombok.Data;

@Data
public class ReviewScenarioRequest {
    private String scenarioId;
    private Integer status;
    private String rejectReason;
}
