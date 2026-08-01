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
    private long activeUsersToday;
    private long activeUsers7d;
    private long activeUsers30d;
}
