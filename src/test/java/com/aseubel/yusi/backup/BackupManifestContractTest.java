package com.aseubel.yusi.backup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackupManifestContractTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String VALID_SHA256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final Path MANIFEST_SCHEMA = Path.of("ops/backup/backup-manifest.schema.json");

    @Test
    void acceptsLowSensitivityManifestWithKnownComponent() throws Exception {
        assertDoesNotThrow(() -> BackupManifestValidator.validate(OBJECT_MAPPER.readTree(validManifest("mysql"))));
    }

    @Test
    void rejectsMissingComponent() throws Exception {
        assertInvalidWithout("component");
    }

    @Test
    void rejectsMissingSourceDataTimestamp() throws Exception {
        assertInvalidWithout("sourceDataTimestampUtc");
    }

    @Test
    void rejectsMissingArtifactChecksum() throws Exception {
        assertInvalidWithout("artifactSha256");
    }

    @Test
    void rejectsChecksumThatIsNotExactly64HexCharacters() throws Exception {
        for (String checksum : List.of("short", "g".repeat(64), "0".repeat(63))) {
            String manifest = validManifest("mysql").replace(VALID_SHA256, checksum);
            assertThrows(IllegalArgumentException.class,
                    () -> BackupManifestValidator.validate(OBJECT_MAPPER.readTree(manifest)), checksum);
        }
    }

    @Test
    void rejectsUnknownComponent() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> BackupManifestValidator.validate(OBJECT_MAPPER.readTree(validManifest("postgres"))));
    }

    @Test
    void rejectsSensitiveFieldsAtTheManifestRoot() throws Exception {
        for (String field : List.of("userId", "query", "content", "token", "objectKey")) {
            String manifest = validManifest("mysql").replace(
                    "\"counts\": {\"tables\": 4}",
                    "\"counts\": {\"tables\": 4}, \"" + field + "\": \"forbidden\"");
            assertThrows(IllegalArgumentException.class,
                    () -> BackupManifestValidator.validate(OBJECT_MAPPER.readTree(manifest)), field);
        }
    }

    @Test
    void schemaExistsAndKeepsComponentAndSensitiveFieldContracts() throws Exception {
        assertTrue(Files.isRegularFile(MANIFEST_SCHEMA), "manifest schema must be created before green");
        JsonNode schema = OBJECT_MAPPER.readTree(Files.readString(MANIFEST_SCHEMA));
        assertEquals(List.of("mysql", "milvus", "redis", "oss"),
                OBJECT_MAPPER.convertValue(schema.path("properties").path("component").path("enum"), List.class));
        for (String forbidden : List.of("userId", "query", "content", "token", "objectKey")) {
            assertFalse(schema.path("properties").has(forbidden), forbidden);
        }
    }

    private void assertInvalidWithout(String field) throws Exception {
        JsonNode manifest = OBJECT_MAPPER.readTree(validManifest("mysql"));
        ((com.fasterxml.jackson.databind.node.ObjectNode) manifest).remove(field);
        assertThrows(IllegalArgumentException.class, () -> BackupManifestValidator.validate(manifest), field);
    }

    private String validManifest(String component) {
        return """
                {
                  "backupId": "backup-rehearsal-001",
                  "component": "%s",
                  "sourceDataTimestampUtc": "2026-08-20T00:00:00Z",
                  "createdAtUtc": "2026-08-20T00:05:00Z",
                  "artifactSha256": "%s",
                  "artifactBytes": 128,
                  "schemaVersion": "v1",
                  "toolVersion": "contract-test",
                  "counts": {"tables": 4},
                  "retentionClass": "standard",
                  "restorePoint": "2026-08-20T00:00:00Z"
                }
                """.formatted(component, VALID_SHA256);
    }

    private static final class BackupManifestValidator {

        private static final Set<String> REQUIRED_FIELDS = Set.of(
                "backupId", "component", "sourceDataTimestampUtc", "createdAtUtc", "artifactSha256",
                "artifactBytes", "schemaVersion", "toolVersion", "counts", "retentionClass", "restorePoint");
        private static final Set<String> ALLOWED_COMPONENTS = Set.of("mysql", "milvus", "redis", "oss");
        private static final Set<String> ALLOWED_RETENTION_CLASSES = Set.of("short", "standard", "long");
        private static final Set<String> FORBIDDEN_FIELDS = Set.of("userId", "query", "content", "token", "objectKey");

        private static void validate(JsonNode manifest) {
            if (manifest == null || !manifest.isObject()) {
                throw new IllegalArgumentException("manifest must be an object");
            }
            rejectSensitiveFieldNames(manifest);
            if (!manifest.fieldNames().hasNext()) {
                throw new IllegalArgumentException("manifest must not be empty");
            }
            for (String field : REQUIRED_FIELDS) {
                if (!manifest.has(field)) {
                    throw new IllegalArgumentException("missing manifest field: " + field);
                }
            }
            manifest.fieldNames().forEachRemaining(field -> {
                if (!REQUIRED_FIELDS.contains(field)) {
                    throw new IllegalArgumentException("unknown manifest field: " + field);
                }
            });
            requireText(manifest, "backupId");
            requireText(manifest, "sourceDataTimestampUtc");
            requireText(manifest, "createdAtUtc");
            requireText(manifest, "toolVersion");
            requireText(manifest, "restorePoint");
            parseInstant(manifest.get("sourceDataTimestampUtc"));
            parseInstant(manifest.get("createdAtUtc"));
            parseInstant(manifest.get("restorePoint"));
            if (!manifest.get("component").isTextual()
                    || !ALLOWED_COMPONENTS.contains(manifest.get("component").textValue())) {
                throw new IllegalArgumentException("unknown manifest component");
            }
            if (!manifest.get("artifactSha256").isTextual()
                    || !manifest.get("artifactSha256").textValue().matches("[0-9a-fA-F]{64}")) {
                throw new IllegalArgumentException("artifactSha256 must be exactly 64 hex characters");
            }
            if (!manifest.get("artifactBytes").canConvertToLong()
                    || manifest.get("artifactBytes").longValue() < 0) {
                throw new IllegalArgumentException("artifactBytes must be a non-negative integer");
            }
            if (!manifest.get("schemaVersion").isTextual()
                    || !"v1".equals(manifest.get("schemaVersion").textValue())) {
                throw new IllegalArgumentException("unsupported schemaVersion");
            }
            if (!manifest.get("retentionClass").isTextual()
                    || !ALLOWED_RETENTION_CLASSES.contains(manifest.get("retentionClass").textValue())) {
                throw new IllegalArgumentException("unsupported retentionClass");
            }
            JsonNode counts = manifest.get("counts");
            if (!counts.isObject()) {
                throw new IllegalArgumentException("counts must be an object");
            }
            counts.fields().forEachRemaining(entry -> {
                if (!entry.getValue().canConvertToLong() || entry.getValue().longValue() < 0) {
                    throw new IllegalArgumentException("counts must contain non-negative integers");
                }
            });
        }

        private static void requireText(JsonNode manifest, String field) {
            if (!manifest.get(field).isTextual() || manifest.get(field).textValue().isBlank()) {
                throw new IllegalArgumentException(field + " must be non-blank text");
            }
        }

        private static void parseInstant(JsonNode value) {
            try {
                Instant.parse(value.textValue());
            } catch (Exception exception) {
                throw new IllegalArgumentException("timestamp must be ISO-8601 UTC", exception);
            }
        }

        private static void rejectSensitiveFieldNames(JsonNode node) {
            if (node.isObject()) {
                node.fieldNames().forEachRemaining(field -> {
                    if (FORBIDDEN_FIELDS.contains(field)) {
                        throw new IllegalArgumentException("forbidden manifest field: " + field);
                    }
                    rejectSensitiveFieldNames(node.get(field));
                });
            } else if (node.isArray()) {
                node.forEach(BackupManifestValidator::rejectSensitiveFieldNames);
            }
        }
    }
}
