package com.aseubel.yusi.service.ai.model;

import com.aseubel.yusi.config.ai.properties.ModelGatewayAdmissionProperties;
import com.aseubel.yusi.observability.alert.AlertPolicy;
import com.aseubel.yusi.observability.metrics.YusiMetrics;
import org.junit.jupiter.api.Test;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModelBudgetAdmissionClassificationTest {

    private static final Set<String> EXPECTED = Set.of(
            "admission_store_unavailable", "reservation_conflict", "limit_exceeded", "unknown");

    @Test
    void metricsAndAlertPolicyUseTheSameClosedBudgetClassificationSet() {
        List<String> inputs = List.of(
                "ADMISSION_STORE_UNAVAILABLE",
                "RESERVATION_CONFLICT",
                "LIMIT_EXCEEDED:user-fixture",
                "fixture-query-rate");

        Set<String> metricValues = inputs.stream()
                .map(YusiMetrics::normalizeBudgetReason)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> alertValues = inputs.stream()
                .map(AlertPolicy::normalizeBudgetReason)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(metricValues).containsExactlyInAnyOrderElementsOf(EXPECTED);
        assertThat(alertValues).containsExactlyInAnyOrderElementsOf(EXPECTED);
        assertThat(metricValues).isEqualTo(alertValues);
        assertThat(metricValues).doesNotContain("user-fixture", "fixture-query-rate");
    }

    @Test
    void eachConfiguredDimensionCanRejectWithoutExposingTheDimensionValue() {
        for (long result : List.of(2L, 3L, 4L)) {
            RedissonClient redisson = mock(RedissonClient.class);
            RScript script = mock(RScript.class);
            when(redisson.getScript(any(StringCodec.class))).thenReturn(script);
            when(script.eval(eq(RScript.Mode.READ_WRITE), anyString(), eq(RScript.ReturnType.INTEGER),
                    anyList(), any(Object[].class))).thenReturn(result);

            ModelGatewayAdmissionProperties properties = new ModelGatewayAdmissionProperties();
            properties.getUser().setMaxRequests(1);
            properties.getModel().setMaxRequests(1);
            properties.getProvider().setMaxRequests(1);
            ModelBudgetPermit permit = new ModelBudgetAdmission(properties, redisson)
                    .reserve(context(), candidate(), new ModelTokenBudget(1, 0));

            assertThat(permit.granted()).isFalse();
            assertThat(permit.reservationKey()).isEqualTo("LIMIT_EXCEEDED");
            assertThat(YusiMetrics.normalizeBudgetReason(permit.reservationKey()))
                    .isEqualTo("limit_exceeded");
            assertThat(permit.reservationKey()).doesNotContain(
                    "fixture-user-rate", "fixture-query-rate", "fixture-content-rate",
                    "fixture-token-rate", "fixture-object-key-rate");
        }
    }

    private ModelRouteContext context() {
        return ModelRouteContext.builder().userId("fixture-user-rate").build();
    }

    private ModelRouteCandidate candidate() {
        ModelInstance instance = ModelInstance.builder()
                .id("fixture-model-rate")
                .modelName("fixture-model-rate")
                .provider("fixture-provider-rate")
                .scenes(Set.of())
                .build();
        return new ModelRouteCandidate("fixture-tier-rate", instance, true, null);
    }
}
