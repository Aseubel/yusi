package com.aseubel.yusi.benchmark;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 记分卡红线校验：违禁内容必须被拒绝落盘。 */
class BenchmarkReportSensitivityValidatorTest {

    private final BenchmarkReportSensitivityValidator validator =
            new BenchmarkReportSensitivityValidator();

    @Test
    void cleanMetricReportPasses() {
        assertThat(validator.validate(
                "{\"retrieval\":{\"recallAt5\":0.72},\"anomalies\":[]}"))
                .isEmpty();
    }

    @Test
    void apiKeyLikeStringIsRejected() {
        assertThat(validator.validate("{\"note\":\"sk-abcdef1234567890abcd\"}"))
                .containsExactly("api_key_like");
    }

    @Test
    void bearerTokenIsRejected() {
        assertThat(validator.validate("{\"header\":\"Bearer eyJhbGciOiJIUzI1NiJ9\"}"))
                .containsExactly("bearer_token");
    }

    @Test
    void privateKeyBlockIsRejected() {
        assertThat(validator.validate("{\"pem\":\"BEGIN RSA PRIVATE KEY\"}"))
                .containsExactly("private_or_public_key_block");
    }

    @Test
    void longBase64IsRejectedAsSuspectedSecret() {
        String longBase64 = "MDEy".repeat(40); // 120 个 base64 字符
        assertThat(validator.validate("{\"k\":\"" + longBase64 + "\"}"))
                .containsExactly("long_base64_suspected_secret");
    }

    @Test
    void oversizedRawTranscriptIsRejectedButShortIsFine() {
        String chunk = "对".repeat(501);
        assertThat(validator.validate("{\"rawTranscript\":\"" + chunk + "\"}"))
                .isNotEmpty();

        String shortOne = "短对话";
        assertThatCode(() -> validator.validate("{\"rawTranscript\":\"" + shortOne + "\"}"))
                .doesNotThrowAnyException();
        assertThat(validator.validate("{\"rawTranscript\":\"" + shortOne + "\"}")).isEmpty();
    }
}
