package com.aseubel.yusi.pojo.dto.match;

import lombok.Data;

/**
 * @author Aseubel
 * @date 2026/1/3 下午10:45
 */
@Data
public class MatchActionRequest {
    private Integer action; // 1: Interested, 2: Skipped
}
