package com.aseubel.yusi.service.ai.model;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ModelUsageExtractorTest {

    private final ModelUsageExtractor extractor = new ModelUsageExtractor();

    @Test
    void extractsUsageAndCalculatesCostFromTheModelPriceSnapshot() {
        ModelInstance model = ModelInstance.builder()
                .id("qwen")
                .inputPricePerMillion(new BigDecimal("2.00"))
                .outputPricePerMillion(new BigDecimal("4.00"))
                .priceVersion("2026-08")
                .build();
        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("ok"))
                .tokenUsage(new TokenUsage(120, 30, 150))
                .finishReason(FinishReason.STOP)
                .build();

        ModelUsageSnapshot usage = extractor.extract(response, model);

        assertThat(usage.inputTokens()).isEqualTo(120L);
        assertThat(usage.outputTokens()).isEqualTo(30L);
        assertThat(usage.finishReason()).isEqualTo("STOP");
        assertThat(usage.priceVersion()).isEqualTo("2026-08");
        assertThat(usage.cost()).isEqualByComparingTo("0.00036");
    }

    @Test
    void leavesCostUnknownWhenProviderDoesNotReturnUsageOrPrice() {
        ModelUsageSnapshot usage = extractor.extract(
                ChatResponse.builder().aiMessage(AiMessage.from("ok")).build(),
                ModelInstance.builder().id("qwen").build());

        assertThat(usage.inputTokens()).isNull();
        assertThat(usage.outputTokens()).isNull();
        assertThat(usage.cost()).isNull();
        assertThat(usage.usageSource()).isEqualTo("unavailable");
    }
}
