package com.aseubel.yusi.service.ai.model;

import com.aseubel.yusi.service.ai.model.strategy.FailOverSelectionStrategy;
import com.aseubel.yusi.service.ai.model.strategy.LeastLatencySelectionStrategy;
import com.aseubel.yusi.service.ai.model.strategy.ModelSelectionStrategy;
import com.aseubel.yusi.service.ai.model.strategy.RoundRobinSelectionStrategy;
import com.aseubel.yusi.service.ai.model.strategy.WeightedRandomSelectionStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ModelStrategyRegistry {

    private final RoundRobinSelectionStrategy roundRobinSelectionStrategy;
    private final LeastLatencySelectionStrategy leastLatencySelectionStrategy;
    private final WeightedRandomSelectionStrategy weightedRandomSelectionStrategy;
    private final FailOverSelectionStrategy failOverSelectionStrategy;

    public Map<ModelSelectionStrategyType, ModelSelectionStrategy> build() {
        Map<ModelSelectionStrategyType, ModelSelectionStrategy> map = new EnumMap<>(ModelSelectionStrategyType.class);
        map.put(ModelSelectionStrategyType.ROUND_ROBIN, roundRobinSelectionStrategy);
        map.put(ModelSelectionStrategyType.LEAST_LATENCY, leastLatencySelectionStrategy);
        map.put(ModelSelectionStrategyType.WEIGHTED_RANDOM, weightedRandomSelectionStrategy);
        map.put(ModelSelectionStrategyType.FAIL_OVER, failOverSelectionStrategy);
        return map;
    }
}
