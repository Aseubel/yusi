package com.aseubel.yusi.service.ai.model;

import java.util.List;

/**
 * Immutable reservation handle for one model attempt.
 */
public record ModelBudgetPermit(
        String reservationKey,
        List<Charge> charges,
        long estimatedInputTokens,
        long reservedOutputTokens,
        boolean granted) {

    public ModelBudgetPermit {
        charges = charges == null ? List.of() : List.copyOf(charges);
    }

    public static ModelBudgetPermit denied(String reason) {
        return new ModelBudgetPermit(reason, List.of(), 0L, 0L, false);
    }

    public static ModelBudgetPermit noop(ModelTokenBudget budget) {
        ModelTokenBudget safeBudget = budget == null ? new ModelTokenBudget(0L, 0L) : budget;
        return new ModelBudgetPermit("noop", List.of(), safeBudget.estimatedInputTokens(),
                safeBudget.reservedOutputTokens(), true);
    }

    public record Charge(String key, long limit, long reservedAmount, ChargeType type) {
    }

    public enum ChargeType {
        REQUEST,
        TOKEN
    }
}
