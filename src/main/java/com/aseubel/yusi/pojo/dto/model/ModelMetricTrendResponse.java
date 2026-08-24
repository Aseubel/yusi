package com.aseubel.yusi.pojo.dto.model;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record ModelMetricTrendResponse(
        ModelMetricTrendQuery.Bucket bucket,
        LocalDateTime from,
        LocalDateTime to,
        List<ModelMetricBucket> items) {

    public ModelMetricTrendResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
