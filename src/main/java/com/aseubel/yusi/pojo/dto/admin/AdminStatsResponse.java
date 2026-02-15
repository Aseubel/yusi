package com.aseubel.yusi.pojo.dto.admin;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminStatsResponse {
    private long totalUsers;
    private long totalDiaries;
    private long pendingScenarios;
    private long totalRooms;
    private long pendingSuggestions;
}
