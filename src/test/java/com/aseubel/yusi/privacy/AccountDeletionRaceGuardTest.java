package com.aseubel.yusi.privacy;

import com.aseubel.yusi.service.privacy.AccountDeletionCoordinator;
import com.aseubel.yusi.service.privacy.AccountDeletionExternalPort;
import com.aseubel.yusi.service.privacy.AccountDeletionInventory;
import com.aseubel.yusi.service.privacy.DeletionResult;
import com.aseubel.yusi.service.security.SecurityAuditService;
import org.h2.api.Trigger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Deterministic replay of the production race where an in-flight cognition-ingest
 * re-writes rows for the target user while the deletion transaction is running:
 * fail-closed on residual, then converge on retry.
 */
@DisplayName("account deletion must fail closed on concurrent writes and converge on retry")
class AccountDeletionRaceGuardTest {

    private static final String TARGET_USER = "fixture-race-delete-target";
    private static final String ADMIN_USER = "fixture-race-delete-admin";
    private static final String JDBC_URL = "jdbc:h2:mem:account_delete_race;MODE=MySQL;"
            + "DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(JDBC_URL, "sa", ""));
        jdbcTemplate.execute("DROP ALL OBJECTS");
        createSchema();
        jdbcTemplate.update("INSERT INTO \"user\" (user_id) VALUES (?), (?)", TARGET_USER, ADMIN_USER);
    }

    @AfterEach
    void tearDown() {
        if (jdbcTemplate != null) {
            jdbcTemplate.execute("DROP ALL OBJECTS");
        }
    }

    @Test
    void concurrentLateWriteMustFailClosedAndConvergeOnRetry() throws SQLException {
        // Simulates the async cognition-ingest writing match_profile for the target
        // while the deletion flow is between its DELETE statements and requireClean.
        jdbcTemplate.execute("CREATE TRIGGER race_late_write BEFORE DELETE ON \"user\" CALL \""
                + LateMatchProfileWrite.class.getName() + "\"");

        DeletionResult first = coordinator().requestDeletion(TARGET_USER, ADMIN_USER);

        assertEquals(DeletionResult.Status.PENDING_RETRY, first.status(),
                "residual written concurrently must fail the deletion closed");
        assertEquals("database_invariant", first.failureCategory());
        assertEquals(1L, countTarget(),
                "target user must survive a failed deletion");

        // Retry path: no new concurrent write, deletion must complete and clean everything.
        jdbcTemplate.execute("DROP TRIGGER race_late_write");
        jdbcTemplate.update("DELETE FROM match_profile WHERE user_id = ?", TARGET_USER);
        DeletionResult second = coordinator().requestDeletion(TARGET_USER, ADMIN_USER);

        assertEquals(DeletionResult.Status.COMPLETED, second.status());
        assertEquals(0L, countTarget());
        assertEquals(0L, countMatchProfile());
    }

    @Test
    void deletionWithoutConcurrentWritesMustCompleteDirectly() {
        DeletionResult result = coordinator().requestDeletion(TARGET_USER, ADMIN_USER);

        assertEquals(DeletionResult.Status.COMPLETED, result.status());
        assertEquals(0L, countTarget());
        assertTrue(result.success());
    }

    private AccountDeletionCoordinator coordinator() {
        AccountDeletionExternalPort noopPort = new AccountDeletionExternalPort() {
            @Override
            public void deleteMilvus(AccountDeletionInventory inventory) {
            }

            @Override
            public void deleteRedis(AccountDeletionInventory inventory) {
            }

            @Override
            public void deleteObjects(AccountDeletionInventory inventory) {
            }
        };
        return new AccountDeletionCoordinator(jdbcTemplate, noopPort, mock(SecurityAuditService.class));
    }

    private long countTarget() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM \"user\" WHERE user_id = ?", Long.class, TARGET_USER);
    }

    private long countMatchProfile() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM match_profile WHERE user_id = ?", Long.class, TARGET_USER);
    }

    /** H2 trigger writing one residual match_profile row when the target user row is deleted. */
    public static class LateMatchProfileWrite implements Trigger {
        @Override
        public void fire(Connection connection, Object[] oldRow, Object[] newRow) throws SQLException {
            // H2 forbids implicit commit inside a trigger; keep the write in the
            // surrounding statement context so requireClean observes the residual.
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement(
                    "INSERT INTO match_profile (user_id) VALUES (?)")) {
                statement.setString(1, TARGET_USER);
                statement.executeUpdate();
            }
        }
    }

    private void createSchema() {
        jdbcTemplate.execute("CREATE TABLE \"user\" (user_id VARCHAR(128) PRIMARY KEY)");
        for (String table : List.of(
                "user_location", "user_notification", "user_persona", "agent_persona_config",
                "cognitive_conflict", "developer_config", "diary", "embedding_task",
                "interface_daily_usage", "life_graph_entity_alias", "life_graph_mention",
                "life_graph_merge_judgment", "life_graph_task", "match_feedback", "match_profile",
                "mid_term_memory", "soul_card", "soul_report", "soul_resonance",
                "life_graph_entity_evidence", "product_event_scope", "security_audit_event_scope",
                "agent_run_trace", "agent_tool_trace", "model_call_trace")) {
            jdbcTemplate.execute("CREATE TABLE " + table + " (user_id VARCHAR(128))");
        }
        jdbcTemplate.execute("CREATE TABLE image_file (user_id VARCHAR(128), object_key VARCHAR(512), "
                + "file_md5 VARCHAR(128))");
        jdbcTemplate.execute("CREATE TABLE situation_scenario (submitter_id VARCHAR(128))");
        jdbcTemplate.execute("CREATE TABLE life_graph_relation_evidence (user_id VARCHAR(128), relation_id BIGINT)");
        jdbcTemplate.execute("CREATE TABLE life_graph_entity (id BIGINT, user_id VARCHAR(128))");
        jdbcTemplate.execute("CREATE TABLE life_graph_relation (id BIGINT, user_id VARCHAR(128), "
                + "source_id BIGINT, target_id BIGINT)");
        jdbcTemplate.execute("CREATE TABLE chat_memory_message (memory_id VARCHAR(128), images VARCHAR(4096))");
        jdbcTemplate.execute("CREATE TABLE resonance_signal (from_user_id VARCHAR(128), to_user_id VARCHAR(128))");
        jdbcTemplate.execute("CREATE TABLE room_message (sender_id VARCHAR(128))");
        jdbcTemplate.execute("CREATE TABLE soul_match (id BIGINT, user_a_id VARCHAR(128), user_b_id VARCHAR(128), "
                + "status_a INT, status_b INT, is_matched BOOLEAN)");
        jdbcTemplate.execute("CREATE TABLE soul_message (sender_id VARCHAR(128), receiver_id VARCHAR(128))");
        jdbcTemplate.execute("CREATE TABLE soul_connection (id BIGINT, match_id BIGINT, "
                + "user_a_id VARCHAR(128), user_b_id VARCHAR(128))");
        jdbcTemplate.execute("CREATE TABLE soul_connection_event (connection_id BIGINT, match_id BIGINT, "
                + "actor_user_id VARCHAR(128))");
        jdbcTemplate.execute("CREATE TABLE product_event (event_id VARCHAR(128), user_id VARCHAR(128), "
                + "actor_user_id VARCHAR(128))");
        jdbcTemplate.execute("CREATE TABLE task_execution (owner_user_id VARCHAR(128), "
                + "trigger_event_id VARCHAR(128), run_id VARCHAR(128))");
        jdbcTemplate.execute("CREATE TABLE security_audit_event (id BIGINT, actor_user_id VARCHAR(128), "
                + "subject_user_id VARCHAR(128), resource_id VARCHAR(128))");
    }
}
