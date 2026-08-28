package com.aseubel.yusi.service.ai.model;

import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.config.ai.properties.ModelTierDefinition;
import com.aseubel.yusi.config.ai.properties.RoutePolicyDefinition;
import com.aseubel.yusi.pojo.dto.model.ModelConfigVersionInfo;
import com.aseubel.yusi.pojo.entity.ModelConfigChangeLog;
import com.aseubel.yusi.repository.ModelConfigChangeLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RBucket;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelConfigCenterRestoreTest {

    @Test
    void restoreFactoryRecordsRestoreFactoryActionAndBumpsVersion() {
        ModelRoutingProperties bootstrap = validV2Config();
        bootstrap.setVersion(3L);
        ModelConfigCenter center = centerWith(bootstrap, List.of());

        ModelRoutingProperties restored = center.restoreCanonical(
                center.getFactoryDefaultConfig(), 3L, "admin-1", true);

        assertThat(restored.getVersion()).isEqualTo(4L);
        ArgumentCaptor<ModelConfigChangeLog> logs = ArgumentCaptor.forClass(ModelConfigChangeLog.class);
        verify(changeLogRepository).save(logs.capture());
        assertThat(logs.getValue().getAction()).isEqualTo("RESTORE_FACTORY");
        assertThat(logs.getValue().getSuccess()).isTrue();
    }

    @Test
    void restoreVersionRecordsRollbackAndAllowsRemovingNewlyAddedModel() throws Exception {
        // 当前配置有 qwen + extra；历史快照只有 qwen（无 extra）。普通更新会触发
        // validateStableModelIds 拦截，恢复路径必须放行。
        ModelRoutingProperties bootstrap = validV2Config();
        bootstrap.setVersion(5L);
        ModelRoutingProperties historical = validV2Config();
        historical.setVersion(2L);
        ModelConfigCenter center = centerWith(bootstrap, List.of(
                entry("change-2", 2L, new ObjectMapper().writeValueAsString(historical))));

        ModelRoutingProperties restored = center.restoreCanonical(
                center.getRestoreSnapshot(2L), 5L, "admin-1", false);

        assertThat(restored.getVersion()).isEqualTo(6L);
        assertThat(restored.getModels()).extracting(
                        ModelRoutingProperties.ModelDefinition::getId)
                .containsExactly("qwen");
        ArgumentCaptor<ModelConfigChangeLog> logs = ArgumentCaptor.forClass(ModelConfigChangeLog.class);
        verify(changeLogRepository).save(logs.capture());
        assertThat(logs.getValue().getAction()).isEqualTo("ROLLBACK");
    }

    @Test
    void restoreBackfillsApiKeyFromCurrentConfigByModelId() throws Exception {
        ModelRoutingProperties bootstrap = validV2Config();
        bootstrap.setVersion(5L);
        ModelRoutingProperties historical = validV2Config(); // apikey 在 after_json 中为 ******
        historical.setVersion(2L);
        String redactedJson = new ObjectMapper().writeValueAsString(historical)
                .replace("\"secret\"", "\"******\"");
        ModelConfigCenter center = centerWith(bootstrap, List.of(
                entry("change-2", 2L, redactedJson)));

        ModelRoutingProperties restored = center.restoreCanonical(
                center.getRestoreSnapshot(2L), 5L, "admin-1", false);

        assertThat(restored.getModels().getFirst().getApikey()).isEqualTo("secret");
    }

    @Test
    void restoreRejectsStaleExpectedVersion() {
        ModelRoutingProperties bootstrap = validV2Config();
        bootstrap.setVersion(3L);
        ModelConfigCenter center = centerWith(bootstrap, List.of());

        assertThatThrownBy(() -> center.restoreCanonical(
                center.getFactoryDefaultConfig(), 2L, "admin-1", true))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.CONFIG_VERSION_CONFLICT));
    }

    @Test
    void getRestoreSnapshotRejectsUnknownVersion() {
        ModelConfigCenter center = centerWith(validV2Config(), List.of());

        assertThatThrownBy(() -> center.getRestoreSnapshot(42L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("42");
    }

    @Test
    void listRestoreVersionsDeduplicatesByVersionDescending() throws Exception {
        ModelRoutingProperties historical = validV2Config();
        historical.setVersion(2L);
        String json = new ObjectMapper().writeValueAsString(historical);
        ModelConfigCenter center = centerWith(validV2Config(), List.of(
                entry("change-new", 2L, json),
                entry("change-old", 2L, json),
                entry("change-one", 1L, json.replace("\"version\":2", "\"version\":1"))));

        List<ModelConfigVersionInfo> versions = center.listRestoreVersions();

        assertThat(versions).extracting(ModelConfigVersionInfo::getVersion)
                .containsExactly(2L, 1L);
        assertThat(versions.getFirst().getChangeId()).isEqualTo("change-new");
    }

    @Test
    void factoryDefaultCarriesCurrentVersionAndNoSecret() {
        ModelRoutingProperties bootstrap = validV2Config();
        bootstrap.setVersion(9L);
        ModelConfigCenter center = centerWith(bootstrap, List.of());

        ModelRoutingProperties factory = center.getFactoryDefaultConfig();

        assertThat(factory.getVersion()).isEqualTo(9L);
        assertThat(factory.getModels().getFirst().getApikey()).isEqualTo("secret");
    }

    private ModelConfigChangeLogRepository changeLogRepository;

    private ModelConfigCenter centerWith(ModelRoutingProperties bootstrap,
            List<ModelConfigChangeLog> history) {
        changeLogRepository = mock(ModelConfigChangeLogRepository.class);
        when(changeLogRepository.findTop500BySuccessTrueOrderByCreatedAtDesc()).thenReturn(history);
        RedissonClient redissonClient = mock(RedissonClient.class);
        RBucket<String> bucket = mock(RBucket.class);
        RTopic topic = mock(RTopic.class);
        when(redissonClient.<String>getBucket(anyString())).thenReturn(bucket);
        when(redissonClient.getTopic(anyString())).thenReturn(topic);
        when(bucket.get()).thenReturn(null);
        ModelConfigCenter center = new ModelConfigCenter(bootstrap, redissonClient,
                new ObjectMapper(), null, null, changeLogRepository);
        center.init();
        return center;
    }

    private ModelConfigChangeLog entry(String changeId, long version, String afterJson) {
        return ModelConfigChangeLog.builder()
                .changeId(changeId)
                .action("UPDATE_CONFIG")
                .afterJson(afterJson)
                .success(true)
                .build();
    }

    private ModelRoutingProperties validV2Config() {
        ModelRoutingProperties config = new ModelRoutingProperties();
        config.setSchemaVersion(2);
        config.setModels(List.of(model("qwen")));

        ModelTierDefinition tier = new ModelTierDefinition();
        tier.setMembers(List.of("qwen"));
        tier.setStrategy(ModelSelectionStrategyType.FAIL_OVER);
        config.setTiers(new LinkedHashMap<>(Map.of("balanced", tier)));

        RoutePolicyDefinition route = new RoutePolicyDefinition();
        route.setId("chat");
        route.setScene("chat");
        route.setPrimaryTier("balanced");
        route.setEnabled(true);
        config.setRoutes(List.of(route));
        return config;
    }

    private ModelRoutingProperties.ModelDefinition model(String id) {
        ModelRoutingProperties.ModelDefinition model = new ModelRoutingProperties.ModelDefinition();
        model.setId(id);
        model.setProvider("openai-compatible");
        model.setProtocol(ModelProtocol.CHAT_COMPLETIONS);
        model.setModel("model-name");
        model.setApikey("secret");
        model.setCapabilities(List.of(ModelCapability.CHAT, ModelCapability.STREAMING_CHAT));
        model.setEnabled(true);
        return model;
    }
}
