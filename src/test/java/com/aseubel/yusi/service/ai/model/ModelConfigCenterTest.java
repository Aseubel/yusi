package com.aseubel.yusi.service.ai.model;

import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.config.ai.properties.ModelTierDefinition;
import com.aseubel.yusi.config.ai.properties.RoutePolicyDefinition;
import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.pojo.entity.ModelConfigChangeLog;
import com.aseubel.yusi.pojo.entity.ModelRuntimeConfig;
import com.aseubel.yusi.repository.ModelConfigChangeLogRepository;
import com.aseubel.yusi.repository.ModelRuntimeConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.redisson.api.RBucket;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.aseubel.yusi.service.ai.model.ModelSelectionStrategyType.FAIL_OVER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelConfigCenterTest {

    @Test
    void convertsLegacyGroupAndMatrixIntoTierAndRouteDefinitions() {
        ModelConfigCenter center = center();
        ModelRoutingProperties legacy = legacyConfig();

        ModelRoutingProperties normalized = center.normalizeLegacyConfig(legacy);

        assertThat(normalized.getSchemaVersion()).isEqualTo(2);
        assertThat(normalized.getTiers()).containsKey("chat-zh");
        assertThat(normalized.getTiers().get("chat-zh").getMembers()).containsExactly("qwen");
        assertThat(normalized.getRoutes()).singleElement().satisfies(route -> {
            assertThat(route.getId()).isEqualTo("zh-chat");
            assertThat(route.getLanguage()).isEqualTo("zh");
            assertThat(route.getScene()).isEqualTo("chat");
            assertThat(route.getPrimaryTier()).isEqualTo("chat-zh");
        });
    }

    @Test
    void rejectsRouteReferencingUnknownTier() {
        ModelConfigCenter center = center();
        ModelRoutingProperties config = validV2Config();
        config.getRoutes().get(0).setPrimaryTier("missing");

        assertThatThrownBy(() -> center.validateForAdmin(config))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("primary-tier");
    }

    @Test
    void rejectsRouteWhosePrimaryTierHasNoEnabledChatModel() {
        ModelConfigCenter center = center();
        ModelRoutingProperties config = validV2Config();
        config.getModels().getFirst().setEnabled(false);

        assertThatThrownBy(() -> center.validateForAdmin(config))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("启用的 Chat");
    }

    @Test
    void rejectsStaleVersionBeforeWritingRuntimeConfig() {
        ModelRoutingProperties current = validV2Config();
        current.setVersion(7L);
        ModelConfigCenter center = new ModelConfigCenter(current, null, new ObjectMapper(), null);

        assertThatThrownBy(() -> center.updateFromAdmin(validV2Config(), 6L, "admin-1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(com.aseubel.yusi.common.exception.ErrorCode.CONFIG_VERSION_CONFLICT));
    }

    @Test
    void rejectsVersionOlderThanPersistedActiveSnapshot() throws Exception {
        ModelRoutingProperties bootstrap = validV2Config();
        bootstrap.setVersion(7L);
        ModelRoutingProperties persisted = validV2Config();
        persisted.setVersion(8L);
        ModelRuntimeConfig stored = ModelRuntimeConfig.builder()
                .configKey("active")
                .configJson(new ObjectMapper().writeValueAsString(persisted))
                .version(8L)
                .build();
        ModelRuntimeConfigRepository runtimeRepository = mock(ModelRuntimeConfigRepository.class);
        when(runtimeRepository.findByConfigKey("active")).thenReturn(Optional.of(stored));
        ModelConfigCenter center = new ModelConfigCenter(bootstrap, null, new ObjectMapper(), null,
                runtimeRepository, null);

        assertThatThrownBy(() -> center.updateFromAdmin(validV2Config(), 7L, "admin-1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(com.aseubel.yusi.common.exception.ErrorCode.CONFIG_VERSION_CONFLICT));
    }

    @Test
    void keepsLocalVersionAndRecordsFailedAuditWhenRedisPublishFails() {
        ModelRoutingProperties current = validV2Config();
        current.setVersion(7L);
        RedissonClient redissonClient = mock(RedissonClient.class);
        RBucket<String> bucket = mock(RBucket.class);
        RTopic topic = mock(RTopic.class);
        when(redissonClient.<String>getBucket(anyString())).thenReturn(bucket);
        when(redissonClient.getTopic(anyString())).thenReturn(topic);
        doThrow(new RuntimeException("redis unavailable")).when(bucket).set(anyString());

        ModelRuntimeConfigRepository runtimeRepository = mock(ModelRuntimeConfigRepository.class);
        ModelConfigChangeLogRepository changeLogRepository = mock(ModelConfigChangeLogRepository.class);
        when(runtimeRepository.findByConfigKey("active")).thenReturn(Optional.empty());
        when(runtimeRepository.save(any(ModelRuntimeConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(changeLogRepository.save(any(ModelConfigChangeLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ModelConfigCenter center = new ModelConfigCenter(current, redissonClient, new ObjectMapper(), null,
                runtimeRepository, changeLogRepository);

        assertThatThrownBy(() -> center.updateFromAdmin(center.getConfigForDisplay(), 7L, "admin-1"))
                .isInstanceOf(ModelConfigCenter.ModelRuntimePublishException.class);

        assertThat(center.getCurrentVersion()).isEqualTo(7L);
        ArgumentCaptor<ModelConfigChangeLog> logs = ArgumentCaptor.forClass(ModelConfigChangeLog.class);
        verify(changeLogRepository, times(2)).save(logs.capture());
        assertThat(logs.getAllValues()).extracting(ModelConfigChangeLog::getSuccess)
                .containsExactly(true, false);
        assertThat(logs.getAllValues().get(0).getAfterJson()).doesNotContain("secret");
        InOrder order = inOrder(runtimeRepository, changeLogRepository, bucket, topic);
        order.verify(runtimeRepository).save(any(ModelRuntimeConfig.class));
        order.verify(changeLogRepository).save(any(ModelConfigChangeLog.class));
        order.verify(bucket).set(anyString());
        order.verify(changeLogRepository).save(any(ModelConfigChangeLog.class));
    }

    private ModelConfigCenter center() {
        return new ModelConfigCenter(new ModelRoutingProperties(), null, new ObjectMapper(), null);
    }

    private ModelRoutingProperties legacyConfig() {
        ModelRoutingProperties config = new ModelRoutingProperties();
        config.setModels(List.of(model("qwen")));
        config.setGroups(new LinkedHashMap<>(Map.of("chat-zh", group(List.of("qwen")))));
        config.setMatrix(new LinkedHashMap<>(Map.of(
                "zh", new LinkedHashMap<>(Map.of("chat", scene("chat-zh"))))));
        return config;
    }

    private ModelRoutingProperties validV2Config() {
        ModelRoutingProperties config = new ModelRoutingProperties();
        config.setModels(List.of(model("qwen")));

        ModelRoutingProperties.GroupDefinition tier = group(List.of("qwen"));
        ModelTierDefinition tierDefinition = new ModelTierDefinition();
        tierDefinition.setMembers(tier.getMembers());
        tierDefinition.setStrategy(tier.getStrategy());
        config.setTiers(new LinkedHashMap<>(Map.of("balanced", tierDefinition)));

        RoutePolicyDefinition route = new RoutePolicyDefinition();
        route.setId("chat-zh");
        route.setLanguage("zh");
        route.setScene("chat");
        route.setPrimaryTier("balanced");
        route.setEnabled(true);
        config.setRoutes(List.of(route));
        return config;
    }

    private ModelRoutingProperties.ModelDefinition model(String id) {
        ModelRoutingProperties.ModelDefinition model = new ModelRoutingProperties.ModelDefinition();
        model.setId(id);
        model.setModel("model-name");
        model.setApikey("secret");
        model.setCapabilities(List.of(ModelCapability.CHAT, ModelCapability.STREAMING_CHAT));
        model.setEnabled(true);
        return model;
    }

    private ModelRoutingProperties.GroupDefinition group(List<String> members) {
        ModelRoutingProperties.GroupDefinition group = new ModelRoutingProperties.GroupDefinition();
        group.setMembers(members);
        group.setStrategy(FAIL_OVER);
        return group;
    }

    private ModelRoutingProperties.SceneDefinition scene(String group) {
        ModelRoutingProperties.SceneDefinition scene = new ModelRoutingProperties.SceneDefinition();
        scene.setGroup(group);
        return scene;
    }
}
