package com.aseubel.yusi.observability.alert;

import com.aseubel.yusi.observability.task.TaskHealthRegistry;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Pure low-sensitivity evaluator with time supplied by a Clock. */
public class AlertEvaluator {

    private static final String SERVICE = "yusi-backend";
    private static final Set<String> FAILURE_CATEGORIES = Set.of(
            "available", "none", "timeout", "connection_failure", "unavailable", "validation", "rejected",
            "dependency", "admission_store_unavailable", "reservation_conflict", "limit_exceeded",
            "unknown");

    private final AlertPolicy policy;
    private final Clock clock;
    private final Map<String, Instant> taskOverdueSince = new HashMap<>();
    private final Map<String, String> activeTaskLevels = new HashMap<>();
    private final Map<String, Instant> taskHealthySince = new HashMap<>();
    private Instant readinessDownSince;
    private boolean readinessAlertActive;
    private int healthyEvaluations;
    private boolean modelAlertActive;
    private Instant modelHealthySince;
    private boolean budgetAlertActive;
    private Instant budgetHealthySince;

    public AlertEvaluator(AlertPolicy policy, Clock clock) {
        this.policy = policy == null ? AlertPolicy.initial() : policy;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public List<AlertSignal> evaluate(AlertSnapshot snapshot) {
        if (snapshot == null) {
            return List.of();
        }
        Instant now = clock.instant();
        if (!snapshot.readinessUp()) {
            return evaluateReadinessDown(snapshot, now);
        }
        List<AlertSignal> signals = new ArrayList<>();
        if (readinessAlertActive) {
            healthyEvaluations++;
            if (healthyEvaluations >= 2) {
                readinessAlertActive = false;
                readinessDownSince = null;
                healthyEvaluations = 0;
                signals.add(signal("service_unavailable", "readiness", "critical", "2m", 1L,
                        1D, "available", now, "recovered"));
            }
            return signals;
        }
        readinessDownSince = null;
        healthyEvaluations = 0;
        signals.addAll(evaluateModel(snapshot, now));
        signals.addAll(evaluateTasks(snapshot, now));
        signals.addAll(evaluateBudget(snapshot, now));
        return signals;
    }

    private List<AlertSignal> evaluateReadinessDown(AlertSnapshot snapshot, Instant now) {
        if (readinessDownSince == null) {
            readinessDownSince = now;
            healthyEvaluations = 0;
        }
        Duration downFor = Duration.between(readinessDownSince, now);
        if (downFor.compareTo(policy.readinessDownAfter()) < 0) {
            return List.of();
        }
        readinessAlertActive = true;
        return List.of(signal("service_unavailable", "readiness", "critical", "2m", 1L,
                0D, normalizeClassification(snapshot.readinessClassification()), now, "firing"));
    }

    private List<AlertSignal> evaluateModel(AlertSnapshot snapshot, Instant now) {
        if (snapshot.modelCalls() < policy.modelMinimumCalls() || snapshot.modelCalls() <= 0) {
            return recoverModelIfClear(snapshot, now);
        }
        double rate = (double) Math.max(0, snapshot.modelFailures()) / snapshot.modelCalls();
        if (rate < policy.modelFailureRateThreshold()) {
            return recoverModelIfClear(snapshot, now);
        }
        modelAlertActive = true;
        modelHealthySince = null;
        return List.of(signal("model_failure_rate", "model_call", "warning", "5m",
                snapshot.modelCalls(), rate, "failure_rate", now, "firing"));
    }

    private List<AlertSignal> recoverModelIfClear(AlertSnapshot snapshot, Instant now) {
        if (!modelAlertActive) {
            return List.of();
        }
        if (modelHealthySince == null) {
            modelHealthySince = now;
        }
        if (Duration.between(modelHealthySince, now)
                .compareTo(policy.modelWindow().multipliedBy(2)) < 0) {
            return List.of();
        }
        modelAlertActive = false;
        modelHealthySince = null;
        return List.of(signal("model_failure_rate", "model_call", "warning", "5m", 0L,
                0D, "available", now, "recovered"));
    }

    private List<AlertSignal> evaluateTasks(AlertSnapshot snapshot, Instant now) {
        List<AlertSignal> signals = new ArrayList<>();
        for (TaskSample task : snapshot.tasks()) {
            String taskName = TaskHealthRegistry.normalizeTaskName(task.taskName());
            if (taskName == null || !task.sampleAvailable()
                    || !Double.isFinite(task.dueGapMinutes()) || !Double.isFinite(task.lagMinutes())) {
                continue;
            }
            double pressure = Math.max(task.dueGapMinutes(), task.lagMinutes());
            if (pressure < policy.taskWarningMinutes()) {
                taskOverdueSince.remove(taskName);
                String activeLevel = activeTaskLevels.get(taskName);
                if (activeLevel != null) {
                    Instant firstHealthy = taskHealthySince.computeIfAbsent(taskName, ignored -> now);
                    if (Duration.between(firstHealthy, now).compareTo(policy.taskSustainAfter()) >= 0) {
                        activeTaskLevels.remove(taskName);
                        taskHealthySince.remove(taskName);
                        signals.add(signal("task_backlog", taskName, activeLevel, "5m", 0L, 0D,
                                "available", now, "recovered"));
                    }
                }
                continue;
            }
            taskHealthySince.remove(taskName);
            Instant firstObserved = taskOverdueSince.computeIfAbsent(taskName, ignored -> now);
            if (Duration.between(firstObserved, now).compareTo(policy.taskSustainAfter()) < 0) {
                continue;
            }
            String level = pressure >= policy.taskCriticalMinutes() ? "critical" : "warning";
            activeTaskLevels.put(taskName, level);
            signals.add(signal("task_backlog", taskName, level, "5m", 1L, pressure,
                    normalizeClassification(task.failureCategory()), now, "firing"));
        }
        return signals;
    }

    private List<AlertSignal> evaluateBudget(AlertSnapshot snapshot, Instant now) {
        if (snapshot.budgetDenials() < policy.budgetMinimumDenials()) {
            if (!budgetAlertActive) {
                return List.of();
            }
            if (budgetHealthySince == null) {
                budgetHealthySince = now;
            }
            if (Duration.between(budgetHealthySince, now).compareTo(policy.budgetWindow()) < 0) {
                return List.of();
            }
            budgetAlertActive = false;
            budgetHealthySince = null;
            return List.of(signal("budget_denied", "model_admission", "warning", "5m", 0L,
                    0D, "available", now, "recovered"));
        }
        budgetAlertActive = true;
        budgetHealthySince = null;
        return List.of(signal("budget_denied", "model_admission", "warning", "5m",
                snapshot.budgetDenials(), snapshot.budgetDenials(), "budget_denied", now, "firing"));
    }

    private AlertSignal signal(String category, String operation, String level, String window,
            long count, double value, String classification, Instant observedAt, String state) {
        return new AlertSignal(category, SERVICE, operation, level, window, count, value,
                normalizeClassification(classification), observedAt, state);
    }

    private String normalizeClassification(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return FAILURE_CATEGORIES.contains(normalized) ? normalized : "unknown";
    }

    public record AlertSnapshot(
            boolean readinessUp,
            String readinessClassification,
            int modelCalls,
            int modelFailures,
            List<TaskSample> tasks,
            int budgetDenials) {

        public AlertSnapshot {
            tasks = tasks == null ? List.of() : List.copyOf(tasks);
            modelCalls = Math.max(0, modelCalls);
            modelFailures = Math.max(0, modelFailures);
            budgetDenials = Math.max(0, budgetDenials);
        }
    }

    public record TaskSample(
            String taskName,
            boolean sampleAvailable,
            double dueGapMinutes,
            double lagMinutes,
            String failureCategory) {
    }
}
