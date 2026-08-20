package com.aseubel.yusi.observability.alert;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AlertPolicyTest {

    @Test
    void initialPolicyExposesTheApprovedProductionTunableThresholds() {
        AlertPolicy policy = AlertPolicy.initial();

        assertThat(policy.readinessDownAfter()).isEqualTo(Duration.ofMinutes(2));
        assertThat(policy.modelWindow()).isEqualTo(Duration.ofMinutes(5));
        assertThat(policy.modelFailureRateThreshold()).isEqualTo(0.20D);
        assertThat(policy.modelMinimumCalls()).isEqualTo(20);
        assertThat(policy.taskWarningMinutes()).isEqualTo(15);
        assertThat(policy.taskCriticalMinutes()).isEqualTo(60);
        assertThat(policy.taskSustainAfter()).isEqualTo(Duration.ofMinutes(5));
        assertThat(policy.budgetWindow()).isEqualTo(Duration.ofMinutes(5));
        assertThat(policy.budgetMinimumDenials()).isEqualTo(10);
        assertThat(policy.suppressionWindow()).isEqualTo(Duration.ofMinutes(30));
        assertThat(policy.maxDeliveryAttempts()).isEqualTo(3);
    }

    @Test
    void budgetReasonsAreCollapsedToAClosedLowSensitivitySet() {
        assertThat(AlertPolicy.normalizeBudgetReason("ADMISSION_STORE_UNAVAILABLE"))
                .isEqualTo("admission_store_unavailable");
        assertThat(AlertPolicy.normalizeBudgetReason("RESERVATION_CONFLICT"))
                .isEqualTo("reservation_conflict");
        assertThat(AlertPolicy.normalizeBudgetReason("LIMIT_EXCEEDED:user-fixture"))
                .isEqualTo("limit_exceeded");
        assertThat(AlertPolicy.normalizeBudgetReason("fixture-query-alert"))
                .isEqualTo("unknown");
    }
}
