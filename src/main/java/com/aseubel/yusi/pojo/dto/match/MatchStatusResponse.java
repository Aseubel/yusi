package com.aseubel.yusi.pojo.dto.match;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 匹配状态响应 DTO
 * 
 * @author Aseubel
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchStatusResponse {

    /**
     * 是否开启匹配
     */
    private Boolean enabled;

    /**
     * 交友意图
     */
    private String intent;

    /**
     * 用户日记数量
     */
    private Long diaryCount;

    /**
     * 待处理匹配数（双方都还未表态）
     */
    private Long pendingMatches;

    /**
     * 已完成匹配数（双方都感兴趣）
     */
    private Long completedMatches;

    /**
     * 下次匹配时间说明
     */
    private String nextMatchTime;

    /**
     * 是否满足开启匹配的条件
     */
    private Boolean canEnable;

    /**
     * 不满足条件时的提示信息
     */
    private String enableHint;
}
