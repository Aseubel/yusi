package com.aseubel.yusi.pojo.dto.situation;

import lombok.Data;

@Data
public class CreateRoomRequest {
    private String ownerId;
    private int maxMembers;
}