package com.aseubel.yusi.service.ai.model;

import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.config.ai.properties.ModelTierDefinition;
import com.aseubel.yusi.config.ai.properties.RoutePolicyDefinition;
import com.aseubel.yusi.pojo.dto.model.ModelConfigRestoreRequest;
import com.aseubel.yusi.pojo.dto.model.ModelConfigRestoreResponse;
import com.aseubel.yusi.pojo.dto.model.ModelGovernanceSnapshot;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelManagementServiceRestoreTest {

    @Test
    void restorePreviewReturnsSnapshotShapedTargetWithoutRuntimeStates() {
        ModelConfigCenter configCenter = org.mockito.Mockito.mock(ModelConfigCenter.class);
        org.mockito.Mockito.when(configCenter.getEffectiveConfig()).thenReturn(SampleConfig.withExtraModel());
        org.mockito.Mockito.when(configCenter.getFactoryDefaultConfig()).thenReturn(SampleConfig.historical());
        ModelManagementService service = ServiceFactory.create(configCenter);

        ModelGovernanceSnapshot preview = service.getRestorePreview("FACTORY", null);

        assertThat(preview.getModels()).extracting(
                ModelGovernanceSnapshot.ModelGovernanceModel::getId).containsExactly("qwen");
        assertThat(preview.getRuntimeStates()).isEmpty();
    }

    @Test
    void restoreVersionRequiresVersionParameter() {
        ModelManagementService service = ServiceFactory.create(
                org.mockito.Mockito.mock(ModelConfigCenter.class));

        assertThatThrownBy(() -> service.getRestorePreview("VERSION", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("version");
    }

    @Test
    void restoreRejectsUnknownMode() {
        ModelManagementService service = ServiceFactory.create(
                org.mockito.Mockito.mock(ModelConfigCenter.class));
        ModelConfigRestoreRequest request = new ModelConfigRestoreRequest();
        request.setMode("WHATEVER");
        request.setExpectedVersion(1L);

        assertThatThrownBy(() -> service.restoreConfig(request, "admin-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("FACTORY");
    }

    @Test
    void restoreReportsModelsWithEmptyApiKeyAfterMerge() {
        ModelConfigCenter configCenter = org.mockito.Mockito.mock(ModelConfigCenter.class);
        org.mockito.Mockito.when(configCenter.getEffectiveConfig()).thenReturn(SampleConfig.withExtraModel());
        org.mockito.Mockito.when(configCenter.getRestoreSnapshot(2L)).thenReturn(SampleConfig.historical());
        org.mockito.Mockito.when(configCenter.restoreCanonical(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq(false)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ModelManagementService service = ServiceFactory.create(configCenter);
        ModelConfigRestoreRequest request = new ModelConfigRestoreRequest();
        request.setMode("VERSION");
        request.setVersion(2L);
        request.setExpectedVersion(5L);

        ModelConfigRestoreResponse response = service.restoreConfig(request, "admin-1");

        assertThat(response.getAction()).isEqualTo("ROLLBACK");
        assertThat(response.getVersion()).isEqualTo(6L);
        // historical 样本的 qwen 无 apikey，当前配置的 qwen 也无 apikey，回填后仍为空
        assertThat(response.getMissingApiKeyModels()).containsExactly("qwen");
    }

    /** 统一构造被测服务：仅注入 restore 相关依赖，其余传 null。 */
    private static final class ServiceFactory {
        static ModelManagementService create(ModelConfigCenter configCenter) {
            return new ModelManagementService(
                    null, configCenter, null, null, null, null,
                    new com.fasterxml.jackson.databind.ObjectMapper());
        }
    }

    /** 测试样本配置。 */
    private static final class SampleConfig {
        static ModelRoutingProperties withExtraModel() {
            ModelRoutingProperties config = base();
            config.setVersion(5L);
            config.setModels(List.of(model("qwen"), model("extra")));
            return config;
        }

        static ModelRoutingProperties historical() {
            ModelRoutingProperties config = base();
            config.setVersion(6L);
            config.setModels(List.of(modelWithoutKey("qwen"))); // extra 模型在历史版本中不存在
            return config;
        }

        private static ModelRoutingProperties base() {
            ModelRoutingProperties config = new ModelRoutingProperties();
            config.setSchemaVersion(2);
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

        private static ModelRoutingProperties.ModelDefinition model(String id) {
            ModelRoutingProperties.ModelDefinition model = modelWithoutKey(id);
            model.setApikey("secret");
            return model;
        }

        private static ModelRoutingProperties.ModelDefinition modelWithoutKey(String id) {
            ModelRoutingProperties.ModelDefinition model = new ModelRoutingProperties.ModelDefinition();
            model.setId(id);
            model.setProvider("openai-compatible");
            model.setProtocol(ModelProtocol.CHAT_COMPLETIONS);
            model.setModel("model-name");
            model.setCapabilities(List.of(ModelCapability.CHAT, ModelCapability.STREAMING_CHAT));
            model.setEnabled(true);
            return model;
        }
    }
}
