package com.aseubel.yusi.service.ai.model;

import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.service.ai.model.constant.ModelHealthPhase;
import com.aseubel.yusi.service.ai.model.constant.ModelStateAction;
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

    private static class LocalWindow {
        private volatile long firstRequestAt = System.currentTimeMillis();
        private volatile long lastUpdatedAt;
        private volatile String modelName;
        private volatile long totalRequests;
        private volatile long successRequests;
        private volatile long failureRequests;
        private volatile double avgLatencyMs;
        private volatile int consecutiveFailures;
        private volatile int consecutiveSuccesses;
        private volatile long nextProbeAt;
        private volatile String lastError;
        private volatile ModelHealthPhase phase = ModelHealthPhase.UP;
        private volatile ModelHealthPhase previousPhase = ModelHealthPhase.UP;
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
            if (message == null || message.getInstanceId() == null) {
                return;
            }
            putIfNewer(message.getInstanceId(), message.getState());
        });

        RMap<String, ModelRuntimeState> stateMap = redissonClient.getMap(properties.getInstanceStateMapKey());
        for (Map.Entry<String, ModelRuntimeState> entry : stateMap.readAllMap().entrySet()) {
            ModelRuntimeState state = entry.getValue();
            if (state != null && state.getInstanceId() != null) {
                remoteStateCache.put(entry.getKey(), state);
                LocalWindow window = localWindows.computeIfAbsent(entry.getKey(), id -> new LocalWindow());
                window.lastUpdatedAt = state.getLastUpdatedAt();
                window.modelName = state.getModelName();
                window.totalRequests = state.getTotalRequests();
                window.successRequests = state.getSuccessRequests();
                window.failureRequests = state.getFailureRequests();
                window.avgLatencyMs = state.getAvgLatencyMs();
                window.consecutiveFailures = state.getConsecutiveFailures();
                window.consecutiveSuccesses = state.getConsecutiveSuccesses();
                window.phase = phaseOrDefault(state.getPhase());
                window.previousPhase = window.phase;
                window.nextProbeAt = state.getNextProbeAt();
                window.lastError = state.getLastError();
                log.info("Restored state for instance {}: phase={}, totalRequests={}",
                        entry.getKey(), window.phase, window.totalRequests);
            }
        }
    }

    public boolean allowRequest(String instanceId) {
        LocalWindow window = localWindows.computeIfAbsent(instanceId, id -> new LocalWindow());
        mergeIntoLocalWindow(instanceId, remoteStateCache.get(instanceId), window);
        long now = System.currentTimeMillis();
        if (window.phase == ModelHealthPhase.UP) {
            return true;
        }
        if (window.phase == ModelHealthPhase.DOWN && now >= window.nextProbeAt) {
            if (window.probing.compareAndSet(false, true)) {
                window.previousPhase = window.phase;
                window.phase = ModelHealthPhase.HALF_OPEN;
                window.lastUpdatedAt = nextUpdateAt(window);
                publishState(instanceId, "", window, ModelStateAction.PHASE_CHANGE.code());
                return true;
            }
            return false;
        }
        if (window.phase == ModelHealthPhase.HALF_OPEN) {
            return window.probing.compareAndSet(false, true);
        }
        return false;
    }

    public void recordSuccess(String instanceId, String modelName, long latencyMs) {
        LocalWindow window = localWindows.computeIfAbsent(instanceId, id -> new LocalWindow());
        synchronized (window) {
            window.modelName = modelName;
            window.lastUpdatedAt = nextUpdateAt(window);
            window.totalRequests++;
            window.successRequests++;
            window.consecutiveFailures = 0;
            window.consecutiveSuccesses++;
            window.avgLatencyMs = window.avgLatencyMs == 0 ? latencyMs : (window.avgLatencyMs * 0.8 + latencyMs * 0.2);
            if (window.phase == ModelHealthPhase.HALF_OPEN
                    && window.consecutiveSuccesses >= modelConfigCenter.getEffectiveConfig().getRecoverySuccessThreshold()) {
                window.previousPhase = window.phase;
                window.phase = ModelHealthPhase.UP;
                window.nextProbeAt = 0L;
                publishState(instanceId, modelName, window, ModelStateAction.PHASE_CHANGE.code());
            }
        }
        window.probing.set(false);
    }

    public void recordFailure(String instanceId, String modelName, long latencyMs, Throwable throwable) {
        LocalWindow window = localWindows.computeIfAbsent(instanceId, id -> new LocalWindow());
        synchronized (window) {
            window.modelName = modelName;
            window.lastUpdatedAt = nextUpdateAt(window);
            window.totalRequests++;
            window.failureRequests++;
            window.consecutiveFailures++;
            window.consecutiveSuccesses = 0;
            window.avgLatencyMs = window.avgLatencyMs == 0 ? latencyMs : (window.avgLatencyMs * 0.8 + latencyMs * 0.2);
            window.lastError = errorSummary(throwable);
            if (window.phase == ModelHealthPhase.HALF_OPEN
                    || window.consecutiveFailures >= modelConfigCenter.getEffectiveConfig().getFailureThreshold()) {
                window.previousPhase = window.phase;
                window.phase = ModelHealthPhase.DOWN;
                window.nextProbeAt = System.currentTimeMillis()
                        + modelConfigCenter.getEffectiveConfig().getRecoveryProbeIntervalMs();
                publishState(instanceId, modelName, window, ModelStateAction.PHASE_CHANGE.code());
            }
        }
        window.probing.set(false);
    }

    public Map<String, ModelRuntimeState> snapshot(Collection<String> instanceIds) {
        RMap<String, ModelRuntimeState> stateMap = redissonClient.getMap(properties.getInstanceStateMapKey());
        Map<String, ModelRuntimeState> result = new HashMap<>();
        for (String instanceId : instanceIds) {
            ModelRuntimeState cached = remoteStateCache.get(instanceId);
            ModelRuntimeState state = stateMap.get(instanceId);
            LocalWindow window = localWindows.get(instanceId);
            ModelRuntimeState local = window == null ? null : toState(instanceId, "", window);
            ModelRuntimeState latest = latest(latest(cached, state), local);
            if (latest != null) {
                putIfNewer(instanceId, latest);
                mergeIntoLocalWindow(instanceId, latest, window);
                result.put(instanceId, latest);
            }
        }
        return result;
    }

    public boolean isProbeDue(ModelRuntimeState state) {
        return state != null
                && ModelHealthPhase.DOWN.code().equalsIgnoreCase(state.getPhase())
                && state.getNextProbeAt() > 0L
                && System.currentTimeMillis() >= state.getNextProbeAt();
    }

    public List<ModelRuntimeState> listStates() {
        Map<String, ModelRuntimeState> states = new HashMap<>(redissonClient
                .<String, ModelRuntimeState>getMap(properties.getInstanceStateMapKey()).readAllMap());
        remoteStateCache.forEach((instanceId, state) -> states.merge(instanceId, state, this::latest));
        localWindows.forEach((instanceId, window) -> states.merge(instanceId,
                toState(instanceId, "", window), this::latest));
        return states.values().stream().toList();
    }

    public void syncToRedis() {
        RMap<String, ModelRuntimeState> stateMap = redissonClient.getMap(properties.getInstanceStateMapKey());
        for (Map.Entry<String, LocalWindow> entry : localWindows.entrySet()) {
            LocalWindow window = entry.getValue();
            ModelRuntimeState state = toState(entry.getKey(), "", window);
            ModelRuntimeState remote = stateMap.get(entry.getKey());
            ModelRuntimeState latest = latest(remote, state);
            if (latest == state) {
                stateMap.put(entry.getKey(), state);
            }
        }
        log.debug("Synced {} model states to Redis", localWindows.size());
    }

    private void publishState(String instanceId, String modelName, LocalWindow window, String action) {
        ModelRuntimeState state = toState(instanceId, modelName, window);
        putIfNewer(instanceId, state);
        try {
            RMap<String, ModelRuntimeState> stateMap = redissonClient.getMap(properties.getInstanceStateMapKey());
            ModelRuntimeState remote = stateMap.get(instanceId);
            if (latest(remote, state) == state) {
                stateMap.put(instanceId, state);
            }
            ModelStateEvent event = ModelStateEvent.builder()
                    .instanceId(instanceId)
                    .action(action)
                    .timestamp(System.currentTimeMillis())
                    .state(state)
                    .build();
            RTopic topic = redissonClient.getTopic(properties.getStateChannel());
            topic.publish(event);
        } catch (RuntimeException exception) {
            log.warn("Model state publish failed: operation=model_state_publish, instanceId={}, action={}, "
                            + "exceptionType={}",
                    instanceId, action,
                    com.aseubel.yusi.common.utils.LowSensitivityLogSummary.exceptionType(exception));
        }
    }

    private ModelRuntimeState toState(String instanceId, String modelName, LocalWindow window) {
        double errorRate = window.totalRequests == 0 ? 0 : (double) window.failureRequests / (double) window.totalRequests;
        double qps = window.totalRequests == 0 ? 0
                : (window.totalRequests * 1000D) / Math.max(1L, System.currentTimeMillis() - window.firstRequestAt);
        double healthScore = Math.max(0D, 1D - errorRate);
        if (window.phase == ModelHealthPhase.DOWN) {
            healthScore = Math.min(healthScore, 0.2D);
        }
        return ModelRuntimeState.builder()
                .instanceId(instanceId)
                .modelName(modelName == null || modelName.isBlank() ? window.modelName : modelName)
                .available(window.phase != ModelHealthPhase.DOWN)
                .healthScore(healthScore)
                .qps(qps)
                .avgLatencyMs(window.avgLatencyMs)
                .errorRate(errorRate)
                .totalRequests(window.totalRequests)
                .successRequests(window.successRequests)
                .failureRequests(window.failureRequests)
                .consecutiveFailures(window.consecutiveFailures)
                .consecutiveSuccesses(window.consecutiveSuccesses)
                .lastUpdatedAt(window.lastUpdatedAt)
                .nextProbeAt(window.nextProbeAt)
                .phase(window.phase.code())
                .lastError(window.lastError)
                .build();
    }

    private ModelHealthPhase phaseOrDefault(String value) {
        ModelHealthPhase phase = ModelHealthPhase.fromCode(value);
        return phase == null ? ModelHealthPhase.UP : phase;
    }

    private void putIfNewer(String instanceId, ModelRuntimeState state) {
        if (state == null) {
            return;
        }
        remoteStateCache.merge(instanceId, state, this::latest);
    }

    private void mergeIntoLocalWindow(String instanceId, ModelRuntimeState remote, LocalWindow window) {
        if (remote == null || window == null) {
            return;
        }
        synchronized (window) {
            if (remote.getLastUpdatedAt() <= window.lastUpdatedAt) {
                return;
            }
            window.lastUpdatedAt = remote.getLastUpdatedAt();
            window.modelName = remote.getModelName();
            window.totalRequests = remote.getTotalRequests();
            window.successRequests = remote.getSuccessRequests();
            window.failureRequests = remote.getFailureRequests();
            window.avgLatencyMs = remote.getAvgLatencyMs();
            window.consecutiveFailures = remote.getConsecutiveFailures();
            window.consecutiveSuccesses = remote.getConsecutiveSuccesses();
            window.phase = phaseOrDefault(remote.getPhase());
            window.previousPhase = window.phase;
            window.nextProbeAt = remote.getNextProbeAt();
            window.lastError = remote.getLastError();
            window.probing.set(false);
        }
    }

    private ModelRuntimeState latest(ModelRuntimeState first, ModelRuntimeState second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return second.getLastUpdatedAt() >= first.getLastUpdatedAt() ? second : first;
    }

    private long nextUpdateAt(LocalWindow window) {
        return Math.max(System.currentTimeMillis(), window.lastUpdatedAt + 1L);
    }

    private String errorSummary(Throwable throwable) {
        if (throwable instanceof ModelInvocationException modelError) {
            return modelError.errorSummary();
        }
        return ModelErrorSummary.summarize(throwable, null);
    }
}
