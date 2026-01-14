package com.aseubel.yusi.service.stats;

import com.aseubel.yusi.pojo.dto.stats.PlatformStatsResponse;

/**
 * 平台统计服务接口
 */
public interface StatsService {

    /**
     * 获取平台统计数据
     */
    PlatformStatsResponse getPlatformStats();
}
