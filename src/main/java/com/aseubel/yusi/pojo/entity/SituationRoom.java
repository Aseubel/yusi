package com.aseubel.yusi.pojo.entity;

import com.aseubel.yusi.pojo.dto.situation.SituationReport;
import com.aseubel.yusi.pojo.contant.RoomStatus;
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
    private SituationReport report;

    public boolean allSubmitted() {
        return members != null && submissions != null && submissions.size() == members.size() && members.size() >= 2;
    }
}