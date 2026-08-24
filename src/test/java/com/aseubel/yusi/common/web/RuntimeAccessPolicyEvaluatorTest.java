package com.aseubel.yusi.common.web;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeAccessPolicyEvaluatorTest {

    private final RuntimeAccessPolicyEvaluator evaluator = new RuntimeAccessPolicyEvaluator();

    @Test
    void blocksAnIpEvenWhenItIsAlsoInTheAllowList() {
        RuntimeAccessPolicySnapshot policy = policy(
                false,
                null,
                List.of("10.0.0.0/8"),
                List.of("10.10.0.0/16"),
                List.of(),
                List.of(),
                List.of());

        assertThat(evaluator.isIpAllowed(policy, "10.10.12.4", LocalDateTime.now())).isFalse();
        assertThat(evaluator.isIpAllowed(policy, "10.11.12.4", LocalDateTime.now())).isTrue();
    }

    @Test
    void rejectsAnIpOutsideANonEmptyAllowList() {
        RuntimeAccessPolicySnapshot policy = policy(
                false,
                null,
                List.of("192.168.1.0/24"),
                List.of(),
                List.of(),
                List.of(),
                List.of());

        assertThat(evaluator.isIpAllowed(policy, "192.168.1.20", LocalDateTime.now())).isTrue();
        assertThat(evaluator.isIpAllowed(policy, "192.168.2.20", LocalDateTime.now())).isFalse();
    }

    @Test
    void developmentModeAllowsOnlyLocalOriginsUntilItExpires() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 24, 15, 0);
        RuntimeAccessPolicySnapshot policy = policy(
                true,
                now.plusHours(1),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("https://app.aseubel.cn"));

        assertThat(evaluator.isOriginAllowed(policy, "http://localhost:5174", now)).isTrue();
        assertThat(evaluator.isOriginAllowed(policy, "http://127.0.0.1:3000", now)).isTrue();
        assertThat(evaluator.isOriginAllowed(policy, "https://evil.example", now)).isFalse();
        assertThat(evaluator.isOriginAllowed(policy, "http://localhost:5174", now.plusHours(2))).isFalse();
        assertThat(evaluator.isOriginAllowed(policy, "https://app.aseubel.cn", now.plusHours(2))).isTrue();
    }

    @Test
    void explicitOriginBlockTakesPriorityOverTheAllowList() {
        RuntimeAccessPolicySnapshot policy = policy(
                false,
                null,
                List.of(),
                List.of(),
                List.of("https://app.aseubel.cn", "http://localhost:*") ,
                List.of("http://localhost:5174"),
                List.of());

        assertThat(evaluator.isOriginAllowed(policy, "http://localhost:5174", LocalDateTime.now())).isFalse();
        assertThat(evaluator.isOriginAllowed(policy, "http://localhost:5175", LocalDateTime.now())).isTrue();
    }

    @Test
    void supportsIpv6CidrRules() {
        RuntimeAccessPolicySnapshot policy = policy(
                false,
                null,
                List.of("2001:db8::/32"),
                List.of(),
                List.of(),
                List.of(),
                List.of());

        assertThat(evaluator.isIpAllowed(policy, "2001:db8:1::42", LocalDateTime.now())).isTrue();
        assertThat(evaluator.isIpAllowed(policy, "2001:db9::42", LocalDateTime.now())).isFalse();
    }

    private RuntimeAccessPolicySnapshot policy(
            boolean developmentModeEnabled,
            LocalDateTime developmentModeExpiresAt,
            List<String> allowedIps,
            List<String> blockedIps,
            List<String> allowedOrigins,
            List<String> blockedOrigins,
            List<String> environmentOrigins) {
        return new RuntimeAccessPolicySnapshot(
                developmentModeEnabled,
                developmentModeExpiresAt,
                allowedOrigins,
                blockedOrigins,
                allowedIps,
                blockedIps,
                environmentOrigins,
                null,
                null);
    }
}
