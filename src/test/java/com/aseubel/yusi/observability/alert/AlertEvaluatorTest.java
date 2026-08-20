package com.aseubel.yusi.observability.alert;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AlertEvaluatorTest {

    private static final Instant START = Instant.parse("2026-08-20T12:00:00Z");

    @Test
    void readinessNeedsAContinuousDownWindowAndTwoHealthyEvaluationsToRecover() {
        MutableClock clock = new MutableClock(START);
        AlertEvaluator evaluator = new AlertEvaluator(AlertPolicy.initial(), clock);

        assertThat(evaluator.evaluate(snapshot(false, 0, 0, 0))).isEmpty();

        clock.advance(Duration.ofMinutes(2));
        List<AlertSignal> firing = evaluator.evaluate(snapshot(false, 0, 0, 0));
        assertThat(firing).extracting(AlertSignal::category)
                .containsExactly("service_unavailable");
        assertThat(firing.get(0).state()).isEqualTo("firing");

        clock.advance(Duration.ofSeconds(30));
        assertThat(evaluator.evaluate(snapshot(true, 0, 0, 0))).isEmpty();
        clock.advance(Duration.ofSeconds(30));
        List<AlertSignal> recovered = evaluator.evaluate(snapshot(true, 0, 0, 0));
        assertThat(recovered).extracting(AlertSignal::category)
                .containsExactly("service_unavailable");
        assertThat(recovered.get(0).state()).isEqualTo("recovered");
    }

    @Test
    void readinessRootSuppressesModelAndTaskChildren() {
        MutableClock clock = new MutableClock(START);
        AlertEvaluator evaluator = new AlertEvaluator(AlertPolicy.initial(), clock);
        assertThat(evaluator.evaluate(snapshot(false, 0, 0, 0))).isEmpty();
        clock.advance(Duration.ofMinutes(2));

        List<AlertSignal> signals = evaluator.evaluate(new AlertEvaluator.AlertSnapshot(
                false,
                "connection_failure",
                20,
                10,
                List.of(new AlertEvaluator.TaskSample("weekly-match", true, 20D, 20D, "dependency")),
                10));

        assertThat(signals).extracting(AlertSignal::category)
                .containsExactly("service_unavailable");
    }

    @Test
    void modelTaskAndBudgetSignalsUseTheApprovedInitialThresholds() {
        MutableClock clock = new MutableClock(START);
        AlertEvaluator evaluator = new AlertEvaluator(AlertPolicy.initial(), clock);
        AlertEvaluator.AlertSnapshot unhealthy = new AlertEvaluator.AlertSnapshot(
                true,
                "available",
                20,
                5,
                List.of(new AlertEvaluator.TaskSample("weekly-match", true, 15D, 0D, "dependency")),
                10);

        List<AlertSignal> first = evaluator.evaluate(unhealthy);
        assertThat(first).extracting(AlertSignal::category)
                .containsExactlyInAnyOrder("model_failure_rate", "budget_denied");

        clock.advance(Duration.ofMinutes(5));
        List<AlertSignal> sustained = evaluator.evaluate(unhealthy);
        assertThat(sustained).extracting(AlertSignal::category)
                .contains("task_backlog");
    }

    @Test
    void unknownTaskInputAndFailureCategoryNeverEchoSensitiveSentinels() {
        MutableClock clock = new MutableClock(START.plus(Duration.ofMinutes(5)));
        AlertEvaluator evaluator = new AlertEvaluator(AlertPolicy.initial(), clock);
        AlertEvaluator.AlertSnapshot snapshot = new AlertEvaluator.AlertSnapshot(
                true,
                "fixture-user-alert",
                0,
                0,
                List.of(new AlertEvaluator.TaskSample(
                        "fixture-query-alert", true, 99D, 99D, "fixture-content-alert")),
                0);

        String text = evaluator.evaluate(snapshot).toString();
        assertThat(text).doesNotContain(
                "fixture-user-alert",
                "fixture-query-alert",
                "fixture-content-alert",
                "fixture-token-alert",
                "fixture-object-key-alert");
    }

    private AlertEvaluator.AlertSnapshot snapshot(boolean readinessUp, int modelCalls,
            int modelFailures, int budgetDenials) {
        return new AlertEvaluator.AlertSnapshot(
                readinessUp, "available", modelCalls, modelFailures, List.of(), budgetDenials);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
