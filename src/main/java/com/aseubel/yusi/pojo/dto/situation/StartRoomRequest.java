package com.aseubel.yusi.pojo.dto.situation;

import lombok.Data;

@Data
public class StartRoomRequest {
    private String code;
    private String scenarioId;
    private String ownerId;
}