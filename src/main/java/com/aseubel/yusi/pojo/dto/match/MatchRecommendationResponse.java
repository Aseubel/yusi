package com.aseubel.yusi.pojo.dto.match;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
}
