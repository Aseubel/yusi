package com.aseubel.yusi.pojo.dto.situation;

import lombok.Data;

@Data
public class SubmitNarrativeRequest {
    private String code;
    private String userId;
    private String narrative;
}