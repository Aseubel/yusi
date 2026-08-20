package com.aseubel.yusi.service.privacy;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Application-level residual and orphan checks for schemas without FK enforcement. */
public final class AccountDeletionInvariantValidator {

    private final JdbcTemplate jdbcTemplate;

    public AccountDeletionInvariantValidator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Long> residualCounts(String targetUserId) {
        Map<String, Long> residuals = new LinkedHashMap<>();
        for (TableColumn table : targetTables()) {
            String column = resolveColumn(table.table(), table.column(), table.alternativeColumn());
            if (column != null && tableExists(table.table())) {
                residuals.put(table.table(), count("SELECT COUNT(*) FROM " + table.table()
                        + " WHERE " + column + " = ?", targetUserId));
            }
        }
        addResidualIfColumns(residuals, "room_message", targetUserId,
                "sender_id");
        addResidualIfColumns(residuals, "resonance_signal", targetUserId,
                "from_user_id", "to_user_id");
        addResidualIfColumns(residuals, "soul_match", targetUserId,
                "user_a_id", "user_b_id");
        addResidualIfColumns(residuals, "soul_message", targetUserId,
                "sender_id", "receiver_id");
        addResidualIfColumns(residuals, "soul_connection", targetUserId,
                "user_a_id", "user_b_id", "last_action_by");
        addResidualIfColumns(residuals, "soul_connection_event", targetUserId,
                "actor_user_id");
        addResidualIfColumns(residuals, "product_event", targetUserId,
                "user_id", "actor_user_id");
        addResidualIfColumns(residuals, "security_audit_event", targetUserId,
                "actor_user_id", "subject_user_id", "resource_id");
        return residuals;
    }

    public Map<String, Long> orphanCounts(String targetUserId) {
        Map<String, Long> orphans = new LinkedHashMap<>();
        if (tableExists("life_graph_relation") && tableExists("life_graph_entity")
                && columnExists("life_graph_relation", "source_id")
                && columnExists("life_graph_relation", "target_id")) {
            orphans.put("life_graph_relation_endpoint", count(
                    "SELECT COUNT(*) FROM life_graph_relation r "
                            + "LEFT JOIN life_graph_entity s ON s.id = r.source_id "
                            + "LEFT JOIN life_graph_entity t ON t.id = r.target_id "
                            + "WHERE s.id IS NULL OR t.id IS NULL"));
        }
        if (tableExists("life_graph_entity_evidence") && tableExists("life_graph_entity")
                && columnExists("life_graph_entity_evidence", "entity_id")) {
            orphans.put("life_graph_entity_evidence_entity", count(
                    "SELECT COUNT(*) FROM life_graph_entity_evidence e "
                            + "LEFT JOIN life_graph_entity n ON n.id = e.entity_id "
                            + "WHERE n.id IS NULL"));
        }
        if (tableExists("life_graph_relation_evidence") && tableExists("life_graph_relation")
                && columnExists("life_graph_relation_evidence", "relation_id")) {
            orphans.put("life_graph_relation_evidence_relation", count(
                    "SELECT COUNT(*) FROM life_graph_relation_evidence e "
                            + "LEFT JOIN life_graph_relation r ON r.id = e.relation_id "
                            + "WHERE r.id IS NULL"));
        }
        if (tableExists("life_graph_entity_alias") && tableExists("life_graph_entity")
                && columnExists("life_graph_entity_alias", "entity_id")) {
            orphans.put("life_graph_entity_alias_entity", count(
                    "SELECT COUNT(*) FROM life_graph_entity_alias a "
                            + "LEFT JOIN life_graph_entity n ON n.id = a.entity_id "
                            + "WHERE n.id IS NULL"));
        }
        if (tableExists("life_graph_mention") && tableExists("life_graph_entity")
                && columnExists("life_graph_mention", "entity_id")) {
            orphans.put("life_graph_mention_entity", count(
                    "SELECT COUNT(*) FROM life_graph_mention m "
                            + "LEFT JOIN life_graph_entity n ON n.id = m.entity_id "
                            + "WHERE n.id IS NULL"));
        }
        if (tableExists("product_event_scope") && tableExists("product_event")
                && columnExists("product_event_scope", "event_id")) {
            orphans.put("product_event_scope_event", count(
                    "SELECT COUNT(*) FROM product_event_scope s "
                            + "LEFT JOIN product_event e ON e.event_id = s.event_id "
                            + "WHERE s.user_id = ? AND e.event_id IS NULL", targetUserId));
        }
        if (tableExists("soul_connection") && tableExists("soul_match")
                && columnExists("soul_connection", "match_id")
                && columnExists("soul_match", "id")) {
            orphans.put("soul_connection_match", count(
                    "SELECT COUNT(*) FROM soul_connection c "
                            + "LEFT JOIN soul_match m ON m.id = c.match_id "
                            + "WHERE m.id IS NULL"));
        }
        if (tableExists("soul_connection_event") && tableExists("soul_connection")
                && columnExists("soul_connection_event", "connection_id")
                && columnExists("soul_connection", "id")) {
            orphans.put("soul_connection_event_connection", count(
                    "SELECT COUNT(*) FROM soul_connection_event e "
                            + "LEFT JOIN soul_connection c ON c.id = e.connection_id "
                            + "WHERE c.id IS NULL"));
        }
        if (tableExists("soul_connection_event") && tableExists("soul_match")
                && columnExists("soul_connection_event", "match_id")
                && columnExists("soul_match", "id")) {
            orphans.put("soul_connection_event_match", count(
                    "SELECT COUNT(*) FROM soul_connection_event e "
                            + "LEFT JOIN soul_match m ON m.id = e.match_id "
                            + "WHERE m.id IS NULL"));
        }
        if (tableExists("match_feedback") && tableExists("soul_match")
                && columnExists("match_feedback", "match_id")
                && columnExists("soul_match", "id")) {
            orphans.put("match_feedback_match", count(
                    "SELECT COUNT(*) FROM match_feedback f "
                            + "LEFT JOIN soul_match m ON m.id = f.match_id "
                            + "WHERE m.id IS NULL"));
        }
        if (tableExists("soul_message") && tableExists("soul_match")
                && columnExists("soul_message", "match_id")
                && columnExists("soul_match", "id")) {
            orphans.put("soul_message_match", count(
                    "SELECT COUNT(*) FROM soul_message s "
                            + "LEFT JOIN soul_match m ON m.id = s.match_id "
                            + "WHERE m.id IS NULL"));
        }
        if (tableExists("chat_memory_message") && tableExists("product_event")
                && columnExists("chat_memory_message", "source_event_id")
                && columnExists("product_event", "event_id")) {
            orphans.put("chat_memory_source_event", count(
                    "SELECT COUNT(*) FROM chat_memory_message c "
                            + "LEFT JOIN product_event p ON p.event_id = c.source_event_id "
                            + "WHERE c.source_event_id IS NOT NULL AND p.event_id IS NULL"));
        }
        if (tableExists("task_execution") && tableExists("product_event")
                && columnExists("task_execution", "trigger_event_id")
                && columnExists("product_event", "event_id")) {
            orphans.put("task_execution_trigger_event", count(
                    "SELECT COUNT(*) FROM task_execution t "
                            + "LEFT JOIN product_event p ON p.event_id = t.trigger_event_id "
                            + "WHERE p.event_id IS NULL"));
        }
        if (tableExists("security_audit_event_scope") && tableExists("security_audit_event")
                && columnExists("security_audit_event_scope", "audit_event_id")) {
            orphans.put("security_audit_scope_event", count(
                    "SELECT COUNT(*) FROM security_audit_event_scope s "
                            + "LEFT JOIN security_audit_event e ON e.id = s.audit_event_id "
                            + "WHERE s.user_id = ? AND e.id IS NULL", targetUserId));
        }
        return orphans;
    }

    public void requireClean(String targetUserId) {
        Map<String, Long> residuals = residualCounts(targetUserId);
        Map<String, Long> orphans = orphanCounts(targetUserId);
        if (residuals.values().stream().anyMatch(value -> value != null && value > 0)
                || orphans.values().stream().anyMatch(value -> value != null && value > 0)) {
            throw new AccountDeletionFailure("database_invariant");
        }
    }

    private List<TableColumn> targetTables() {
        return List.of(
                new TableColumn("user", "user_id", null),
                new TableColumn("user_location", "user_id", null),
                new TableColumn("user_notification", "user_id", null),
                new TableColumn("user_persona", "user_id", null),
                new TableColumn("agent_persona_config", "user_id", null),
                new TableColumn("cognitive_conflict", "user_id", null),
                new TableColumn("developer_config", "user_id", null),
                new TableColumn("diary", "user_id", null),
                new TableColumn("chat_memory_message", "memory_id", null),
                new TableColumn("embedding_task", "user_id", null),
                new TableColumn("image_file", "user_id", null),
                new TableColumn("interface_daily_usage", "user_id", null),
                new TableColumn("life_graph_entity", "user_id", null),
                new TableColumn("life_graph_entity_alias", "user_id", null),
                new TableColumn("life_graph_entity_evidence", "user_id", null),
                new TableColumn("life_graph_mention", "user_id", null),
                new TableColumn("life_graph_merge_judgment", "user_id", null),
                new TableColumn("life_graph_relation_evidence", "user_id", null),
                new TableColumn("life_graph_relation", "user_id", null),
                new TableColumn("life_graph_task", "user_id", null),
                new TableColumn("match_feedback", "user_id", null),
                new TableColumn("match_profile", "user_id", null),
                new TableColumn("mid_term_memory", "user_id", null),
                new TableColumn("soul_card", "user_id", null),
                new TableColumn("soul_report", "user_id", null),
                new TableColumn("soul_resonance", "user_id", null),
                new TableColumn("situation_scenario", "submitter_id", "user_id"),
                new TableColumn("product_event_scope", "user_id", null),
                new TableColumn("agent_run_trace", "user_id", null),
                new TableColumn("agent_tool_trace", "user_id", null),
                new TableColumn("model_call_trace", "user_id", null),
                new TableColumn("task_execution", "owner_user_id", null));
    }

    private String resolveColumn(String table, String preferred, String alternative) {
        if (columnExists(table, preferred)) {
            return preferred;
        }
        return alternative != null && columnExists(table, alternative) ? alternative : null;
    }

    private boolean tableExists(String table) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                        + "WHERE UPPER(TABLE_NAME) = UPPER(?)", Long.class, table);
        return count != null && count > 0;
    }

    private boolean columnExists(String table, String column) {
        if (!tableExists(table)) {
            return false;
        }
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE UPPER(TABLE_NAME) = UPPER(?) AND UPPER(COLUMN_NAME) = UPPER(?)",
                Long.class, table, column);
        return count != null && count > 0;
    }

    private long count(String sql, Object... args) {
        Long result = jdbcTemplate.queryForObject(sql, Long.class, args);
        return result == null ? 0L : result;
    }

    private void addResidualIfColumns(Map<String, Long> residuals, String table,
            String targetUserId, String... columns) {
        if (!tableExists(table)) {
            return;
        }
        List<String> present = java.util.Arrays.stream(columns)
                .filter(column -> columnExists(table, column))
                .toList();
        if (present.isEmpty()) {
            return;
        }
        String predicate = present.stream().map(column -> column + " = ?")
                .collect(java.util.stream.Collectors.joining(" OR "));
        Object[] args = java.util.stream.Stream.generate(() -> (Object) targetUserId)
                .limit(present.size()).toArray();
        residuals.put(table, count("SELECT COUNT(*) FROM " + table + " WHERE " + predicate, args));
    }

    private record TableColumn(String table, String column, String alternativeColumn) {
    }
}
