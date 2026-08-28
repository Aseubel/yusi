package com.aseubel.yusi.benchmark;

import com.aseubel.yusi.config.ai.properties.MilvusCollectionProperties;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 收尾清理守卫：删除所有 bench- 前缀用户的 DB 行，drop yusi_benchmark_* 集合。
 * 幂等设计：表通过 information_schema 动态发现（只处理真实存在 user_id 列的表），
 * 外键约束下按轮次重试；任何失败记 CLEANUP_ERROR 且 clean=false——清理失败必须进记分卡，不允许静默。
 */
@Slf4j
public class BenchmarkDataGuard {

    private static final String FIND_TABLES_SQL = """
            SELECT c.table_name FROM information_schema.columns c
            WHERE c.table_schema = DATABASE() AND c.column_name = 'user_id'
              AND EXISTS (SELECT 1 FROM information_schema.tables t
                          WHERE t.table_schema = c.table_schema AND t.table_name = c.table_name)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final MilvusClientV2 milvusClientV2;
    private final MilvusCollectionProperties collectionProperties;
    private final BenchmarkFailureRecorder failureRecorder;

    public record CleanupResult(int dbRowsDeleted, List<String> tablesDeletedFrom,
            List<String> droppedCollections, boolean clean, List<String> errors) {
    }

    public BenchmarkDataGuard(JdbcTemplate jdbcTemplate, MilvusClientV2 milvusClientV2,
            MilvusCollectionProperties collectionProperties,
            BenchmarkFailureRecorder failureRecorder) {
        this.jdbcTemplate = jdbcTemplate;
        this.milvusClientV2 = milvusClientV2;
        this.collectionProperties = collectionProperties;
        this.failureRecorder = failureRecorder;
    }

    public CleanupResult cleanup() {
        List<String> errors = new ArrayList<>();
        int rowsDeleted = 0;
        Set<String> tablesTouched = new LinkedHashSet<>();

        // 外键依赖下删除顺序不定：最多三轮重试未被删除的表，最终仍失败才记 CLEANUP_ERROR
        Set<String> pendingTables = discoverTablesWithUserIdColumn();
        for (int round = 0; round < 3 && !pendingTables.isEmpty(); round++) {
            Set<String> remaining = new LinkedHashSet<>();
            for (String table : pendingTables) {
                try {
                    int deleted = jdbcTemplate.update(
                            "DELETE FROM `" + table + "` WHERE `user_id` LIKE 'bench-%'");
                    if (deleted > 0) {
                        rowsDeleted += deleted;
                        tablesTouched.add(table);
                        log.info("benchmark cleanup removed {} rows from {}", deleted, table);
                    }
                } catch (Exception e) {
                    remaining.add(table);
                    if (round == 2) {
                        errors.add("db:" + table);
                        failureRecorder.record("cleanup:" + table,
                                BenchmarkFailureRecorder.TYPE_CLEANUP_ERROR,
                                e.getClass().getSimpleName());
                    }
                }
            }
            pendingTables = remaining;
        }

        List<String> dropped = dropBenchmarkCollections(errors);

        return new CleanupResult(rowsDeleted, List.copyOf(tablesTouched),
                dropped, errors.isEmpty(), List.copyOf(errors));
    }

    /** 动态发现本库含 user_id 列的表，避免硬编码表名漂移导致漏清或误删不存在的表。 */
    private Set<String> discoverTablesWithUserIdColumn() {
        return new LinkedHashSet<>(jdbcTemplate.queryForList(FIND_TABLES_SQL, String.class));
    }

    private List<String> dropBenchmarkCollections(List<String> errors) {
        List<String> dropped = new ArrayList<>();
        List.of(collectionProperties.getEmbedding(), collectionProperties.getMidTermMemory(),
                collectionProperties.getMatchProfile()).stream()
                .filter(name -> name != null && !name.isBlank())
                .forEach(name -> {
                    try {
                        Boolean exists = milvusClientV2.hasCollection(
                                HasCollectionReq.builder().collectionName(name).build());
                        if (Boolean.TRUE.equals(exists)) {
                            milvusClientV2.dropCollection(
                                    DropCollectionReq.builder().collectionName(name).build());
                            dropped.add(name);
                            log.info("benchmark cleanup dropped collection {}", name);
                        }
                    } catch (Exception e) {
                        errors.add("milvus:" + name);
                        failureRecorder.record("cleanup:milvus:" + name,
                                BenchmarkFailureRecorder.TYPE_CLEANUP_ERROR,
                                e.getClass().getSimpleName());
                    }
                });
        return dropped;
    }
}
