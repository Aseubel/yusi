package com.aseubel.yusi.pojo.dto.match;

import lombok.Data;

/**
 * @author Aseubel
 * @date 2026/1/3 下午10:43
 */
@Data
public class MatchSettingsRequest {
    private Boolean enabled;
    private String intent;
}
