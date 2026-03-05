package com.aseubel.yusi.service.ai.model;

import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMap;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class ModelStateCenter {

    private enum Phase {
        UP, HALF_OPEN, DOWN
    }

    private static class LocalWindow {
        private volatile long firstRequestAt = System.currentTimeMillis();
        private volatile long totalRequests;
        private volatile long successRequests;
        private volatile long failureRequests;
        private volatile double avgLatencyMs;
        private volatile int consecutiveFailures;
        private volatile int consecutiveSuccesses;
        private volatile long nextProbeAt;
        private volatile String lastError;
        private volatile Phase phase = Phase.UP;
        private final AtomicBoolean probing = new AtomicBoolean(false);
    }

    private final ModelRoutingProperties properties;
    private final ModelConfigCenter modelConfigCenter;
    private final RedissonClient redissonClient;
    private final Map<String, LocalWindow> localWindows = new ConcurrentHashMap<>();
    private final Map<String, ModelRuntimeState> remoteStateCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        redissonClient.getTopic(properties.getStateChannel()).addListener(ModelStateEvent.class, (channel, message) -> {
            if (message == null || message.getState() == null || message.getInstanceId() == null) {
                return;
            }
            remoteStateCache.put(message.getInstanceId(), message.getState());
        });
    }

    public boolean allowRequest(String instanceId) {
        LocalWindow window = localWindows.computeIfAbsent(instanceId, id -> new LocalWindow());
        long now = System.currentTimeMillis();
        if (window.phase == Phase.UP) {
            return true;
        }
        if (window.phase == Phase.DOWN && now >= window.nextProbeAt) {
            if (window.probing.compareAndSet(false, true)) {
                window.phase = Phase.HALF_OPEN;
                return true;
            }
            return false;
        }
        if (window.phase == Phase.HALF_OPEN) {
            return window.probing.compareAndSet(false, true);
        }
        return false;
    }

    public void recordSuccess(String instanceId, String modelName, long latencyMs) {
        LocalWindow window = localWindows.computeIfAbsent(instanceId, id -> new LocalWindow());
        synchronized (window) {
            window.totalRequests++;
            window.successRequests++;
            window.consecutiveFailures = 0;
            window.consecutiveSuccesses++;
            window.avgLatencyMs = window.avgLatencyMs == 0 ? latencyMs : (window.avgLatencyMs * 0.8 + latencyMs * 0.2);
            if (window.phase == Phase.HALF_OPEN
                    && window.consecutiveSuccesses >= modelConfigCenter.getEffectiveConfig().getRecoverySuccessThreshold()) {
                window.phase = Phase.UP;
                window.nextProbeAt = 0L;
            }
            publishState(instanceId, modelName, window, "SUCCESS");
        }
        window.probing.set(false);
    }

    public void recordFailure(String instanceId, String modelName, long latencyMs, Throwable throwable) {
        LocalWindow window = localWindows.computeIfAbsent(instanceId, id -> new LocalWindow());
        synchronized (window) {
            window.totalRequests++;
            window.failureRequests++;
            window.consecutiveFailures++;
            window.consecutiveSuccesses = 0;
            window.avgLatencyMs = window.avgLatencyMs == 0 ? latencyMs : (window.avgLatencyMs * 0.8 + latencyMs * 0.2);
            window.lastError = throwable == null ? "" : throwable.getMessage();
            if (window.phase == Phase.HALF_OPEN
                    || window.consecutiveFailures >= modelConfigCenter.getEffectiveConfig().getFailureThreshold()) {
                window.phase = Phase.DOWN;
                window.nextProbeAt = System.currentTimeMillis()
                        + modelConfigCenter.getEffectiveConfig().getRecoveryProbeIntervalMs();
            }
            publishState(instanceId, modelName, window, "FAILURE");
        }
        window.probing.set(false);
    }

    public Map<String, ModelRuntimeState> snapshot(Collection<String> instanceIds) {
        RMap<String, ModelRuntimeState> stateMap = redissonClient.getMap(properties.getInstanceStateMapKey());
        Map<String, ModelRuntimeState> result = new HashMap<>();
        for (String instanceId : instanceIds) {
            ModelRuntimeState cached = remoteStateCache.get(instanceId);
            if (cached != null) {
                result.put(instanceId, cached);
                continue;
            }
            ModelRuntimeState state = stateMap.get(instanceId);
            if (state != null) {
                result.put(instanceId, state);
                continue;
            }
            LocalWindow window = localWindows.get(instanceId);
            if (window != null) {
                result.put(instanceId, toState(instanceId, "", window));
            }
        }
        return result;
    }

    public List<ModelRuntimeState> listStates() {
        return redissonClient.<String, ModelRuntimeState>getMap(properties.getInstanceStateMapKey())
                .readAllMap()
                .values()
                .stream()
                .toList();
    }

    private void publishState(String instanceId, String modelName, LocalWindow window, String action) {
        ModelRuntimeState state = toState(instanceId, modelName, window);
        redissonClient.<String, ModelRuntimeState>getMap(properties.getInstanceStateMapKey()).put(instanceId, state);
        ModelStateEvent event = ModelStateEvent.builder()
                .instanceId(instanceId)
                .action(action)
                .timestamp(System.currentTimeMillis())
                .state(state)
                .build();
        RTopic topic = redissonClient.getTopic(properties.getStateChannel());
        topic.publish(event);
    }

    private ModelRuntimeState toState(String instanceId, String modelName, LocalWindow window) {
        double errorRate = window.totalRequests == 0 ? 0 : (double) window.failureRequests / (double) window.totalRequests;
        double qps = window.totalRequests == 0 ? 0
                : (window.totalRequests * 1000D) / Math.max(1L, System.currentTimeMillis() - window.firstRequestAt);
        double healthScore = Math.max(0D, 1D - errorRate);
        if (window.phase == Phase.DOWN) {
            healthScore = Math.min(healthScore, 0.2D);
        }
        return ModelRuntimeState.builder()
                .instanceId(instanceId)
                .modelName(modelName)
                .available(window.phase != Phase.DOWN)
                .healthScore(healthScore)
                .qps(qps)
                .avgLatencyMs(window.avgLatencyMs)
                .errorRate(errorRate)
                .totalRequests(window.totalRequests)
                .successRequests(window.successRequests)
                .failureRequests(window.failureRequests)
                .consecutiveFailures(window.consecutiveFailures)
                .consecutiveSuccesses(window.consecutiveSuccesses)
                .lastUpdatedAt(System.currentTimeMillis())
                .nextProbeAt(window.nextProbeAt)
                .phase(window.phase.name())
                .lastError(window.lastError)
                .build();
    }
}
