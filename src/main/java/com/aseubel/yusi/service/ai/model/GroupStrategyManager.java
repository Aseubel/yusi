package com.aseubel.yusi.service.ai.model;

import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class GroupStrategyManager {

    private final ModelRoutingProperties properties;
    private final ModelConfigCenter modelConfigCenter;
    private final RedissonClient redissonClient;
    private final Map<String, ModelSelectionStrategyType> localCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        redissonClient.getTopic(properties.getStrategyChannel()).addListener(GroupStrategyEvent.class, (channel, msg) -> {
            if (msg == null || msg.getGroup() == null || msg.getStrategy() == null) {
                return;
            }
            localCache.put(msg.getGroup(), msg.getStrategy());
        });
    }

    public ModelSelectionStrategyType getStrategy(String group) {
        ModelSelectionStrategyType local = localCache.get(group);
        if (local != null) {
            return local;
        }
        RMap<String, String> map = redissonClient.getMap(properties.getGroupStrategyMapKey());
        String redisValue = map.get(group);
        if (redisValue != null && !redisValue.isBlank()) {
            try {
                ModelSelectionStrategyType strategy = ModelSelectionStrategyType
                        .valueOf(redisValue.toUpperCase(Locale.ROOT));
                localCache.put(group, strategy);
                return strategy;
            } catch (IllegalArgumentException ignored) {
            }
        }
        ModelRoutingProperties.GroupDefinition groupDefinition = modelConfigCenter.getEffectiveConfig().getGroups().get(group);
        return groupDefinition == null ? ModelSelectionStrategyType.ROUND_ROBIN : groupDefinition.getStrategy();
    }

    public void switchStrategy(String group, ModelSelectionStrategyType strategy) {
        redissonClient.<String, String>getMap(properties.getGroupStrategyMapKey()).put(group, strategy.name());
        localCache.put(group, strategy);
        GroupStrategyEvent event = GroupStrategyEvent.builder()
                .group(group)
                .strategy(strategy)
                .timestamp(System.currentTimeMillis())
                .build();
        redissonClient.getTopic(properties.getStrategyChannel()).publish(event);
    }
}
