package com.aseubel.yusi.service.ai.model;

import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.service.ai.model.provider.ChatModelProviderRegistry;
import com.aseubel.yusi.service.ai.model.provider.OpenAiCompatibleChatModelProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatModelProviderRegistryTest {

    private final ChatModelProviderRegistry registry =
            new ChatModelProviderRegistry(List.of(new OpenAiCompatibleChatModelProvider()));

    @Test
    void defaultsLegacyChatModelToOpenAiCompatibleProvider() {
        ModelRoutingProperties.ModelDefinition definition = definition(null);

        var bundle = registry.create(definition);

        assertThat(bundle.provider()).isEqualTo("openai-compatible");
        assertThat(bundle.chatModel()).isNotNull();
        assertThat(bundle.streamingChatModel()).isNotNull();
    }

    @Test
    void rejectsUnsupportedChatProviderBeforeRegistryReload() {
        ModelRoutingProperties.ModelDefinition definition = definition("anthropic");

        assertThatThrownBy(() -> registry.create(definition))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("provider");
    }

    @Test
    void normalizesCompatibleProviderAliasesToTheAdapterId() {
        ModelRoutingProperties.ModelDefinition definition = definition("deepseek");

        var bundle = registry.create(definition);

        assertThat(bundle.provider()).isEqualTo("openai-compatible");
    }

    private ModelRoutingProperties.ModelDefinition definition(String provider) {
        ModelRoutingProperties.ModelDefinition definition = new ModelRoutingProperties.ModelDefinition();
        definition.setId("qwen");
        definition.setProvider(provider);
        definition.setBaseurl("https://example.test/v1");
        definition.setApikey("test-key");
        definition.setModel("test-model");
        definition.setTimeoutSeconds(2);
        return definition;
    }
}
