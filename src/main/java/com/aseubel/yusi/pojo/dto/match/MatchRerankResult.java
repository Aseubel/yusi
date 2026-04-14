package com.aseubel.yusi.pojo.dto.match;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchRerankResult {

    private Boolean resonance;
    private Integer score;
    private String reason;
    private String timingReason;
    private String iceBreaker;
}
