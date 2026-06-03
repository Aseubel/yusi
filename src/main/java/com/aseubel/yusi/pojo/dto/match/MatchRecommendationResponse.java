package com.aseubel.yusi.pojo.dto.match;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchRecommendationResponse {

    private Long matchId;
    private String counterpartUserId;
    private String counterpartUserName;
    private String recommendationLetter;
    private String counterpartLetter;
    private String reason;
    private String timingReason;
    private String iceBreaker;
    private Integer score;
    private Integer myStatus;
    private Integer counterpartStatus;
    private Boolean matched;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 匹配后破冰话题列表（F9.1） */
    private List<String> iceBreakers;

    /** 推荐的情景室描述（F9.1），null 表示无推荐 */
    private String suggestedScenario;
}
