package com.aseubel.yusi.pojo.dto.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelMetricTrendQuery {

    public enum Bucket {
        HOUR,
        DAY
    }

    private LocalDateTime from;
    private LocalDateTime to;
    private String scene;
    private String userId;
    private String runId;
    private String promptKey;
    private String promptVersion;
    private String modelTier;
    private String provider;
    private String model;
    private Boolean fallbackUsed;
    private String status;
    private Bucket bucket = Bucket.HOUR;

    public ModelCallTraceQuery toTraceQuery() {
        return ModelCallTraceQuery.builder()
                .from(from)
                .to(to)
                .scene(scene)
                .userId(userId)
                .runId(runId)
                .promptKey(promptKey)
                .promptVersion(promptVersion)
                .modelTier(modelTier)
                .provider(provider)
                .model(model)
                .fallbackUsed(fallbackUsed)
                .status(status)
                .build();
    }
}
