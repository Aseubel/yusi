package com.aseubel.yusi.service.ai.model;

import com.aseubel.yusi.service.ai.prompt.PromptSnapshot;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ModelRouteContext {
    String requestId;
    String runId;
    String userId;
    String scene;
    String promptKey;
    String promptVersion;
    String promptLocale;
    String riskLevel;
    Integer estimatedInputTokens;
    Integer reservedOutputTokens;
    @Builder.Default
    boolean maskSensitiveData = true;

    public static class ModelRouteContextBuilder {
        public ModelRouteContextBuilder prompt(PromptSnapshot snapshot) {
            if (snapshot == null) {
                return this;
            }
            return promptKey(snapshot.key())
                    .promptVersion(snapshot.version())
                    .promptLocale(snapshot.locale());
        }
    }
}
