package com.aseubel.yusi.observability.alert;

import com.aseubel.yusi.observability.task.TaskHealthRegistry;
import com.aseubel.yusi.observability.task.TaskScheduleCatalog;
import com.aseubel.yusi.observability.metrics.YusiMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/** Periodically evaluates low-sensitivity snapshots and submits deduplicated alerts. */
@Slf4j
@Component
@ConditionalOnProperty(name = "yusi.alert.feishu.enabled", havingValue = "true")
public class AlertScheduler {

    private final HealthEndpoint healthEndpoint;
    private final MeterRegistry meterRegistry;
    private final TaskHealthRegistry taskHealthRegistry;
    private final TaskScheduleCatalog taskScheduleCatalog;
    private final AlertEvaluator evaluator;
    private final AlertPolicy policy;
    private final AlertStateStore stateStore;
    private final FeishuAlertNotifier notifier;
    private final YusiMetrics metrics;
    private final Clock clock;
    private final Deque<CounterSample> counterSamples = new ArrayDeque<>();

    public AlertScheduler(HealthEndpoint healthEndpoint, MeterRegistry meterRegistry,
            TaskHealthRegistry taskHealthRegistry, TaskScheduleCatalog taskScheduleCatalog,
            AlertEvaluator evaluator, AlertStateStore stateStore,
            FeishuAlertNotifier notifier, Clock clock) {
        this(healthEndpoint, meterRegistry, taskHealthRegistry, taskScheduleCatalog, evaluator,
                AlertPolicy.initial(), stateStore, notifier, clock, null);
    }

    @Autowired
    public AlertScheduler(HealthEndpoint healthEndpoint, MeterRegistry meterRegistry,
            TaskHealthRegistry taskHealthRegistry, TaskScheduleCatalog taskScheduleCatalog,
            AlertEvaluator evaluator, AlertPolicy policy, AlertStateStore stateStore,
            FeishuAlertNotifier notifier, Clock clock, YusiMetrics metrics) {
        this.healthEndpoint = healthEndpoint;
        this.meterRegistry = meterRegistry;
        this.taskHealthRegistry = taskHealthRegistry;
        this.taskScheduleCatalog = taskScheduleCatalog;
        this.evaluator = evaluator;
        this.policy = policy == null ? AlertPolicy.initial() : policy;
        this.stateStore = stateStore;
        this.notifier = notifier;
        this.metrics = metrics;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Scheduled(fixedDelayString = "${yusi.alert.feishu.interval-ms:30000}")
    public void evaluateScheduled() {
        evaluateOnce();
    }

    public void evaluateOnce() {
        Instant now = clock.instant();
        try {
            HealthComponent readiness = healthEndpoint.healthForPath("readiness");
            boolean ready = readiness != null && "UP".equals(readiness.getStatus().getCode());
            recordDependencyHealth(readiness);
            Map<String, TaskHealthRegistry.TaskTiming> timings = taskHealthRegistry.timingSnapshot(now,
                    taskScheduleCatalog);
            for (TaskHealthRegistry.TaskTiming timing : timings.values()) {
                if (timing.sampleAvailable() && metrics != null) {
                    metrics.recordTaskBacklog(timing.taskName(), timing.dueGapMinutes(), timing.lagMinutes(),
                            timing.result(), timing.failureCategory());
                }
            }
            List<AlertEvaluator.TaskSample> tasks = timings.values().stream()
                    .map(timing -> new AlertEvaluator.TaskSample(timing.taskName(), timing.sampleAvailable(),
                            timing.dueGapMinutes(), timing.lagMinutes(), timing.failureCategory()))
                    .toList();
            CounterSample window = windowSample(now,
                    counterValue("model_call_total"),
                    counterValue("model_call_failure_total"),
                    counterValue("budget_denied_total"));
            AlertEvaluator.AlertSnapshot snapshot = new AlertEvaluator.AlertSnapshot(
                    ready, ready ? "available" : "unavailable",
                    window.modelCalls(), window.modelFailures(), tasks, window.budgetDenials());
            for (AlertSignal signal : evaluator.evaluate(snapshot)) {
                publish(signal, now);
            }
        } catch (RuntimeException exception) {
            log.warn("Alert evaluation skipped: category=evaluator_failure, exceptionType={}",
                    exception.getClass().getSimpleName());
        }
    }

    private void publish(AlertSignal signal, Instant now) {
        String fingerprint = signal.fingerprint();
        if ("recovered".equals(signal.state())) {
            if (stateStore.markRecovered(fingerprint, now)) {
                notifier.notify(AlertMessage.fromSignal(signal));
            }
            return;
        }
        if (stateStore.isRootSuppressionActive(now)
                && !"service_unavailable".equals(signal.category())) {
            return;
        }
        if (!stateStore.claim(fingerprint, now, policy.suppressionWindow())) {
            return;
        }
        stateStore.markFiring(fingerprint, now);
        notifier.notify(AlertMessage.fromSignal(signal));
    }

    private int counterValue(String name) {
        double total = meterRegistry.getMeters().stream()
                .filter(meter -> meter.getId().getName().equals(name))
                .filter(meter -> meter instanceof Counter)
                .mapToDouble(meter -> ((Counter) meter).count())
                .sum();
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0D, total));
    }

    private CounterSample windowSample(Instant now, int modelCalls, int modelFailures,
            int budgetDenials) {
        counterSamples.addLast(new CounterSample(now, modelCalls, modelFailures, budgetDenials));
        Duration retention = policy.modelWindow().compareTo(policy.budgetWindow()) >= 0
                ? policy.modelWindow() : policy.budgetWindow();
        Instant cutoff = now.minus(retention);
        while (counterSamples.size() > 1) {
            CounterSample second = counterSamples.stream().skip(1).findFirst().orElse(null);
            if (second == null || second.at().isAfter(cutoff)) {
                break;
            }
            counterSamples.removeFirst();
        }
        CounterSample modelBaseline = baselineAt(now.minus(policy.modelWindow()));
        CounterSample budgetBaseline = baselineAt(now.minus(policy.budgetWindow()));
        if (modelBaseline == null || budgetBaseline == null) {
            return new CounterSample(now, 0, 0, 0);
        }
        return new CounterSample(now,
                Math.max(0, modelCalls - modelBaseline.modelCalls()),
                Math.max(0, modelFailures - modelBaseline.modelFailures()),
                Math.max(0, budgetDenials - budgetBaseline.budgetDenials()));
    }

    private CounterSample baselineAt(Instant cutoff) {
        CounterSample candidate = null;
        for (CounterSample sample : counterSamples) {
            if (sample.at().isAfter(cutoff)) {
                break;
            }
            candidate = sample;
        }
        return candidate == null ? counterSamples.peekFirst() : candidate;
    }

    private void recordDependencyHealth(HealthComponent readiness) {
        if (metrics == null) {
            return;
        }
        recordDependencyHealth("readiness", readiness);
        for (String component : List.of("db", "redis", "milvus", "modelGateway", "tasks")) {
            recordDependencyHealth(component, healthEndpoint.healthForPath(component));
        }
    }

    private void recordDependencyHealth(String component, HealthComponent health) {
        boolean available = health != null && health.getStatus() != null
                && "UP".equals(health.getStatus().getCode());
        String operation = "modelGateway".equals(component) ? "model_gateway" : component;
        metrics.recordDependencyHealth(operation, available ? "up" : "down",
                available ? "none" : "unavailable", available);
    }

    private record CounterSample(Instant at, int modelCalls, int modelFailures, int budgetDenials) {
    }
}
