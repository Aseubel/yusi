package com.aseubel.yusi.privacy;

import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.repository.DiaryRepository;
import com.aseubel.yusi.repository.InterfaceDailyUsageRepository;
import com.aseubel.yusi.repository.SituationRoomRepository;
import com.aseubel.yusi.repository.SituationScenarioRepository;
import com.aseubel.yusi.repository.SuggestionRepository;
import com.aseubel.yusi.repository.UserRepository;
import com.aseubel.yusi.redis.service.IRedisService;
import com.aseubel.yusi.service.security.SecurityAuditService;
import com.aseubel.yusi.service.privacy.AccountDeletionCoordinator;
import com.aseubel.yusi.service.privacy.AccountDeletionExternalPort;
import com.aseubel.yusi.service.privacy.AccountDeletionFailure;
import com.aseubel.yusi.service.privacy.AccountDeletionInvariantValidator;
import com.aseubel.yusi.service.privacy.DeletionResult;
import com.aseubel.yusi.service.user.TokenService;
import com.aseubel.yusi.service.user.impl.AdminServiceImpl;
import io.milvus.v2.client.MilvusClientV2;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("application-invariant-only account deletion privacy audit")
class AccountDeletionPrivacyAuditTest {

    private static final String TARGET_USER = "fixture-user-delete-target";
    private static final String CONTROL_USER = "fixture-user-delete-control";
    private static final String ADMIN_USER = "fixture-user-delete-admin";
    private static final String JDBC_URL = "jdbc:h2:mem:account_delete_invariant;MODE=MySQL;"
            + "DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(JDBC_URL, "sa", ""));
        jdbcTemplate.execute("DROP ALL OBJECTS");
        createSchema();
        insertTargetAndControlRows();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        if (jdbcTemplate != null) {
            jdbcTemplate.execute("DROP ALL OBJECTS");
        }
    }

    @Test
    void currentDeregisterEntryMustLeaveNoTargetRowsAcrossAllDataSurfaces() {
        jdbcTemplate.update("INSERT INTO soul_match (id, user_a_id, user_b_id) VALUES (?, ?, ?)",
                3L, TARGET_USER, TARGET_USER);
        jdbcTemplate.update("INSERT INTO soul_match "
                        + "(id, user_a_id, user_b_id, status_a, status_b, is_matched) VALUES (?, ?, ?, ?, ?, ?)",
                4L, CONTROL_USER, TARGET_USER, 2, 1, true);
        jdbcTemplate.update("UPDATE soul_match SET status_a = ?, status_b = ?, is_matched = ? WHERE id = ?",
                1, 2, true, 1L);
        jdbcTemplate.update("INSERT INTO soul_connection (id, match_id, user_a_id, user_b_id) VALUES (?, ?, ?, ?)",
                2L, 3L, TARGET_USER, TARGET_USER);

        User target = User.builder().userId(TARGET_USER).permissionLevel(0).build();
        User admin = User.builder().userId(ADMIN_USER).permissionLevel(10).build();

        UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
        org.mockito.Mockito.when(userRepository.findByUserId(TARGET_USER)).thenReturn(target);
        org.mockito.Mockito.when(userRepository.findByUserId(ADMIN_USER)).thenReturn(admin);

        SituationRoomRepository situationRoomRepository = org.mockito.Mockito.mock(SituationRoomRepository.class);
        org.mockito.Mockito.when(situationRoomRepository.findByMembersContainingOrderByCreatedAtDesc(TARGET_USER))
                .thenReturn(List.of());

        AdminServiceImpl service = new AdminServiceImpl(
                userRepository,
                org.mockito.Mockito.mock(DiaryRepository.class),
                situationRoomRepository,
                org.mockito.Mockito.mock(SituationScenarioRepository.class),
                org.mockito.Mockito.mock(SuggestionRepository.class),
                org.mockito.Mockito.mock(InterfaceDailyUsageRepository.class),
                jdbcTemplate,
                org.mockito.Mockito.mock(TokenService.class),
                org.mockito.Mockito.mock(IRedisService.class),
                org.mockito.Mockito.mock(MilvusClientV2.class),
                org.mockito.Mockito.mock(SecurityAuditService.class));

        UserContext.setUserId(ADMIN_USER);
        service.deregisterUser(TARGET_USER);

        List<Executable> residualChecks = targetOwnedTables().stream()
                .map(table -> (Executable) () -> assertEquals(0L, count(table, ownerColumn(table)),
                        table + " retains target data"))
                .toList();
        assertAll("application-invariant-only target residual counts", residualChecks);
        assertEquals(2L, countAll("user"), "control/admin users must remain");
        assertEquals(1L, countAll("security_audit_event"),
                "retained audit evidence must remain after de-identification");
        assertEquals(0L, countWhere("security_audit_event_scope", "user_id", TARGET_USER),
                "target audit visibility scope must be removed");
        assertEquals(0L, countWhere("security_audit_event", "actor_user_id", TARGET_USER),
                "retained security audit must not keep target actor id");
        assertEquals(0L, countWhere("security_audit_event", "subject_user_id", TARGET_USER),
                "retained security audit must not keep target subject id");
        assertEquals(0L, countWhere("security_audit_event", "resource_id", TARGET_USER),
                "retained security audit must not keep target resource id");
        assertEquals(3L, countAll("soul_match"),
                "shared matches and control-only match row must remain available to the control user");
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM soul_match WHERE id = ? AND user_b_id = ? AND user_a_id IS NULL",
                Long.class, 1L, CONTROL_USER),
                "shared match must retain the control participant without retaining the target identity");
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM soul_match WHERE id = ? AND user_a_id IS NULL AND status_a IS NULL "
                        + "AND status_b = ? AND is_matched = FALSE",
                Long.class, 1L, 2),
                "target status in user_a slot must be cleared with the target identity");
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM soul_match WHERE id = ? AND user_a_id = ? AND user_b_id IS NULL "
                        + "AND status_a = ? AND status_b IS NULL AND is_matched = FALSE",
                Long.class, 4L, CONTROL_USER, 2),
                "target status in user_b slot must be cleared with the target identity");
        assertEquals(1L, countAll("soul_connection"),
                "shared connection lifecycle must remain as de-identified control data");
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM soul_connection WHERE match_id = ? AND user_b_id = ? AND user_a_id <> ?",
                Long.class, 1L, CONTROL_USER, TARGET_USER),
                "shared connection must retain the control participant without the target identity");
    }

    @Test
    void deliberateBrokenRelationEndpointMustFailOrphanInvariant() {
        jdbcTemplate.update("INSERT INTO life_graph_relation (id, user_id, source_id, target_id) VALUES (?, ?, ?, ?)",
                901L, TARGET_USER, 901L, 902L);

        assertThrows(AccountDeletionFailure.class,
                () -> new AccountDeletionInvariantValidator(jdbcTemplate).requireClean(TARGET_USER),
                "deliberate broken reference must remain a failing invariant");
    }

    @Test
    void graphEvidenceAndConnectionEventOrphansMustFailInvariant() {
        jdbcTemplate.update("INSERT INTO life_graph_relation_evidence (user_id, relation_id) VALUES (?, ?)",
                CONTROL_USER, 991L);
        jdbcTemplate.update("INSERT INTO soul_connection_event (connection_id, match_id, actor_user_id) "
                        + "VALUES (?, ?, ?)",
                991L, 991L, CONTROL_USER);

        assertThrows(AccountDeletionFailure.class,
                () -> new AccountDeletionInvariantValidator(jdbcTemplate).requireClean(TARGET_USER),
                "unowned orphan references must remain a failing invariant");
    }

    @Test
    void inventoryMustIncludeChatImagesAndUploadSessionKeys() {
        jdbcTemplate.update("UPDATE chat_memory_message SET images = ? WHERE memory_id = ?",
                "[\"fixture-chat-image-reference\"]", TARGET_USER);
        jdbcTemplate.update("UPDATE image_file SET object_key = ?, file_md5 = ? WHERE user_id = ?",
                "fixture-image-reference", "fixture-file-digest", TARGET_USER);
        jdbcTemplate.update("INSERT INTO image_file (user_id, object_key, file_md5) VALUES (?, ?, ?)",
                CONTROL_USER, "fixture-chat-image-reference", "fixture-control-digest");

        AtomicReference<com.aseubel.yusi.service.privacy.AccountDeletionInventory> captured = new AtomicReference<>();
        AccountDeletionExternalPort capturingPort = new AccountDeletionExternalPort() {
            @Override
            public void deleteMilvus(com.aseubel.yusi.service.privacy.AccountDeletionInventory inventory) {
                captured.set(inventory);
            }

            @Override
            public void deleteRedis(com.aseubel.yusi.service.privacy.AccountDeletionInventory inventory) {
            }

            @Override
            public void deleteObjects(com.aseubel.yusi.service.privacy.AccountDeletionInventory inventory) {
            }
        };

        DeletionResult result = new AccountDeletionCoordinator(
                jdbcTemplate, capturingPort, mock(SecurityAuditService.class))
                .requestDeletion(TARGET_USER, ADMIN_USER);

        assertEquals(DeletionResult.Status.COMPLETED, result.status());
        assertEquals(Set.of("fixture-image-reference"),
                captured.get().imageObjectKeys());
        assertTrue(captured.get().exactRedisKeys().contains(
                "yusi:md5:" + TARGET_USER + ":fixture-file-digest"));
        assertTrue(captured.get().exactRedisKeys().contains(
                "yusi:chunk:" + TARGET_USER + ":fixture-file-digest:uploadId"));
    }

    @Test
    void externalFailureMustRemainPendingRetryAndMustNotDeleteDatabaseOrWriteSuccessAudit() {
        SecurityAuditService auditService = mock(SecurityAuditService.class);
        AccountDeletionExternalPort failingPort = new AccountDeletionExternalPort() {
            @Override
            public void deleteMilvus(com.aseubel.yusi.service.privacy.AccountDeletionInventory inventory) {
                throw new IllegalStateException("fixture-external-failure");
            }

            @Override
            public void deleteRedis(com.aseubel.yusi.service.privacy.AccountDeletionInventory inventory) {
            }

            @Override
            public void deleteObjects(com.aseubel.yusi.service.privacy.AccountDeletionInventory inventory) {
            }
        };

        AccountDeletionCoordinator coordinator = new AccountDeletionCoordinator(
                jdbcTemplate, failingPort, auditService);

        DeletionResult result = coordinator.requestDeletion(TARGET_USER, ADMIN_USER);

        assertEquals(DeletionResult.Status.PENDING_RETRY, result.status());
        assertEquals(1L, countWhere("user", "user_id", TARGET_USER));
        assertFalse(result.success());
        verifyNoInteractions(auditService);
    }

    @Test
    void externalCleanupMustInventoryAndDeleteOssBeforeClearingRedisSessions() {
        List<String> order = new ArrayList<>();
        AccountDeletionExternalPort orderedPort = new AccountDeletionExternalPort() {
            @Override
            public void deleteMilvus(com.aseubel.yusi.service.privacy.AccountDeletionInventory inventory) {
                order.add("milvus");
            }

            @Override
            public void deleteRedis(com.aseubel.yusi.service.privacy.AccountDeletionInventory inventory) {
                order.add("redis");
            }

            @Override
            public void deleteObjects(com.aseubel.yusi.service.privacy.AccountDeletionInventory inventory) {
                order.add("objects");
            }
        };

        DeletionResult result = new AccountDeletionCoordinator(
                jdbcTemplate, orderedPort, mock(SecurityAuditService.class))
                .requestDeletion(TARGET_USER, ADMIN_USER);

        assertEquals(DeletionResult.Status.COMPLETED, result.status());
        assertEquals(List.of("milvus", "objects", "redis"), order,
                "chunk object references must be read before Redis session keys are removed");
    }

    private void createSchema() {
        jdbcTemplate.execute("CREATE TABLE \"user\" (user_id VARCHAR(128) PRIMARY KEY)");
        for (String table : List.of(
                "user_location", "user_notification", "user_persona", "agent_persona_config",
                "cognitive_conflict", "developer_config", "diary", "embedding_task",
                "interface_daily_usage", "life_graph_entity_alias", "life_graph_mention",
                "life_graph_merge_judgment",
                "life_graph_task", "match_feedback", "match_profile", "mid_term_memory", "soul_card",
                "soul_report", "soul_resonance", "life_graph_entity_evidence",
                "product_event_scope", "security_audit_event_scope", "agent_run_trace", "agent_tool_trace")) {
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
        jdbcTemplate.execute("CREATE TABLE soul_connection (id BIGINT, match_id BIGINT, user_a_id VARCHAR(128), user_b_id VARCHAR(128))");
        jdbcTemplate.execute("CREATE TABLE soul_connection_event (connection_id BIGINT, match_id BIGINT, actor_user_id VARCHAR(128))");
        jdbcTemplate.execute("CREATE TABLE product_event (event_id VARCHAR(128), user_id VARCHAR(128), actor_user_id VARCHAR(128))");
        jdbcTemplate.execute("CREATE TABLE task_execution (owner_user_id VARCHAR(128), trigger_event_id VARCHAR(128), run_id VARCHAR(128))");
        jdbcTemplate.execute("CREATE TABLE security_audit_event (id BIGINT, actor_user_id VARCHAR(128), "
                + "subject_user_id VARCHAR(128), resource_id VARCHAR(128))");
        jdbcTemplate.execute("CREATE TABLE model_call_trace (user_id VARCHAR(128))");
    }

    private void insertTargetAndControlRows() {
        jdbcTemplate.update("INSERT INTO \"user\" (user_id) VALUES (?), (?), (?)",
                TARGET_USER, CONTROL_USER, ADMIN_USER);
        for (String table : List.of(
                "user_location", "user_notification", "user_persona", "agent_persona_config",
                "cognitive_conflict", "developer_config", "diary", "embedding_task", "image_file",
                "interface_daily_usage", "life_graph_entity_alias", "life_graph_mention",
                "life_graph_merge_judgment", "life_graph_relation",
                "life_graph_task", "match_feedback", "match_profile", "mid_term_memory", "soul_card",
                "soul_report", "soul_resonance", "life_graph_entity_evidence",
                "product_event_scope", "security_audit_event_scope", "agent_run_trace", "agent_tool_trace")) {
            if (table.equals("life_graph_relation")) {
                jdbcTemplate.update("INSERT INTO life_graph_relation (id, user_id, source_id, target_id) "
                                + "VALUES (?, ?, ?, ?), (?, ?, ?, ?)",
                        1L, TARGET_USER, 1L, 2L, 2L, CONTROL_USER, 3L, 4L);
            } else {
                jdbcTemplate.update("INSERT INTO " + table + " (user_id) VALUES (?), (?)",
                        TARGET_USER, CONTROL_USER);
            }
        }
        jdbcTemplate.update("INSERT INTO life_graph_relation_evidence (user_id, relation_id) VALUES (?, ?), (?, ?)",
                TARGET_USER, 1L, CONTROL_USER, 2L);
        jdbcTemplate.update("INSERT INTO situation_scenario (submitter_id) VALUES (?), (?)",
                TARGET_USER, CONTROL_USER);
        jdbcTemplate.update("INSERT INTO life_graph_entity (id, user_id) VALUES (?, ?), (?, ?), (?, ?), (?, ?)",
                1L, TARGET_USER, 2L, CONTROL_USER, 3L, CONTROL_USER, 4L, CONTROL_USER);
        jdbcTemplate.update("INSERT INTO chat_memory_message (memory_id) VALUES (?), (?)", TARGET_USER, CONTROL_USER);
        jdbcTemplate.update("INSERT INTO resonance_signal (from_user_id, to_user_id) VALUES (?, ?), (?, ?)",
                TARGET_USER, CONTROL_USER, CONTROL_USER, TARGET_USER);
        jdbcTemplate.update("INSERT INTO room_message (sender_id) VALUES (?), (?)", TARGET_USER, CONTROL_USER);
        jdbcTemplate.update("INSERT INTO soul_match (id, user_a_id, user_b_id) VALUES (?, ?, ?), (?, ?, ?)",
                1L, TARGET_USER, CONTROL_USER, 2L, CONTROL_USER, CONTROL_USER);
        jdbcTemplate.update("INSERT INTO soul_message (sender_id, receiver_id) VALUES (?, ?), (?, ?)",
                TARGET_USER, CONTROL_USER, CONTROL_USER, CONTROL_USER);
        jdbcTemplate.update("INSERT INTO soul_connection (id, match_id, user_a_id, user_b_id) VALUES (?, ?, ?, ?)",
                1L, 1L, TARGET_USER, CONTROL_USER);
        jdbcTemplate.update("INSERT INTO soul_connection_event (connection_id, match_id, actor_user_id) VALUES (?, ?, ?)",
                1L, 1L, TARGET_USER);
        jdbcTemplate.update("INSERT INTO product_event (event_id, user_id, actor_user_id) VALUES (?, ?, ?)",
                "fixture-event-delete-target", TARGET_USER, CONTROL_USER);
        jdbcTemplate.update("INSERT INTO task_execution (owner_user_id, trigger_event_id, run_id) VALUES (?, ?, ?)",
                TARGET_USER, "fixture-event-delete-target", "fixture-run-delete-target");
        jdbcTemplate.update("INSERT INTO security_audit_event "
                        + "(id, actor_user_id, subject_user_id, resource_id) VALUES (?, ?, ?, ?)",
                1L, TARGET_USER, TARGET_USER, TARGET_USER);
        jdbcTemplate.update("INSERT INTO model_call_trace (user_id) VALUES (?)", TARGET_USER);
    }

    private List<String> targetOwnedTables() {
        return List.of(
                "user_location", "user_notification", "user_persona", "agent_persona_config",
                "cognitive_conflict", "developer_config", "diary", "embedding_task", "image_file",
                "interface_daily_usage", "life_graph_entity", "life_graph_entity_alias", "life_graph_mention",
                "life_graph_merge_judgment", "life_graph_relation_evidence", "life_graph_relation",
                "life_graph_task", "match_feedback", "match_profile", "mid_term_memory", "soul_card",
                "soul_report", "soul_resonance", "situation_scenario", "life_graph_entity_evidence",
                "product_event_scope", "security_audit_event_scope", "agent_run_trace", "agent_tool_trace",
                "model_call_trace", "chat_memory_message", "task_execution", "product_event");
    }

    private String ownerColumn(String table) {
        return switch (table) {
            case "chat_memory_message" -> "memory_id";
            case "task_execution" -> "owner_user_id";
            case "situation_scenario" -> "submitter_id";
            default -> "user_id";
        };
    }

    private long count(String table, String column) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", Long.class, TARGET_USER);
    }

    private long countWhere(String table, String column, String value) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", Long.class, value);
    }

    private long countAll(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }
}
