package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.dto.model.ModelMetricAggregate;
import com.aseubel.yusi.pojo.dto.model.ModelMetricBucket;
import com.aseubel.yusi.pojo.dto.model.ModelMetricTrendQuery;
import com.aseubel.yusi.pojo.entity.ModelCallTrace;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

public interface ModelCallTraceMetricsRepository {

    ModelMetricAggregate aggregate(Specification<ModelCallTrace> specification);

    List<ModelMetricBucket> aggregateTrend(Specification<ModelCallTrace> specification,
            ModelMetricTrendQuery.Bucket bucket);
}
