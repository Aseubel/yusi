package com.aseubel.yusi.service.ai.model;

import com.aseubel.yusi.config.ai.properties.ModelGatewayAdmissionProperties;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.junit.jupiter.api.Test;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ModelBudgetAdmissionTest {

    @Test
    void disabledAdmissionDoesNotRequireRedis() {
        ModelBudgetAdmission admission = new ModelBudgetAdmission();

        ModelBudgetPermit permit = admission.reserve(context(), candidate(), new ModelTokenBudget(20, 10));

        assertThat(permit.granted()).isTrue();
        assertThat(permit.reservationKey()).isEqualTo("noop");
    }

    @Test
    void reservesEveryConfiguredDimensionAndReconcilesProviderUsage() {
        ModelGatewayAdmissionProperties properties = new ModelGatewayAdmissionProperties();
        properties.setKeyPrefix("test:admission:");
        properties.getUser().setMaxRequests(10);
        properties.getUser().setMaxTokens(1_000);
        properties.getTenant().setMaxRequests(20);
        properties.getTenant().setMaxTokens(2_000);
        properties.getModel().setMaxRequests(30);
        properties.getModel().setMaxTokens(3_000);
        properties.getProvider().setMaxRequests(40);
        properties.getProvider().setMaxTokens(4_000);

        RedissonClient redissonClient = mock(RedissonClient.class);
        RScript script = mock(RScript.class);
        when(redissonClient.getScript(any(StringCodec.class))).thenReturn(script);
        when(script.eval(eq(RScript.Mode.READ_WRITE), anyString(), eq(RScript.ReturnType.INTEGER),
                anyList(), any(Object[].class))).thenReturn(1L);

        ModelBudgetAdmission admission = new ModelBudgetAdmission(properties, redissonClient);
        ModelBudgetPermit permit = admission.reserve(context(), candidate(), new ModelTokenBudget(20, 10));

        assertThat(permit.granted()).isTrue();
        assertThat(permit.charges()).hasSize(8);
        assertThat(permit.charges()).allMatch(charge -> charge.reservedAmount() == 1L
                || charge.reservedAmount() == 30L);

        admission.reconcile(permit, new ModelUsageSnapshot(12L, 4L, null,
                "STOP", null, "test", "test"));

        verify(redissonClient, org.mockito.Mockito.times(2)).getScript(any(StringCodec.class));
    }

    private ModelRouteContext context() {
        return ModelRouteContext.builder().userId("user-1").tenantId("tenant-1").build();
    }

    private ModelRouteCandidate candidate() {
        ModelInstance instance = ModelInstance.builder()
                .id("model-1")
                .modelName("model-1")
                .provider("provider-1")
                .languages(Set.of())
                .scenes(Set.of())
                .chatModel(mock(ChatModel.class))
                .streamingChatModel(mock(StreamingChatModel.class))
                .build();
        return new ModelRouteCandidate("tier-1", instance, true, null);
    }
}
