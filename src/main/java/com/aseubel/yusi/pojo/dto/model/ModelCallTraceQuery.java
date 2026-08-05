package com.aseubel.yusi.pojo.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelCallTraceQuery {
    private LocalDateTime from;
    private LocalDateTime to;
    private String scene;
    private String language;
    private String modelTier;
    private String provider;
    private String model;
    private Boolean fallbackUsed;
    private String status;
    @Builder.Default
    private int page = 0;
    @Builder.Default
    private int size = 20;
}
