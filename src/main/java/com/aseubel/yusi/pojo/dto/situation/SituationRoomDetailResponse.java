package com.aseubel.yusi.pojo.dto.situation;

import com.aseubel.yusi.pojo.contant.RoomStatus;
import com.aseubel.yusi.pojo.dto.situation.SituationReport;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SituationRoomDetailResponse {

    private String code;

    private RoomStatus status;

    private String ownerId;

    private String scenarioId;

    private Set<String> members;

    private Map<String, String> submissions;

    private Map<String, Boolean> submissionVisibility;

    private Set<String> cancelVotes;

    private LocalDateTime createdAt;

    private SituationReport report;

    private Map<String, String> memberNames;

    private ScenarioDetail scenario;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScenarioDetail {
        private String id;
        private String title;
        private String description;
        private String summary;
    }
}
