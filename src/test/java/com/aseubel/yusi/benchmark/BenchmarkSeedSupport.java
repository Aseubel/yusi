package com.aseubel.yusi.benchmark;

import com.aseubel.yusi.pojo.constant.KeyMode;
import com.aseubel.yusi.pojo.entity.MidTermMemory;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.repository.MidTermMemoryRepository;
import com.aseubel.yusi.repository.UserRepository;
import com.aseubel.yusi.service.memory.MidTermMemoryVectorService;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * bench 用户与预置记忆的共享 seed 工具（Layer A 各 runner 复用）。
 * 全部使用 bench- 前缀用户 ID 与合成文本，跑完由 BenchmarkDataGuard 统一清理。
 */
public final class BenchmarkSeedSupport {

    private BenchmarkSeedSupport() {
    }

    /**
     * bench 用户名：带层标签与 runId 后缀。tagPrefix（如 ext-lin / ret-lin）保证同一 run 内
     * 不同 layer 各自建用户不撞 uk_user_username；runId 保证跨 run 不撞上次残留。
     */
    public static String benchUserName(String tagPrefix, String displayName) {
        return "bench-" + tagPrefix + "-" + displayName + "-" + BenchmarkEnv.runId();
    }

    /** 创建一个 DEFAULT 密钥模式的 bench 用户并返回其 userId。 */
    public static String createBenchUser(UserRepository userRepository, String tagPrefix,
            String displayName) {
        User user = new User();
        user.setUserId(BenchmarkEnv.userId(tagPrefix));
        user.setUserName(benchUserName(tagPrefix, displayName));
        user.setPassword("bench-only-no-login-" + BenchmarkEnv.runId());
        user.setEmail("bench-" + tagPrefix + "-" + BenchmarkEnv.runId() + "@benchmark.invalid");
        user.setKeyMode(KeyMode.DEFAULT.code());
        userRepository.save(user);
        return user.getUserId();
    }

    /**
     * 预置中期记忆（真实 MySQL 行 + 真实 embedding 向量入库）。
     * id 由数据库自增分配——实体是 IDENTITY 主键，手动 setId 会让 save 走 UPDATE 引发乐观锁异常。
     *
     * @param seeds 记忆种子列表：文本、是否隐藏
     * @return id -> 已持久化的记忆实体（含向量）
     */
    public static Map<Long, MidTermMemory> seedMidTermMemories(MidTermMemoryRepository repository,
            MidTermMemoryVectorService vectorService, String userId,
            List<SeedMemory> seeds) {
        Map<Long, MidTermMemory> saved = new HashMap<>();
        for (SeedMemory seed : seeds) {
            MidTermMemory memory = new MidTermMemory();
            memory.setUserId(userId);
            memory.setSummary(seed.text());
            memory.setImportance(4d);
            memory.setConfidence(0.9);
            memory.setHidden(seed.hidden());
            memory.setMatchAllowed(true);
            memory.setCreatedAt(LocalDateTime.now());
            memory.setUpdatedAt(LocalDateTime.now());
            MidTermMemory persisted = repository.saveAndFlush(memory);
            vectorService.upsert(persisted);
            saved.put(persisted.getId(), persisted);
        }
        return saved;
    }

    public record SeedMemory(String text, boolean hidden) {
        public static SeedMemory visible(String text) {
            return new SeedMemory(text, false);
        }

        public static SeedMemory hidden(String text) {
            return new SeedMemory(text, true);
        }
    }
}
