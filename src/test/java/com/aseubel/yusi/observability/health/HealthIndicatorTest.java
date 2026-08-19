package com.aseubel.yusi.observability.health;

import com.aseubel.yusi.service.ai.model.ModelConfigCenter;
import com.aseubel.yusi.service.ai.model.ModelInstanceRegistry;
import com.aseubel.yusi.service.ai.model.ModelStateCenter;
import com.aseubel.yusi.observability.task.TaskHealthRegistry;
import io.milvus.v2.client.MilvusClientV2;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class HealthIndicatorTest {

    private static final String QUERY_SENTINEL = "fixture-query-health";
    private static final String SQL_SENTINEL = "fixture-sql-health";
    private static final String CACHE_KEY_SENTINEL = "fixture-cache-key-health";
    private static final String MODEL_RESPONSE_SENTINEL = "fixture-model-response-health";

    @Test
    void dependencyIndicatorsExposeOnlyFixedLowSensitivityDetails() {
        HealthView redis = new HealthView(new RedisHealthIndicator(mock(RedissonClient.class)).health());
        HealthView milvus = new HealthView(new MilvusHealthIndicator(mock(MilvusClientV2.class)).health());
        HealthView tasks = new HealthView(new TaskHealthIndicator(new TaskHealthRegistry()).health());

        assertThat(redis.text()).contains("redis").doesNotContain(QUERY_SENTINEL, SQL_SENTINEL,
                CACHE_KEY_SENTINEL, MODEL_RESPONSE_SENTINEL);
        assertThat(milvus.text()).contains("milvus").doesNotContain(QUERY_SENTINEL, SQL_SENTINEL,
                CACHE_KEY_SENTINEL, MODEL_RESPONSE_SENTINEL);
        assertThat(tasks.text()).contains("tasks").doesNotContain(QUERY_SENTINEL, SQL_SENTINEL,
                CACHE_KEY_SENTINEL, MODEL_RESPONSE_SENTINEL);
    }

    @Test
    void modelProbeUsesStateOnlyAndNeverCallsModelClients() {
        ModelConfigCenter configCenter = mock(ModelConfigCenter.class);
        ModelInstanceRegistry registry = mock(ModelInstanceRegistry.class);
        ModelStateCenter stateCenter = mock(ModelStateCenter.class);

        HealthView health = new HealthView(
                new ModelGatewayHealthIndicator(configCenter, registry, stateCenter).health());

        assertThat(health.text()).contains("modelGateway")
                .doesNotContain(QUERY_SENTINEL, SQL_SENTINEL, CACHE_KEY_SENTINEL, MODEL_RESPONSE_SENTINEL);
        verifyNoInteractions(registry, stateCenter);
    }

    private record HealthView(org.springframework.boot.actuate.health.Health health) {
        String text() {
            return health.getStatus().getCode() + " " + health.getDetails();
        }
    }
}
