package com.aseubel.yusi.pojo.dto.stats;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 平台统计数据响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformStatsResponse {

    /**
     * 用户总数
     */
    private long userCount;

    /**
     * 日记/对话总数
     */
    private long diaryCount;

    /**
     * 灵魂卡片总数
     */
    private long soulCardCount;

    /**
     * 情景室总数
     */
    private long roomCount;

    /**
     * 共鸣总数
     */
    private long resonanceCount;
}
