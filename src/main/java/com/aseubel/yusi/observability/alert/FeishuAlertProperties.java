package com.aseubel.yusi.observability.alert;

import java.util.function.Function;

/** Runtime-only Feishu configuration. Values are intentionally not exposed by toString. */
public record FeishuAlertProperties(boolean enabled, String webhookUrl, String signingSecret) {

    public FeishuAlertProperties {
        webhookUrl = safe(webhookUrl);
        signingSecret = safe(signingSecret);
    }

    public static FeishuAlertProperties fromEnvironment(Function<String, String> resolver) {
        Function<String, String> source = resolver == null ? System::getenv : resolver;
        return new FeishuAlertProperties(
                Boolean.parseBoolean(source.apply("YUSI_ALERT_FEISHU_ENABLED")),
                safe(source.apply("YUSI_ALERT_FEISHU_WEBHOOK_URL")),
                safe(source.apply("YUSI_ALERT_FEISHU_SIGNING_SECRET")));
    }

    public boolean configured() {
        return enabled && !webhookUrl.isBlank() && !signingSecret.isBlank();
    }

    public int maxDeliveryAttempts() {
        return 3;
    }

    @Override
    public String toString() {
        return "FeishuAlertProperties[enabled=" + enabled
                + ", endpointConfigured=" + !webhookUrl.isBlank()
                + ", credentialConfigured=" + !signingSecret.isBlank() + "]";
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
