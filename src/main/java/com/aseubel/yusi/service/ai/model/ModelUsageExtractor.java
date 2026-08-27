package com.aseubel.yusi.service.ai.model;

import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class ModelUsageExtractor {

    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000L);

    public ModelUsageSnapshot extract(ChatResponse response, ModelInstance model) {
        String priceVersion = normalizePriceVersion(model == null ? null : model.getPriceVersion());
        if (response == null) {
            return ModelUsageSnapshot.unavailable(priceVersion);
        }
        TokenUsage tokenUsage = response.tokenUsage();
        Long inputTokens = tokenUsage == null || tokenUsage.inputTokenCount() == null
                ? null : nonNegative(tokenUsage.inputTokenCount().longValue());
        Long outputTokens = tokenUsage == null || tokenUsage.outputTokenCount() == null
                ? null : nonNegative(tokenUsage.outputTokenCount().longValue());
        String finishReason = finishReason(response.finishReason());
        BigDecimal cost = calculateCost(inputTokens, outputTokens, model);
        return new ModelUsageSnapshot(inputTokens, outputTokens, null, finishReason, cost,
                priceVersion, tokenUsage == null ? "unavailable" : "langchain4j");
    }

    private BigDecimal calculateCost(Long inputTokens, Long outputTokens, ModelInstance model) {
        String priceVersion = normalizePriceVersion(model == null ? null : model.getPriceVersion());
        if (inputTokens == null || outputTokens == null || model == null
                || priceVersion == null
                || model.getInputPricePerMillion() == null || model.getOutputPricePerMillion() == null
                || model.getInputPricePerMillion().signum() < 0
                || model.getOutputPricePerMillion().signum() < 0) {
            return null;
        }
        BigDecimal inputCost = BigDecimal.valueOf(inputTokens)
                .divide(ONE_MILLION, 12, RoundingMode.HALF_UP)
                .multiply(model.getInputPricePerMillion());
        BigDecimal outputCost = BigDecimal.valueOf(outputTokens)
                .divide(ONE_MILLION, 12, RoundingMode.HALF_UP)
                .multiply(model.getOutputPricePerMillion());
        return inputCost.add(outputCost).stripTrailingZeros();
    }

    private String finishReason(FinishReason finishReason) {
        return finishReason == null ? null : finishReason.name();
    }

    private Long nonNegative(long value) {
        return Math.max(0L, value);
    }

    private String normalizePriceVersion(String priceVersion) {
        if (priceVersion == null || priceVersion.isBlank()) {
            return null;
        }
        return priceVersion.trim();
    }
}
