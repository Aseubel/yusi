package com.aseubel.yusi.observability.health;

import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.service.ai.model.ModelConfigCenter;
import com.aseubel.yusi.service.ai.model.ModelInstance;
import com.aseubel.yusi.service.ai.model.ModelInstanceRegistry;
import com.aseubel.yusi.service.ai.model.ModelRuntimeState;
import com.aseubel.yusi.service.ai.model.ModelStateCenter;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reports routing availability from local configuration and circuit-breaker
 * state. It never invokes a chat or embedding model.
 */
@Component("modelGateway")
@ConditionalOnBean({ModelConfigCenter.class, ModelInstanceRegistry.class, ModelStateCenter.class})
public class ModelGatewayHealthIndicator implements HealthIndicator {

    private final ModelConfigCenter configCenter;
    private final ModelInstanceRegistry instanceRegistry;
    private final ModelStateCenter stateCenter;

    public ModelGatewayHealthIndicator(ModelConfigCenter configCenter,
            ModelInstanceRegistry instanceRegistry,
            ModelStateCenter stateCenter) {
        this.configCenter = configCenter;
        this.instanceRegistry = instanceRegistry;
        this.stateCenter = stateCenter;
    }

    @Override
    public Health health() {
        ModelRoutingProperties config = configCenter.getEffectiveConfig();
        if (config == null) {
            return unavailable("configuration");
        }

        Set<String> requiredTiers = requiredTiers(config);
        if (requiredTiers.isEmpty()) {
            return unavailable("no_route");
        }

        int requiredTierCount = 0;
        for (String tierId : requiredTiers) {
            List<ModelInstance> members = instanceRegistry.getTierMembers(tierId);
            if (members == null || members.isEmpty()) {
                continue;
            }
            requiredTierCount++;
            List<String> ids = members.stream().map(ModelInstance::getId).toList();
            Map<String, ModelRuntimeState> states = stateCenter.snapshot(ids);
            boolean tierAvailable = false;
            for (ModelInstance member : members) {
                ModelRuntimeState state = states == null ? null : states.get(member.getId());
                if (state == null || state.isAvailable() || isHalfOpen(state)) {
                    tierAvailable = true;
                }
            }
            if (!tierAvailable) {
                return Health.down()
                        .withDetail("dependency", "modelGateway")
                        .withDetail("classification", "unavailable")
                        .build();
            }
        }

        if (requiredTierCount == 0) {
            return unavailable("unavailable");
        }
        return Health.up()
                .withDetail("dependency", "modelGateway")
                .withDetail("classification", "available")
                .build();
    }

    private Set<String> requiredTiers(ModelRoutingProperties config) {
        Set<String> tiers = new LinkedHashSet<>();
        if (config.getRoutes() != null) {
            config.getRoutes().stream()
                    .filter(route -> route != null && route.isEnabled())
                    .map(route -> route.getPrimaryTier())
                    .filter(tier -> tier != null && !tier.isBlank())
                    .forEach(tiers::add);
        }
        if (tiers.isEmpty() && config.getDefaultTier() != null && !config.getDefaultTier().isBlank()) {
            tiers.add(config.getDefaultTier());
        }
        return tiers;
    }

    private boolean isHalfOpen(ModelRuntimeState state) {
        return "HALF_OPEN".equalsIgnoreCase(state.getPhase());
    }

    private Health unavailable(String classification) {
        return Health.down()
                .withDetail("dependency", "modelGateway")
                .withDetail("classification", classification)
                .build();
    }
}
