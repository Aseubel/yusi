package com.aseubel.yusi.privacy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("account deletion source coverage gate")
class AccountDeletionSourceCoverageTest {

    private static final Path ADMIN_SERVICE = Path.of(
            "src/main/java/com/aseubel/yusi/service/user/impl/AdminServiceImpl.java");

    @Test
    void deletionImplementationMustCoverMissingTablesAndExternalFamilies() throws Exception {
        String source = implementationSource();

        List<Executable> coverageChecks = List.of(
                "DELETE FROM life_graph_entity_evidence",
                "UPDATE soul_match SET",
                "UPDATE soul_connection SET",
                "UPDATE soul_connection_event SET actor_user_id = NULL",
                "DELETE FROM product_event_scope",
                "DELETE FROM product_event",
                "DELETE FROM task_execution",
                "DELETE FROM security_audit_event_scope",
                "DELETE FROM agent_run_trace",
                "DELETE FROM agent_tool_trace",
                "DELETE FROM model_call_trace")
                .stream()
                .map(required -> (Executable) () -> assertTrue(source.contains(required),
                        "missing deletion statement: " + required))
                .toList();
        assertAll("missing deletion coverage must stay visible", coverageChecks);
    }

    @Test
    void deletionImplementationMustCoverAllMilvusCollectionsAndRedisFamilies() throws Exception {
        String source = implementationSource();

        assertAll("external deletion coverage",
                () -> assertTrue(source.contains("yusi_embedding_collection")),
                () -> assertTrue(source.contains("yusi_mid_term_memory")),
                () -> assertTrue(source.contains("yusi_match_profile")),
                () -> assertTrue(source.contains("yusi:usage:")),
                () -> assertTrue(source.contains("yusi:violation:count:")),
                 () -> assertTrue(source.contains("yusi:langchain:")),
                 () -> assertTrue(source.contains("yusi:chunk:")),
                 () -> assertTrue(source.contains("yusi:md5:")),
                 () -> assertTrue(source.contains("removeFromMap")),
                 () -> assertTrue(source.contains("deidentifySharedMatchData")),
                 () -> assertTrue(source.contains("deleteObjects")));

        assertTrue(source.contains("UPDATE security_audit_event SET actor_user_id = NULL"),
                "retained audit evidence must be de-identified, not deleted");
    }

    @Test
    void sharedMatchStatusesMustBeEvaluatedBeforeParticipantIdsChange() throws Exception {
        String source = implementationSource();
        int statusA = source.indexOf("status_a = CASE WHEN user_a_id = ? THEN NULL ELSE status_a END");
        int statusB = source.indexOf("status_b = CASE WHEN user_b_id = ? THEN NULL ELSE status_b END");
        int userA = source.indexOf("user_a_id = CASE WHEN user_a_id = ? THEN NULL ELSE user_a_id END");
        int userB = source.indexOf("user_b_id = CASE WHEN user_b_id = ? THEN NULL ELSE user_b_id END");

        assertAll("participant status must use the original IDs",
                () -> assertTrue(statusA >= 0 && statusA < userA),
                () -> assertTrue(statusB >= 0 && statusB < userB));
    }

    @Test
    void modifiedOssLoggerProjectionsMustExcludeObjectKeysDigestsAndThrowables() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/aseubel/yusi/service/oss/OssService.java"));
        List<String> loggerBlocks = new java.util.ArrayList<>();
        String[] lines = source.split("\\R");
        for (int index = 0; index < lines.length; index++) {
            if (!lines[index].matches(".*\\blog\\.(trace|debug|info|warn|error)\\s*\\(.*")) {
                continue;
            }
            StringBuilder block = new StringBuilder(lines[index]);
            while (index < lines.length - 1 && !lines[index].trim().endsWith(");")) {
                index++;
                block.append(' ').append(lines[index].trim());
            }
            loggerBlocks.add(block.toString());
        }
        assertFalse(loggerBlocks.isEmpty(), "OSS logger sentinel must scan logger projections");
        for (String block : loggerBlocks) {
            assertFalse(block.matches("(?s).*\\b(objectKey|existObjectKey|cachedObjectKey|chunkObjectKey|finalObjectKey|fileMd5|md5|tempDir)\\b.*"),
                    "OSS logger must not project object keys or digests: " + block);
            assertFalse(block.matches("(?s).*[, ](e|exception|cause)\\s*\\)\\s*$"),
                    "OSS logger must not attach Throwable: " + block);
        }
    }

    @Test
    void adminEntryMustNoLongerUseSwallowedDeleteLoopOrSuccessLog() throws Exception {
        String adminSource = Files.readString(ADMIN_SERVICE);

        assertFalse(adminSource.contains("String[] deleteQueries"),
                "the old per-statement delete loop is a forbidden implementation");
        assertFalse(adminSource.contains("Successfully deregistered user"),
                "success must be written only after external and invariant completion");
    }

    @Test
    void chunkInventoryMustNotRecordAnInvalidZeroChunkSession() throws Exception {
        String source = implementationSource();

        assertFalse(source.contains("addChunkSession(fileMd5, 0)"),
                "chunk inventory must not claim a zero-part upload session");
    }

    private String implementationSource() throws Exception {
        StringBuilder source = new StringBuilder(Files.readString(ADMIN_SERVICE));
        Path privacyRoot = Path.of("src/main/java/com/aseubel/yusi/service/privacy");
        if (Files.isDirectory(privacyRoot)) {
            try (var paths = Files.walk(privacyRoot)) {
                for (Path path : paths.filter(file -> file.toString().endsWith(".java")).toList()) {
                    source.append('\n').append(Files.readString(path));
                }
            }
        }
        return source.toString();
    }
}
