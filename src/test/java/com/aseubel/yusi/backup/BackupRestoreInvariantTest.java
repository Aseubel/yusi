package com.aseubel.yusi.backup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("application-invariant-only backup restore checks")
class BackupRestoreInvariantTest {

    private static final String JDBC_URL = "jdbc:h2:mem:backup_invariant;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
            + "NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
    private static final String FIXTURE_USER = "backup-rehearsal-user-001";
    private static final String FIXTURE_DIARY = "backup-rehearsal-diary-001";

    @Test
    void validSyntheticH2SnapshotHasNoOrphans() throws Exception {
        try (Connection connection = DriverManager.getConnection(JDBC_URL)) {
            createSchema(connection);
            insertValidSnapshot(connection);

            BackupRestoreInvariantValidator.InvariantReport report =
                    BackupRestoreInvariantValidator.validate(connection);

            assertTrue(report.valid(), report::toString);
            assertEquals(1, report.rowCount("user"));
            assertEquals(1, report.rowCount("diary"));
            assertEquals(0, report.orphanCount("diary_user"));
            assertEquals(0, report.orphanCount("relation_endpoint"));
            assertEquals(0, report.orphanCount("connection_match"));
        }
    }

    @Test
    void brokenDiaryUserReferenceFailsIntegrityGate() throws Exception {
        try (Connection connection = DriverManager.getConnection(JDBC_URL)) {
            createSchema(connection);
            insertValidSnapshot(connection);
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO diary (diary_id, user_id) VALUES (?, ?)")) {
                statement.setString(1, "backup-rehearsal-diary-broken-001");
                statement.setString(2, "backup-rehearsal-user-missing-001");
                statement.executeUpdate();
            }

            BackupRestoreInvariantValidator.InvariantReport report =
                    BackupRestoreInvariantValidator.validate(connection);

            assertFalse(report.valid(), report::toString);
            assertEquals(1, report.orphanCount("diary_user"));
        }
    }

    @Test
    void redisKeyFamiliesHaveExplicitRecoveryClasses() {
        assertEquals("rebuildable-cache", BackupRestoreStaticContract.classifyRedisKey("yusi:langchain:fixture"));
        assertEquals("rebuildable-cache", BackupRestoreStaticContract.classifyRedisKey("yusi:chunk:fixture"));
        assertEquals("rebuildable-cache", BackupRestoreStaticContract.classifyRedisKey("yusi:md5:fixture"));
        assertEquals("security-state", BackupRestoreStaticContract.classifyRedisKey("yusi:auth:refresh:fixture"));
        assertEquals("security-state", BackupRestoreStaticContract.classifyRedisKey("yusi:auth:blacklist:fixture"));
        assertEquals("security-state", BackupRestoreStaticContract.classifyRedisKey("yusi:auth:devices:fixture"));
        assertEquals("reconcile-with-mysql", BackupRestoreStaticContract.classifyRedisKey("yusi:usage:2026-08-20"));
        assertEquals("review-before-restore", BackupRestoreStaticContract.classifyRedisKey("yusi:violation:count:fixture"));
        assertEquals("rebuildable-runtime", BackupRestoreStaticContract.classifyRedisKey("yusi:model:state:instances"));
        assertEquals("non-restorable-channel", BackupRestoreStaticContract.classifyRedisKey("yusi:model:state:channel"));
        assertEquals("restore-from-mysql", BackupRestoreStaticContract.classifyRedisKey("yusi:model:runtime:config"));
    }

    @Test
    void externalDependencyWrappersAreExplicitlyDeploymentOnlyAndStatic() throws Exception {
        for (Path script : List.of(
                Path.of("ops/backup/milvus-backup.ps1"),
                Path.of("ops/backup/redis-backup.ps1"),
                Path.of("ops/backup/oss-inventory.ps1"))) {
            assertTrue(Files.isRegularFile(script), script + " must exist");
            String source = Files.readString(script);
            assertTrue(source.contains("DEPLOYMENT-ONLY"), script + " must not claim local recovery");
            assertFalse(source.contains("Invoke-RestMethod"), script + " must not call a remote endpoint locally");
            assertFalse(source.contains("Start-Process"), script + " must not start an external dependency locally");
            assertFalse(source.contains("redis-cli"), script + " must not execute Redis recovery locally");
            assertFalse(source.contains("ossutil"), script + " must not execute OSS recovery locally");
        }
    }

    private void createSchema(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
            statement.execute("CREATE TABLE \"user\" (user_id VARCHAR(64) PRIMARY KEY)");
            statement.execute("CREATE TABLE diary (diary_id VARCHAR(64) PRIMARY KEY, user_id VARCHAR(64) NOT NULL)");
            statement.execute("CREATE TABLE mid_term_memory (id BIGINT PRIMARY KEY, user_id VARCHAR(64) NOT NULL)");
            statement.execute("CREATE TABLE match_profile (id BIGINT PRIMARY KEY, user_id VARCHAR(64) NOT NULL)");
            statement.execute("CREATE TABLE life_graph_entity (id BIGINT PRIMARY KEY, user_id VARCHAR(64) NOT NULL)");
            statement.execute("CREATE TABLE life_graph_relation (id BIGINT PRIMARY KEY, user_id VARCHAR(64) NOT NULL,"
                    + " source_id BIGINT NOT NULL, target_id BIGINT NOT NULL)");
            statement.execute("CREATE TABLE soul_match (id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE soul_connection (id BIGINT PRIMARY KEY, match_id BIGINT NOT NULL)");
            statement.execute("CREATE TABLE image_file (id BIGINT PRIMARY KEY, user_id VARCHAR(64) NOT NULL)");
            statement.execute("CREATE TABLE product_event (event_id VARCHAR(64) PRIMARY KEY, user_id VARCHAR(64) NOT NULL)");
        }
    }

    private void insertValidSnapshot(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO \"user\" (user_id) VALUES ('" + FIXTURE_USER + "')");
            statement.executeUpdate("INSERT INTO diary (diary_id, user_id) VALUES ('" + FIXTURE_DIARY + "', '"
                    + FIXTURE_USER + "')");
            statement.executeUpdate("INSERT INTO mid_term_memory (id, user_id) VALUES (1, '" + FIXTURE_USER + "')");
            statement.executeUpdate("INSERT INTO match_profile (id, user_id) VALUES (1, '" + FIXTURE_USER + "')");
            statement.executeUpdate("INSERT INTO life_graph_entity (id, user_id) VALUES (1, '" + FIXTURE_USER + "')");
            statement.executeUpdate("INSERT INTO life_graph_entity (id, user_id) VALUES (2, '" + FIXTURE_USER + "')");
            statement.executeUpdate("INSERT INTO life_graph_relation (id, user_id, source_id, target_id)"
                    + " VALUES (1, '" + FIXTURE_USER + "', 1, 2)");
            statement.executeUpdate("INSERT INTO soul_match (id) VALUES (1)");
            statement.executeUpdate("INSERT INTO soul_connection (id, match_id) VALUES (1, 1)");
            statement.executeUpdate("INSERT INTO image_file (id, user_id) VALUES (1, '" + FIXTURE_USER + "')");
            statement.executeUpdate("INSERT INTO product_event (event_id, user_id) VALUES ('backup-event-001', '"
                    + FIXTURE_USER + "')");
        }
    }
}

final class BackupRestoreInvariantValidator {

    private BackupRestoreInvariantValidator() {
    }

    static InvariantReport validate(Connection connection) throws SQLException {
        Map<String, Long> rowCounts = new LinkedHashMap<>();
        for (String table : new String[]{
                "user", "diary", "mid_term_memory", "match_profile", "life_graph_entity",
                "life_graph_relation", "soul_match", "soul_connection", "image_file", "product_event"}) {
            rowCounts.put(table, count(connection, "SELECT COUNT(*) FROM \"" + table + "\""));
        }

        Map<String, Long> orphanCounts = new LinkedHashMap<>();
        orphanCounts.put("diary_user", count(connection,
                "SELECT COUNT(*) FROM diary d LEFT JOIN \"user\" u ON u.user_id = d.user_id "
                        + "WHERE u.user_id IS NULL"));
        orphanCounts.put("memory_user", count(connection,
                "SELECT COUNT(*) FROM mid_term_memory m LEFT JOIN \"user\" u ON u.user_id = m.user_id "
                        + "WHERE u.user_id IS NULL"));
        orphanCounts.put("profile_user", count(connection,
                "SELECT COUNT(*) FROM match_profile p LEFT JOIN \"user\" u ON u.user_id = p.user_id "
                        + "WHERE u.user_id IS NULL"));
        orphanCounts.put("entity_user", count(connection,
                "SELECT COUNT(*) FROM life_graph_entity e LEFT JOIN \"user\" u ON u.user_id = e.user_id "
                        + "WHERE u.user_id IS NULL"));
        orphanCounts.put("relation_endpoint", count(connection,
                "SELECT COUNT(*) FROM life_graph_relation r "
                        + "LEFT JOIN life_graph_entity s ON s.id = r.source_id "
                        + "LEFT JOIN life_graph_entity t ON t.id = r.target_id "
                        + "WHERE s.id IS NULL OR t.id IS NULL"));
        orphanCounts.put("connection_match", count(connection,
                "SELECT COUNT(*) FROM soul_connection c LEFT JOIN soul_match m ON m.id = c.match_id "
                        + "WHERE m.id IS NULL"));
        orphanCounts.put("image_user", count(connection,
                "SELECT COUNT(*) FROM image_file i LEFT JOIN \"user\" u ON u.user_id = i.user_id "
                        + "WHERE u.user_id IS NULL"));
        orphanCounts.put("product_event_user", count(connection,
                "SELECT COUNT(*) FROM product_event e LEFT JOIN \"user\" u ON u.user_id = e.user_id "
                        + "WHERE u.user_id IS NULL"));
        return new InvariantReport(rowCounts, orphanCounts);
    }

    private static long count(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    record InvariantReport(Map<String, Long> rowCounts, Map<String, Long> orphanCounts) {

        boolean valid() {
            return orphanCounts.values().stream().allMatch(count -> count == 0);
        }

        long rowCount(String table) {
            return rowCounts.getOrDefault(table, 0L);
        }

        long orphanCount(String relationship) {
            return orphanCounts.getOrDefault(relationship, 0L);
        }

        @Override
        public String toString() {
            return "InvariantReport{rowCounts=" + rowCounts + ", orphanCounts=" + orphanCounts + '}';
        }
    }
}

final class BackupRestoreStaticContract {

    private BackupRestoreStaticContract() {
    }

    static String classifyRedisKey(String key) {
        if (key.startsWith("yusi:langchain:") || key.startsWith("yusi:chunk:")
                || key.startsWith("yusi:md5:")) {
            return "rebuildable-cache";
        }
        if (key.startsWith("yusi:auth:")) {
            return "security-state";
        }
        if (key.startsWith("yusi:usage:")) {
            return "reconcile-with-mysql";
        }
        if (key.startsWith("yusi:violation:count:")) {
            return "review-before-restore";
        }
        if (key.equals("yusi:model:state:instances")) {
            return "rebuildable-runtime";
        }
        if (key.equals("yusi:model:state:channel")) {
            return "non-restorable-channel";
        }
        if (key.equals("yusi:model:runtime:config")) {
            return "restore-from-mysql";
        }
        throw new IllegalArgumentException("unclassified Redis key family");
    }
}
