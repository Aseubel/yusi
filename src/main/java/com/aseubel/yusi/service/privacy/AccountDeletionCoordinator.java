package com.aseubel.yusi.service.privacy;

import com.aseubel.yusi.pojo.constant.SecurityAuditAction;
import com.aseubel.yusi.pojo.constant.SecurityAuditDetailKeys;
import com.aseubel.yusi.pojo.constant.SecurityAuditOperation;
import com.aseubel.yusi.pojo.constant.SecurityAuditOutcome;
import com.aseubel.yusi.pojo.constant.SecurityAuditReasonCode;
import com.aseubel.yusi.pojo.constant.SecurityAuditResourceType;
import com.aseubel.yusi.pojo.entity.AccountDeletionRequest;
import com.aseubel.yusi.repository.AccountDeletionRequestRepository;
import com.aseubel.yusi.service.security.SecurityAuditService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Coordinates account deletion with retry-safe, failure-closed semantics. */
@Slf4j
@Service
public class AccountDeletionCoordinator {

    private static final String USAGE_PREFIX = "yusi:usage:";
    private static final String CHUNK_PREFIX = "yusi:chunk:";
    private static final String MD5_PREFIX = "yusi:md5:";
    private static final String DEIDENTIFIED_USER_REF = "account-deleted";
    private static final String FIELD_SEPARATOR = "\u0001";

    private final JdbcTemplate jdbcTemplate;
    private final AccountDeletionExternalPort externalPort;
    private final SecurityAuditService securityAuditService;
    private final AccountDeletionRequestRepository requestRepository;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    @Autowired
    public AccountDeletionCoordinator(JdbcTemplate jdbcTemplate,
            AccountDeletionExternalPort externalPort,
            SecurityAuditService securityAuditService,
            AccountDeletionRequestRepository requestRepository,
            PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.externalPort = externalPort;
        this.securityAuditService = securityAuditService;
        this.requestRepository = requestRepository;
        this.objectMapper = new ObjectMapper();
        this.transactionManager = transactionManager;
    }

    /** Constructor used by application-invariant tests without a JPA context. */
    public AccountDeletionCoordinator(JdbcTemplate jdbcTemplate,
            AccountDeletionExternalPort externalPort,
            SecurityAuditService securityAuditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.externalPort = externalPort;
        this.securityAuditService = securityAuditService;
        this.requestRepository = null;
        this.objectMapper = new ObjectMapper();
        this.transactionManager = null;
    }

    /** Compatibility constructor for callers that provide a repository but no transaction manager. */
    public AccountDeletionCoordinator(JdbcTemplate jdbcTemplate,
            AccountDeletionExternalPort externalPort,
            SecurityAuditService securityAuditService,
            AccountDeletionRequestRepository requestRepository) {
        this(jdbcTemplate, externalPort, securityAuditService, requestRepository, null);
    }

    public DeletionResult requestDeletion(String targetUserId, String adminUserId) {
        String requestId = newRequestId();
        AccountDeletionRequest request = createRequest(requestId, targetUserId, adminUserId);
        try {
            Runnable deletion = () -> {
                AccountDeletionInventory inventory = collectInventory(targetUserId);
                markRunning(request);
                externalPort.deleteMilvus(inventory);
                externalPort.deleteObjects(inventory);
                externalPort.deleteRedis(inventory);
                deleteChildFirst(inventory);
                new AccountDeletionInvariantValidator(jdbcTemplate).requireClean(targetUserId);
                markCompleted(request);
                recordDeidentifiedSuccess(requestId, adminUserId);
            };
            if (transactionManager == null) {
                deletion.run();
            } else {
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> deletion.run());
            }
            return DeletionResult.completed(requestId);
        } catch (AccountDeletionFailure failure) {
            markRetry(request, failure.category());
            log.warn("Account deletion pending retry: operation=account_delete, failureCategory={}",
                    failure.category());
            return DeletionResult.pendingRetry(requestId, failure.category());
        } catch (Exception exception) {
            markRetry(request, "external_or_database");
            log.warn("Account deletion pending retry: operation=account_delete, failureCategory=external_or_database");
            return DeletionResult.pendingRetry(requestId, "external_or_database");
        }
    }

    private AccountDeletionRequest createRequest(String requestId, String targetUserId, String adminUserId) {
        AccountDeletionRequest request = AccountDeletionRequest.builder()
                .requestId(requestId)
                .targetUserRef(targetUserId)
                .requestedByRef(adminUserId)
                .status(AccountDeletionRequest.Status.PENDING)
                .retryCount(0)
                .build();
        return requestRepository == null ? request : requestRepository.save(request);
    }

    private void markRunning(AccountDeletionRequest request) {
        request.setStatus(AccountDeletionRequest.Status.RUNNING);
        request.setUpdatedAt(java.time.LocalDateTime.now());
        save(request);
    }

    private void markRetry(AccountDeletionRequest request, String category) {
        request.setStatus(AccountDeletionRequest.Status.PENDING_RETRY);
        request.setFailureCategory(category);
        request.setRetryCount((request.getRetryCount() == null ? 0 : request.getRetryCount()) + 1);
        request.setUpdatedAt(java.time.LocalDateTime.now());
        save(request);
    }

    private void markCompleted(AccountDeletionRequest request) {
        request.setStatus(AccountDeletionRequest.Status.COMPLETED);
        request.setTargetUserRef(null);
        request.setFailureCategory(null);
        request.setCompletedAt(java.time.LocalDateTime.now());
        request.setUpdatedAt(java.time.LocalDateTime.now());
        save(request);
    }

    private void save(AccountDeletionRequest request) {
        if (requestRepository != null) {
            requestRepository.save(request);
        }
    }

    private AccountDeletionInventory collectInventory(String targetUserId) {
        AccountDeletionInventory inventory = new AccountDeletionInventory(targetUserId);
        collectDiaryReferences(inventory);
        collectChatReferences(inventory);
        collectImageReferences(inventory);
        collectRelationalReferences(inventory);
        collectUsageFields(inventory);
        collectExactCacheKeys(inventory);
        return inventory;
    }

    private void collectDiaryReferences(AccountDeletionInventory inventory) {
        if (!tableExists("diary") || !columnExists("diary", "user_id")) {
            return;
        }
        List<String> columns = new ArrayList<>();
        if (columnExists("diary", "diary_id")) {
            columns.add("diary_id");
        }
        if (columnExists("diary", "images")) {
            columns.add("images");
        }
        if (columnExists("diary", "audio_object_key")) {
            columns.add("audio_object_key");
        }
        if (columnExists("diary", "attachment_bindings")) {
            columns.add("attachment_bindings");
        }
        if (columns.isEmpty()) {
            return;
        }
        String sql = "SELECT " + String.join(", ", columns) + " FROM diary WHERE user_id = ?";
        jdbcTemplate.query(sql, new Object[]{inventory.targetUserId()},
                (org.springframework.jdbc.core.ResultSetExtractor<Void>) rs -> {
                    while (rs.next()) {
                        if (columns.contains("diary_id")) {
                            inventory.addDiaryId(rs.getString("diary_id"));
                        }
                        if (columns.contains("images")) {
                            addJsonStrings(rs.getString("images"), inventory, ReferenceKind.IMAGE);
                        }
                        if (columns.contains("audio_object_key")) {
                            inventory.addAudioObjectKey(rs.getString("audio_object_key"));
                        }
                        if (columns.contains("attachment_bindings")) {
                            addJsonStrings(rs.getString("attachment_bindings"), inventory, ReferenceKind.ATTACHMENT);
                        }
                    }
                    return null;
                });
    }

    private void collectChatReferences(AccountDeletionInventory inventory) {
        if (!tableExists("chat_memory_message") || !columnExists("chat_memory_message", "memory_id")
                || !columnExists("chat_memory_message", "images")) {
            return;
        }
        jdbcTemplate.query("SELECT images FROM chat_memory_message WHERE memory_id = ?",
                new Object[]{inventory.targetUserId()},
                (org.springframework.jdbc.core.ResultSetExtractor<Void>) rs -> {
                    while (rs.next()) {
                        addJsonStrings(rs.getString("images"), inventory, ReferenceKind.IMAGE);
                    }
                    return null;
                });
    }

    private void collectImageReferences(AccountDeletionInventory inventory) {
        if (!tableExists("image_file") || !columnExists("image_file", "user_id")
                || !columnExists("image_file", "object_key")) {
            return;
        }
        List<String> columns = new ArrayList<>(List.of("object_key"));
        if (columnExists("image_file", "file_md5")) {
            columns.add("file_md5");
        }
        jdbcTemplate.query("SELECT " + String.join(", ", columns)
                        + " FROM image_file WHERE user_id = ?",
                new Object[]{inventory.targetUserId()},
                (org.springframework.jdbc.core.ResultSetExtractor<Void>) rs -> {
                    while (rs.next()) {
                        String objectKey = rs.getString("object_key");
                        Long references = jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM image_file WHERE object_key = ? AND user_id <> ?",
                                Long.class, objectKey, inventory.targetUserId());
                        if (references == null || references == 0L) {
                            inventory.addImageObjectKey(objectKey);
                        }
                        if (columns.contains("file_md5")) {
                            addUploadSessionKeys(inventory, rs.getString("file_md5"));
                        }
                    }
                    return null;
                });
    }

    private void addUploadSessionKeys(AccountDeletionInventory inventory, String fileMd5) {
        if (fileMd5 == null || fileMd5.isBlank()) {
            return;
        }
        String base = CHUNK_PREFIX + inventory.targetUserId() + ":" + fileMd5;
        inventory.addExactRedisKey(MD5_PREFIX + inventory.targetUserId() + ":" + fileMd5);
        for (String suffix : List.of(":uploadId", ":totalChunks", ":uploadedCount", ":bytes")) {
            inventory.addExactRedisKey(base + suffix);
        }
    }

    private void collectRelationalReferences(AccountDeletionInventory inventory) {
        collectColumnValues("life_graph_entity", "id", "user_id", inventory.targetUserId(),
                inventory::addGraphEntityId);
        collectColumnValues("life_graph_relation", "id", "user_id", inventory.targetUserId(),
                inventory::addGraphRelationId);
        collectParticipantColumnValues("soul_match", "id", inventory.targetUserId(),
                inventory::addSoulMatchId);
        collectParticipantColumnValues("soul_connection", "id", inventory.targetUserId(),
                inventory::addSoulConnectionId);
        collectProductEventIds(inventory);
        collectColumnValues("agent_run_trace", "run_id", "user_id", inventory.targetUserId(), inventory::addRunId);
        collectColumnValues("agent_tool_trace", "run_id", "user_id", inventory.targetUserId(), inventory::addRunId);
        collectColumnValues("model_call_trace", "run_id", "user_id", inventory.targetUserId(), inventory::addRunId);
        collectColumnValues("task_execution", "run_id", "owner_user_id", inventory.targetUserId(), inventory::addRunId);
    }

    private void collectProductEventIds(AccountDeletionInventory inventory) {
        if (!tableExists("product_event") || !columnExists("product_event", "event_id")) {
            return;
        }
        List<String> ownerColumns = List.of("user_id", "actor_user_id").stream()
                .filter(column -> columnExists("product_event", column)).toList();
        if (ownerColumns.isEmpty()) {
            return;
        }
        String predicate = ownerColumns.stream().map(column -> column + " = ?")
                .collect(java.util.stream.Collectors.joining(" OR "));
        Object[] args = java.util.stream.Stream.generate(() -> (Object) inventory.targetUserId())
                .limit(ownerColumns.size()).toArray();
        jdbcTemplate.query("SELECT event_id FROM product_event WHERE " + predicate, args,
                (org.springframework.jdbc.core.ResultSetExtractor<Void>) rs -> {
                    while (rs.next()) {
                        inventory.addProductEventId(rs.getString("event_id"));
                    }
                    return null;
                });
    }

    private void collectColumnValues(String table, String selectedColumn, String ownerColumn,
            String targetUserId, Consumer<Object> consumer) {
        if (!tableExists(table) || !columnExists(table, selectedColumn) || !columnExists(table, ownerColumn)) {
            return;
        }
        jdbcTemplate.query("SELECT " + selectedColumn + " FROM " + table + " WHERE " + ownerColumn + " = ?",
                new Object[]{targetUserId},
                (org.springframework.jdbc.core.ResultSetExtractor<Void>) rs -> {
                    while (rs.next()) {
                        consumer.accept(rs.getObject(selectedColumn));
                    }
                    return null;
                });
    }

    private void collectParticipantColumnValues(String table, String selectedColumn,
            String targetUserId, Consumer<Object> consumer) {
        if (!tableExists(table) || !columnExists(table, selectedColumn)
                || !columnExists(table, "user_a_id") || !columnExists(table, "user_b_id")) {
            return;
        }
        jdbcTemplate.query("SELECT " + selectedColumn + " FROM " + table
                        + " WHERE user_a_id = ? OR user_b_id = ?",
                new Object[]{targetUserId, targetUserId},
                (org.springframework.jdbc.core.ResultSetExtractor<Void>) rs -> {
                    while (rs.next()) {
                        consumer.accept(rs.getObject(selectedColumn));
                    }
                    return null;
                });
    }

    private void collectUsageFields(AccountDeletionInventory inventory) {
        if (!tableExists("interface_daily_usage") || !columnExists("interface_daily_usage", "user_id")) {
            return;
        }
        List<String> columns = new ArrayList<>();
        if (columnExists("interface_daily_usage", "usage_date")) {
            columns.add("usage_date");
        }
        if (columnExists("interface_daily_usage", "ip")) {
            columns.add("ip");
        }
        if (columnExists("interface_daily_usage", "interface_name")) {
            columns.add("interface_name");
        }
        if (columns.size() != 3) {
            return;
        }
        jdbcTemplate.query("SELECT usage_date, ip, interface_name FROM interface_daily_usage WHERE user_id = ?",
                new Object[]{inventory.targetUserId()},
                (org.springframework.jdbc.core.ResultSetExtractor<Void>) rs -> {
                    while (rs.next()) {
                        LocalDate date = rs.getObject("usage_date", LocalDate.class);
                        String dateText = date == null ? rs.getString("usage_date") : date.toString();
                        String field = inventory.targetUserId() + FIELD_SEPARATOR
                                + rs.getString("ip") + FIELD_SEPARATOR + rs.getString("interface_name");
                        inventory.addUsageField(USAGE_PREFIX + dateText, field);
                    }
                    return null;
                });
    }

    private void collectExactCacheKeys(AccountDeletionInventory inventory) {
        String userId = inventory.targetUserId();
        for (String key : List.of(
                "yusi:user:data:" + userId,
                "yusi:user:admin:" + userId,
                "yusi:match:list:" + userId,
                "yusi:notifications:user:" + userId + ":unread",
                "yusi:notifications:user:" + userId + ":unread-count",
                "yusi:diary:footprints:" + userId)) {
            inventory.addExactRedisKey(key);
        }
        for (String diaryId : inventory.diaryIds()) {
            inventory.addExactRedisKey("yusi:diary:detail:v4:" + diaryId + ":" + userId);
        }
    }

    private void addJsonStrings(String json, AccountDeletionInventory inventory, ReferenceKind kind) {
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) {
                return;
            }
            for (JsonNode item : root) {
                JsonNode keyNode = item.isTextual() ? item : item.get("objectKey");
                if (keyNode == null || !keyNode.isTextual() || keyNode.textValue().isBlank()) {
                    continue;
                }
                if (kind == ReferenceKind.IMAGE) {
                    if (!isReferencedByAnotherUser(keyNode.textValue(), inventory.targetUserId())) {
                        inventory.addImageObjectKey(keyNode.textValue());
                    }
                } else {
                    inventory.addAttachmentObjectKey(keyNode.textValue());
                }
            }
        } catch (Exception ignored) {
            // An unreadable reference is a deletion blocker at the external boundary.
            throw new AccountDeletionFailure("object_inventory_unreadable");
        }
    }

    private void deleteChildFirst(AccountDeletionInventory inventory) {
        String targetUserId = inventory.targetUserId();
        deleteCrossOwnerDependents(inventory);
        cleanSituationRooms(targetUserId);
        List<DeleteStatement> statements = List.of(
                new DeleteStatement("security_audit_event_scope",
                        "DELETE FROM security_audit_event_scope WHERE user_id = ?", false),
                new DeleteStatement("product_event_scope",
                        "DELETE FROM product_event_scope WHERE user_id = ?", false),
                new DeleteStatement("room_message",
                        "DELETE FROM room_message WHERE sender_id = ?", false),
                new DeleteStatement("chat_memory_message",
                        "DELETE FROM chat_memory_message WHERE memory_id = ?", false),
                new DeleteStatement("agent_tool_trace",
                        "DELETE FROM agent_tool_trace WHERE user_id = ?", false),
                new DeleteStatement("agent_run_trace",
                        "DELETE FROM agent_run_trace WHERE user_id = ?", false),
                new DeleteStatement("model_call_trace",
                        "DELETE FROM model_call_trace WHERE user_id = ?", false),
                new DeleteStatement("task_execution",
                        "DELETE FROM task_execution WHERE owner_user_id = ?", false),
                new DeleteStatement("product_event",
                        "DELETE FROM product_event WHERE user_id = ? OR actor_user_id = ?", true),
                new DeleteStatement("life_graph_entity_evidence",
                        "DELETE FROM life_graph_entity_evidence WHERE user_id = ?", false),
                new DeleteStatement("life_graph_relation_evidence",
                        "DELETE FROM life_graph_relation_evidence WHERE user_id = ?", false),
                new DeleteStatement("life_graph_mention",
                        "DELETE FROM life_graph_mention WHERE user_id = ?", false),
                new DeleteStatement("life_graph_entity_alias",
                        "DELETE FROM life_graph_entity_alias WHERE user_id = ?", false),
                new DeleteStatement("life_graph_merge_judgment",
                        "DELETE FROM life_graph_merge_judgment WHERE user_id = ?", false),
                new DeleteStatement("life_graph_relation",
                        "DELETE FROM life_graph_relation WHERE user_id = ?", false),
                new DeleteStatement("life_graph_entity",
                        "DELETE FROM life_graph_entity WHERE user_id = ?", false),
                new DeleteStatement("resonance_signal",
                        "DELETE FROM resonance_signal WHERE from_user_id = ? OR to_user_id = ?", true),
                new DeleteStatement("soul_resonance",
                        "DELETE FROM soul_resonance WHERE user_id = ?", false),
                new DeleteStatement("match_feedback",
                        "DELETE FROM match_feedback WHERE user_id = ?", false),
                new DeleteStatement("match_profile",
                        "DELETE FROM match_profile WHERE user_id = ?", false),
                new DeleteStatement("soul_card",
                        "DELETE FROM soul_card WHERE user_id = ?", false),
                new DeleteStatement("soul_report",
                        "DELETE FROM soul_report WHERE user_id = ?", false),
                new DeleteStatement("situation_scenario",
                        "DELETE FROM situation_scenario WHERE submitter_id = ?", false),
                new DeleteStatement("cognitive_conflict",
                        "DELETE FROM cognitive_conflict WHERE user_id = ?", false),
                new DeleteStatement("life_graph_task",
                        "DELETE FROM life_graph_task WHERE user_id = ?", false),
                new DeleteStatement("embedding_task",
                        "DELETE FROM embedding_task WHERE user_id = ?", false),
                new DeleteStatement("interface_daily_usage",
                        "DELETE FROM interface_daily_usage WHERE user_id = ?", false),
                new DeleteStatement("developer_config",
                        "DELETE FROM developer_config WHERE user_id = ?", false),
                new DeleteStatement("user_notification",
                        "DELETE FROM user_notification WHERE user_id = ?", false),
                new DeleteStatement("user_location",
                        "DELETE FROM user_location WHERE user_id = ?", false),
                new DeleteStatement("user_persona",
                        "DELETE FROM user_persona WHERE user_id = ?", false),
                new DeleteStatement("agent_persona_config",
                        "DELETE FROM agent_persona_config WHERE user_id = ?", false),
                new DeleteStatement("diary",
                        "DELETE FROM diary WHERE user_id = ?", false),
                new DeleteStatement("image_file",
                        "DELETE FROM image_file WHERE user_id = ?", false),
                new DeleteStatement("mid_term_memory",
                        "DELETE FROM mid_term_memory WHERE user_id = ?", false),
                new DeleteStatement("security_audit_event",
                        "UPDATE security_audit_event SET actor_user_id = NULL, subject_user_id = NULL WHERE actor_user_id = ? OR subject_user_id = ?",
                        true),
                new DeleteStatement("user",
                        "DELETE FROM user WHERE user_id = ?", false));

        for (DeleteStatement statement : statements) {
            if (!tableExists(statement.table()) && !statement.table().equals("user")) {
                continue;
            }
            String sql = adaptStatement(statement);
            if (statement.table().equals("security_audit_event")
                    && columnExists("security_audit_event", "resource_id")) {
                jdbcTemplate.update(sql, targetUserId, targetUserId, targetUserId);
            } else if (statement.twoParameters()) {
                jdbcTemplate.update(sql, targetUserId, targetUserId);
            } else {
                jdbcTemplate.update(sql, targetUserId);
            }
        }
    }

    private void deleteCrossOwnerDependents(AccountDeletionInventory inventory) {
        String targetUserId = inventory.targetUserId();
        deleteGraphDependents(inventory);
        deleteMatchDependents(inventory);
        deleteProductEventDependents(inventory);
        deleteByIds("chat_memory_message", "source_event_id", inventory.productEventIds());
        deleteByIds("chat_memory_message", "run_id", inventory.runIds());
        deleteByIds("agent_tool_trace", "run_id", inventory.runIds());
        deleteByIds("agent_run_trace", "run_id", inventory.runIds());
        deleteByIds("model_call_trace", "run_id", inventory.runIds());
    }

    private void deleteGraphDependents(AccountDeletionInventory inventory) {
        String targetUserId = inventory.targetUserId();
        deleteByIds("life_graph_relation_evidence", "relation_id", inventory.graphRelationIds());
        deleteByIds("life_graph_entity_alias", "entity_id", inventory.graphEntityIds());
        deleteByIds("life_graph_mention", "entity_id", inventory.graphEntityIds());
        deleteByIds("life_graph_entity_evidence", "entity_id", inventory.graphEntityIds());
        deleteByIds("life_graph_relation", "id", inventory.graphRelationIds());
        if (tableExists("life_graph_relation") && tableExists("life_graph_entity")) {
            if (tableExists("life_graph_relation_evidence")
                    && columnExists("life_graph_relation_evidence", "relation_id")) {
                jdbcTemplate.update("DELETE FROM life_graph_relation_evidence WHERE relation_id IN "
                                + "(SELECT id FROM life_graph_relation WHERE user_id = ? OR source_id IN "
                                + "(SELECT id FROM life_graph_entity WHERE user_id = ?) OR target_id IN "
                                + "(SELECT id FROM life_graph_entity WHERE user_id = ?))",
                        targetUserId, targetUserId, targetUserId);
            }
            jdbcTemplate.update("DELETE FROM life_graph_relation WHERE user_id = ? OR source_id IN "
                            + "(SELECT id FROM life_graph_entity WHERE user_id = ?) OR target_id IN "
                            + "(SELECT id FROM life_graph_entity WHERE user_id = ?)",
                    targetUserId, targetUserId, targetUserId);
        }
        if (tableExists("life_graph_entity")) {
            for (String table : List.of("life_graph_entity_alias", "life_graph_mention",
                    "life_graph_entity_evidence")) {
                if (tableExists(table) && columnExists(table, "entity_id")) {
                    jdbcTemplate.update("DELETE FROM " + table + " WHERE entity_id IN "
                                    + "(SELECT id FROM life_graph_entity WHERE user_id = ?)",
                            targetUserId);
                }
            }
        }
    }

    private void deleteMatchDependents(AccountDeletionInventory inventory) {
        deidentifySharedMatchData(inventory);
    }

    /**
     * A shared match is not an owned row. Remove the target's identity and
     * private payload while retaining the control user's lifecycle record.
     */
    private void deidentifySharedMatchData(AccountDeletionInventory inventory) {
        String targetUserId = inventory.targetUserId();
        Set<String> targetOnlyMatchIds = findTargetOnlyMatchIds(targetUserId);
        deleteTargetOnlyMatchDependents(targetOnlyMatchIds);
        deidentifySharedMatch(targetUserId);
        deidentifySharedConnection(targetUserId);
        deidentifyConnectionEvents(targetUserId);
        deidentifySharedMessages(inventory);
    }

    private Set<String> findTargetOnlyMatchIds(String targetUserId) {
        if (!tableExists("soul_match") || !columnExists("soul_match", "id")
                || !columnExists("soul_match", "user_a_id") || !columnExists("soul_match", "user_b_id")) {
            return Set.of();
        }
        return jdbcTemplate.query("SELECT id FROM soul_match WHERE "
                        + "(user_a_id = ? AND (user_b_id IS NULL OR user_b_id = ?)) "
                        + "OR (user_b_id = ? AND (user_a_id IS NULL OR user_a_id = ?))",
                new Object[]{targetUserId, targetUserId, targetUserId, targetUserId},
                (org.springframework.jdbc.core.ResultSetExtractor<Set<String>>) rs -> {
                    Set<String> ids = new java.util.LinkedHashSet<>();
                    while (rs.next()) {
                        ids.add(String.valueOf(rs.getObject("id")));
                    }
                    return ids;
                });
    }

    private void deleteTargetOnlyMatchDependents(Set<String> matchIds) {
        if (matchIds.isEmpty()) {
            return;
        }
        Set<String> connectionIds = selectIdsByIds("soul_connection", "id", "match_id", matchIds);
        deleteByIds("soul_connection_event", "match_id", matchIds);
        deleteByIds("soul_connection_event", "connection_id", connectionIds);
        deleteByIds("soul_message", "match_id", matchIds);
        deleteByIds("soul_message", "connection_id", connectionIds);
        deleteByIds("match_feedback", "match_id", matchIds);
        deleteByIds("match_feedback", "connection_id", connectionIds);
        deleteByIds("soul_connection", "match_id", matchIds);
        deleteByIds("soul_match", "id", matchIds);
    }

    private Set<String> selectIdsByIds(String table, String selectedColumn,
            String whereColumn, Set<String> ids) {
        if (ids == null || ids.isEmpty() || !tableExists(table)
                || !columnExists(table, selectedColumn) || !columnExists(table, whereColumn)) {
            return Set.of();
        }
        return jdbcTemplate.query("SELECT " + selectedColumn + " FROM " + table
                        + " WHERE " + whereColumn + " IN (" + placeholders(ids.size()) + ")",
                ids.toArray(), (org.springframework.jdbc.core.ResultSetExtractor<Set<String>>) rs -> {
                    Set<String> selected = new java.util.LinkedHashSet<>();
                    while (rs.next()) {
                        selected.add(String.valueOf(rs.getObject(selectedColumn)));
                    }
                    return selected;
                });
    }

    private void deidentifySharedMatch(String targetUserId) {
        if (!tableExists("soul_match") || !columnExists("soul_match", "user_a_id")
                || !columnExists("soul_match", "user_b_id")) {
            return;
        }
        List<String> assignments = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        assignments.add("user_a_id = CASE WHEN user_a_id = ? THEN NULL ELSE user_a_id END");
        args.add(targetUserId);
        assignments.add("user_b_id = CASE WHEN user_b_id = ? THEN NULL ELSE user_b_id END");
        args.add(targetUserId);
        if (columnExists("soul_match", "status_a")) {
            assignments.add("status_a = CASE WHEN user_a_id = ? THEN NULL ELSE status_a END");
            args.add(targetUserId);
        }
        if (columnExists("soul_match", "status_b")) {
            assignments.add("status_b = CASE WHEN user_b_id = ? THEN NULL ELSE status_b END");
            args.add(targetUserId);
        }
        for (String column : List.of("letter_a_to_b", "letter_b_to_a", "reason",
                "timing_reason", "ice_breaker", "generation_run_id", "recommendation_event_id")) {
            if (columnExists("soul_match", column)) {
                assignments.add(column + " = NULL");
            }
        }
        if (columnExists("soul_match", "is_matched")) {
            assignments.add("is_matched = FALSE");
        }
        args.add(targetUserId);
        args.add(targetUserId);
        jdbcTemplate.update("UPDATE soul_match SET " + String.join(", ", assignments)
                + " WHERE user_a_id = ? OR user_b_id = ?", args.toArray());
    }

    private void deidentifySharedConnection(String targetUserId) {
        if (!tableExists("soul_connection") || !columnExists("soul_connection", "user_a_id")
                || !columnExists("soul_connection", "user_b_id")) {
            return;
        }
        List<String> assignments = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        assignments.add("user_a_id = CASE WHEN user_a_id = ? THEN ? ELSE user_a_id END");
        args.add(targetUserId);
        args.add(DEIDENTIFIED_USER_REF);
        assignments.add("user_b_id = CASE WHEN user_b_id = ? THEN ? ELSE user_b_id END");
        args.add(targetUserId);
        args.add(DEIDENTIFIED_USER_REF);
        if (columnExists("soul_connection", "last_action_by")) {
            assignments.add("last_action_by = CASE WHEN last_action_by = ? THEN NULL ELSE last_action_by END");
            args.add(targetUserId);
        }
        if (columnExists("soul_connection", "status")) {
            assignments.add("status = 'BLOCKED'");
        }
        if (columnExists("soul_connection", "last_action")) {
            assignments.add("last_action = 'ACCOUNT_DELETION'");
        }
        if (columnExists("soul_connection", "reason_category")) {
            assignments.add("reason_category = 'ACCOUNT_DELETION'");
        }
        args.add(targetUserId);
        args.add(targetUserId);
        jdbcTemplate.update("UPDATE soul_connection SET " + String.join(", ", assignments)
                + " WHERE user_a_id = ? OR user_b_id = ?", args.toArray());
    }

    private void deidentifyConnectionEvents(String targetUserId) {
        if (tableExists("soul_connection_event") && columnExists("soul_connection_event", "actor_user_id")) {
            jdbcTemplate.update("UPDATE soul_connection_event SET actor_user_id = NULL WHERE actor_user_id = ?",
                    targetUserId);
        }
    }

    private void deidentifySharedMessages(AccountDeletionInventory inventory) {
        if (!tableExists("soul_message")) {
            return;
        }
        List<String> predicates = new ArrayList<>();
        List<Object> whereArgs = new ArrayList<>();
        if (columnExists("soul_message", "sender_id")) {
            predicates.add("sender_id = ?");
            whereArgs.add(inventory.targetUserId());
        }
        if (columnExists("soul_message", "receiver_id")) {
            predicates.add("receiver_id = ?");
            whereArgs.add(inventory.targetUserId());
        }
        if (columnExists("soul_message", "match_id") && !inventory.soulMatchIds().isEmpty()) {
            predicates.add("match_id IN (" + placeholders(inventory.soulMatchIds().size()) + ")");
            whereArgs.addAll(inventory.soulMatchIds().stream().map(Long::valueOf).toList());
        }
        if (columnExists("soul_message", "connection_id") && !inventory.soulConnectionIds().isEmpty()) {
            predicates.add("connection_id IN (" + placeholders(inventory.soulConnectionIds().size()) + ")");
            whereArgs.addAll(inventory.soulConnectionIds().stream().map(Long::valueOf).toList());
        }
        if (predicates.isEmpty()) {
            return;
        }

        List<String> assignments = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (columnExists("soul_message", "sender_id")) {
            assignments.add("sender_id = CASE WHEN sender_id = ? THEN NULL ELSE sender_id END");
            args.add(inventory.targetUserId());
        }
        if (columnExists("soul_message", "receiver_id")) {
            assignments.add("receiver_id = CASE WHEN receiver_id = ? THEN NULL ELSE receiver_id END");
            args.add(inventory.targetUserId());
        }
        for (String column : List.of("content", "run_id", "source_event_id")) {
            if (columnExists("soul_message", column)) {
                assignments.add(column + " = NULL");
            }
        }
        args.addAll(whereArgs);
        jdbcTemplate.update("UPDATE soul_message SET " + String.join(", ", assignments)
                + " WHERE " + String.join(" OR ", predicates), args.toArray());
    }

    private boolean isReferencedByAnotherUser(String objectKey, String targetUserId) {
        if (!tableExists("image_file") || !columnExists("image_file", "object_key")
                || !columnExists("image_file", "user_id")) {
            return false;
        }
        Long references = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM image_file WHERE object_key = ? AND user_id <> ?",
                Long.class, objectKey, targetUserId);
        return references != null && references > 0;
    }

    private String placeholders(int count) {
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
    }

    private void deleteProductEventDependents(AccountDeletionInventory inventory) {
        String targetUserId = inventory.targetUserId();
        deleteByIds("product_event_scope", "event_id", inventory.productEventIds());
        deleteByIds("task_execution", "trigger_event_id", inventory.productEventIds());
        if (!tableExists("product_event") || !columnExists("product_event", "event_id")) {
            return;
        }
        List<String> ownerColumns = List.of("user_id", "actor_user_id").stream()
                .filter(column -> columnExists("product_event", column)).toList();
        if (ownerColumns.isEmpty()) {
            return;
        }
        String predicate = ownerColumns.stream().map(column -> column + " = ?")
                .collect(java.util.stream.Collectors.joining(" OR "));
        Object[] ownerArgs = java.util.stream.Stream.generate(() -> (Object) targetUserId)
                .limit(ownerColumns.size()).toArray();
        if (tableExists("product_event_scope") && columnExists("product_event_scope", "event_id")) {
            jdbcTemplate.update("DELETE FROM product_event_scope WHERE event_id IN "
                            + "(SELECT event_id FROM product_event WHERE " + predicate + ")", ownerArgs);
        }
        if (tableExists("task_execution") && columnExists("task_execution", "trigger_event_id")) {
            jdbcTemplate.update("DELETE FROM task_execution WHERE trigger_event_id IN "
                            + "(SELECT event_id FROM product_event WHERE " + predicate + ")", ownerArgs);
        }
    }

    private void deleteByIds(String table, String column, Set<String> ids) {
        if (ids == null || ids.isEmpty() || !tableExists(table) || !columnExists(table, column)) {
            return;
        }
        String placeholders = String.join(", ", java.util.Collections.nCopies(ids.size(), "?"));
        jdbcTemplate.update("DELETE FROM " + table + " WHERE " + column + " IN (" + placeholders + ")",
                ids.toArray());
    }

    private void cleanSituationRooms(String targetUserId) {
        if (!tableExists("situation_room") || !columnExists("situation_room", "code")) {
            return;
        }
        List<String> columns = new ArrayList<>();
        for (String column : List.of("code", "owner_id", "members", "submissions",
                "submission_visibility", "cancel_votes", "report")) {
            if (columnExists("situation_room", column)) {
                columns.add(column);
            }
        }
        if (!columns.contains("members")) {
            return;
        }
        String sql = "SELECT " + String.join(", ", columns)
                + " FROM situation_room WHERE members LIKE ?"
                + (columns.contains("owner_id") ? " OR owner_id = ?" : "");
        Object[] args = columns.contains("owner_id")
                ? new Object[]{"%\"" + targetUserId + "\"%", targetUserId}
                : new Object[]{"%\"" + targetUserId + "\"%"};
        jdbcTemplate.query(sql, args, (org.springframework.jdbc.core.ResultSetExtractor<Void>) rs -> {
            while (rs.next()) {
                String roomCode = rs.getString("code");
                boolean owned = columns.contains("owner_id") && targetUserId.equals(rs.getString("owner_id"));
                if (owned) {
                    if (tableExists("room_message")) {
                        jdbcTemplate.update("DELETE FROM room_message WHERE room_code = ?", roomCode);
                    }
                    jdbcTemplate.update("DELETE FROM situation_room WHERE code = ?", roomCode);
                    continue;
                }

                Map<String, String> updates = new java.util.LinkedHashMap<>();
                updates.put("members", removeJsonArrayValue(rs.getString("members"), targetUserId));
                if (columns.contains("submissions")) {
                    updates.put("submissions", removeJsonObjectValue(rs.getString("submissions"), targetUserId));
                }
                if (columns.contains("submission_visibility")) {
                    updates.put("submission_visibility",
                            removeJsonObjectValue(rs.getString("submission_visibility"), targetUserId));
                }
                if (columns.contains("cancel_votes")) {
                    updates.put("cancel_votes", removeJsonArrayValue(rs.getString("cancel_votes"), targetUserId));
                }
                if (columns.contains("report")) {
                    updates.put("report", removeReportValue(rs.getString("report"), targetUserId));
                }
                List<String> assignments = updates.keySet().stream()
                        .map(column -> column + " = ?").toList();
                jdbcTemplate.update("UPDATE situation_room SET " + String.join(", ", assignments)
                        + " WHERE code = ?", append(updates.values().toArray(), roomCode));
            }
            return null;
        });
    }

    private String removeJsonArrayValue(String json, String targetUserId) {
        if (json == null || json.isBlank()) {
            return json;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root.isArray()) {
                java.util.Iterator<JsonNode> iterator = root.iterator();
                while (iterator.hasNext()) {
                    if (targetUserId.equals(iterator.next().asText())) {
                        iterator.remove();
                    }
                }
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception exception) {
            throw new AccountDeletionFailure("shared_room_payload_unreadable");
        }
    }

    private String removeJsonObjectValue(String json, String targetUserId) {
        if (json == null || json.isBlank()) {
            return json;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root.isObject()) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) root).remove(targetUserId);
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception exception) {
            throw new AccountDeletionFailure("shared_room_payload_unreadable");
        }
    }

    private String removeReportValue(String json, String targetUserId) {
        if (json == null || json.isBlank()) {
            return json;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root.isObject()) {
                com.fasterxml.jackson.databind.node.ObjectNode report =
                        (com.fasterxml.jackson.databind.node.ObjectNode) root;
                removeArrayObjects(report, "personal", "userId", targetUserId);
                removeArrayObjects(report, "publicSubmissions", "userId", targetUserId);
                removePairObjects(report, "pairs", targetUserId);
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception exception) {
            throw new AccountDeletionFailure("shared_room_payload_unreadable");
        }
    }

    private void removeArrayObjects(com.fasterxml.jackson.databind.node.ObjectNode root,
            String field, String key, String targetUserId) {
        JsonNode array = root.get(field);
        if (array == null || !array.isArray()) {
            return;
        }
        java.util.Iterator<JsonNode> iterator = array.iterator();
        while (iterator.hasNext()) {
            JsonNode item = iterator.next();
            if (targetUserId.equals(item.path(key).asText())) {
                iterator.remove();
            }
        }
    }

    private void removePairObjects(com.fasterxml.jackson.databind.node.ObjectNode root,
            String field, String targetUserId) {
        JsonNode array = root.get(field);
        if (array == null || !array.isArray()) {
            return;
        }
        java.util.Iterator<JsonNode> iterator = array.iterator();
        while (iterator.hasNext()) {
            JsonNode item = iterator.next();
            if (targetUserId.equals(item.path("userA").asText())
                    || targetUserId.equals(item.path("userB").asText())) {
                iterator.remove();
            }
        }
    }

    private Object[] append(Object[] values, Object last) {
        Object[] result = java.util.Arrays.copyOf(values, values.length + 1);
        result[values.length] = last;
        return result;
    }

    private String adaptStatement(DeleteStatement statement) {
        if (statement.table().equals("situation_scenario")
                && !columnExists("situation_scenario", "submitter_id")
                && columnExists("situation_scenario", "user_id")) {
            return "DELETE FROM situation_scenario WHERE user_id = ?";
        }
        if (statement.table().equals("security_audit_event")) {
            String sql = statement.sql();
            if (columnExists("security_audit_event", "resource_id")) {
                sql = sql.replace("subject_user_id = NULL", "subject_user_id = NULL, resource_id = NULL")
                        .replace("OR subject_user_id = ?", "OR subject_user_id = ? OR resource_id = ?");
                return sql;
            }
        }
        return statement.sql();
    }

    private void recordDeidentifiedSuccess(String requestId, String adminUserId) {
        if (securityAuditService == null) {
            return;
        }
        securityAuditService.recordAdmin(SecurityAuditAction.ADMIN_USER_DEREGISTERED,
                adminUserId, null, SecurityAuditResourceType.USER, null,
                SecurityAuditOutcome.SUCCESS, SecurityAuditReasonCode.ADMIN_MUTATION,
                Map.of(SecurityAuditDetailKeys.OPERATION, SecurityAuditOperation.DEREGISTER.name(),
                        SecurityAuditDetailKeys.REASON_CATEGORY, "ACCOUNT_DELETION_COMPLETED",
                        "requestId", requestId));
    }

    private boolean tableExists(String table) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_NAME) = UPPER(?)",
                Long.class, table);
        return count != null && count > 0;
    }

    private boolean columnExists(String table, String column) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE UPPER(TABLE_NAME) = UPPER(?) AND UPPER(COLUMN_NAME) = UPPER(?)",
                Long.class, table, column);
        return count != null && count > 0;
    }

    private String newRequestId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private enum ReferenceKind {
        IMAGE,
        ATTACHMENT
    }

    private record DeleteStatement(String table, String sql, boolean twoParameters) {
    }
}
