package com.aseubel.yusi.service.ai.model;

import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.service.ai.model.provider.AnthropicMessagesChatModelProvider;
import com.aseubel.yusi.service.ai.model.provider.ChatModelProviderRegistry;
import com.aseubel.yusi.service.ai.model.provider.OpenAiCompatibleChatModelProvider;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiResponsesChatModel;
import dev.langchain4j.model.openai.OpenAiResponsesStreamingChatModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatModelProviderRegistryTest {

    private final ChatModelProviderRegistry registry =
            new ChatModelProviderRegistry(List.of(
                    new OpenAiCompatibleChatModelProvider(),
                    new AnthropicMessagesChatModelProvider()));

    @Test
    void createsChatCompletionsClientForOpenAiCompatibleProvider() {
        ModelRoutingProperties.ModelDefinition definition = definition("openai-compatible");

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

    @Test
    void createsResponsesClientsForTheResponsesProtocol() {
        ModelRoutingProperties.ModelDefinition definition = definition("openai");
        definition.setProtocol(ModelProtocol.RESPONSES);

        var bundle = registry.create(definition);

        assertThat(bundle.chatModel()).isInstanceOf(OpenAiResponsesChatModel.class);
        assertThat(bundle.streamingChatModel()).isInstanceOf(OpenAiResponsesStreamingChatModel.class);
    }

    @Test
    void createsAnthropicClientsForTheAnthropicMessagesProtocol() {
        ModelRoutingProperties.ModelDefinition definition = definition("anthropic");
        definition.setProtocol(ModelProtocol.ANTHROPIC_MESSAGES);

        var bundle = registry.create(definition);

        assertThat(bundle.provider()).isEqualTo("anthropic");
        assertThat(bundle.chatModel()).isInstanceOf(AnthropicChatModel.class);
        assertThat(bundle.streamingChatModel()).isInstanceOf(AnthropicStreamingChatModel.class);
    }

    @Test
    void rejectsAnthropicProviderWhenTheWireProtocolDoesNotMatch() {
        ModelRoutingProperties.ModelDefinition definition = definition("anthropic");
        definition.setProtocol(ModelProtocol.CHAT_COMPLETIONS);

        assertThatThrownBy(() -> registry.create(definition))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CHAT_COMPLETIONS");
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
