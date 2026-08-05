package com.aseubel.yusi.service.ai.model;

import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.config.ai.properties.ModelTierDefinition;
import com.aseubel.yusi.config.ai.properties.RoutePolicyDefinition;
import com.aseubel.yusi.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.aseubel.yusi.service.ai.model.ModelSelectionStrategyType.FAIL_OVER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
