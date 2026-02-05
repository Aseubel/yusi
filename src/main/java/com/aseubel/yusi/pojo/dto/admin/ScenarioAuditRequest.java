package com.aseubel.yusi.pojo.dto.admin;

import lombok.Data;

@Data
public class ScenarioAuditRequest {
    private boolean approved;
    private String rejectReason;
}
