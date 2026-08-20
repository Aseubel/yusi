package com.aseubel.yusi.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class AlertSensitiveDataTest {

    private static final Path ALERT_SOURCE = Path.of("src/main/java/com/aseubel/yusi/observability/alert");
    private static final Path BASE_CONFIG = Path.of("src/main/resources/application.yml");
    private static final Path PROD_CONFIG = Path.of("src/main/resources/application-prod.yml");
    private static final String[] FORBIDDEN_PAYLOAD_FIELDS = {
            "userId", "query", "token", "objectKey", "prompt", "response"
    };

    @Test
    void alertImplementationSurfaceContainsNoSensitivePayloadFieldOrCredentialLiteral() throws IOException {
        assertThat(Files.isDirectory(ALERT_SOURCE)).isTrue();
        String source = readJavaFiles(ALERT_SOURCE);

        assertThat(source).doesNotContain("http://", "https://", "BEGIN PRIVATE KEY");
        assertThat(source).doesNotContain(FORBIDDEN_PAYLOAD_FIELDS);
    }

    @Test
    void configContainsOnlyRuntimeCredentialNamesAndKeepsTheChannelDisabledByDefault() throws IOException {
        String yaml = Files.readString(BASE_CONFIG) + "\n" + Files.readString(PROD_CONFIG);

        assertThat(yaml).contains("enabled: ${YUSI_ALERT_FEISHU_ENABLED:false}");
        assertThat(yaml).doesNotContain("YUSI_ALERT_FEISHU_WEBHOOK_URL:",
                "YUSI_ALERT_FEISHU_SIGNING_SECRET:",
                "webhook-url:", "signing-secret:");
        assertThat(yaml).doesNotContain("fixture-user-alert", "fixture-query-alert",
                "fixture-content-alert", "fixture-token-alert", "fixture-object-key-alert");
    }

    private String readJavaFiles(Path root) throws IOException {
        return Files.walk(root)
                .filter(path -> path.toString().endsWith(".java"))
                .map(this::read)
                .collect(Collectors.joining("\n"));
    }

    private String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read alert audit file", exception);
        }
    }
}
