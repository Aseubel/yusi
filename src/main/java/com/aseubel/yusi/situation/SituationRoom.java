package com.aseubel.yusi.situation;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.*;

@Data
@Builder
public class SituationRoom {
    private String code;
    private RoomStatus status;
    private String scenarioId;
    private Set<String> members;
    private Map<String, String> submissions;
    private LocalDateTime createdAt;

    public boolean allSubmitted() {
        return members != null && submissions != null && submissions.size() == members.size() && members.size() >= 2;
    }
}