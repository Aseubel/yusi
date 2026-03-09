package com.aseubel.yusi.monitor;

import com.aseubel.yusi.pojo.entity.InterfaceDailyUsage;
import com.aseubel.yusi.redis.service.IRedisService;
import com.aseubel.yusi.repository.InterfaceDailyUsageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static com.aseubel.yusi.redis.common.RedisKey.USAGE_PREFIX;
import static com.aseubel.yusi.redis.common.RedisKey.SEPARATOR;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterfaceUsageMonitor {

    private final IRedisService redissonService;
    private final InterfaceDailyUsageRepository repository;
    private final ThreadPoolTaskExecutor threadPoolExecutor;

    // 批量写入缓冲区
    private final ConcurrentHashMap<String, AtomicLong> usageBuffer = new ConcurrentHashMap<>();
    
    // 批量写入阈值
    private static final int BATCH_SIZE_THRESHOLD = 100;
    
    // 缓冲区计数器
    private final AtomicLong bufferCount = new AtomicLong(0);
    
    // field 内部分隔符，使用不会在数据中出现的字符
    private static final String FIELD_SEPARATOR = "\u0001";

    /**
     * 记录接口使用情况到 Redis（带批量缓冲）
     */
    public void recordUsage(String userId, String ip, String interfaceName) {
        try {
            if (userId == null)
                userId = "anonymous";
            if (ip == null)
                ip = "unknown";

            String dateStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            String redisKey = USAGE_PREFIX + dateStr;
            String field = userId + FIELD_SEPARATOR + ip + FIELD_SEPARATOR + interfaceName;

            // 先写入本地缓冲区
            AtomicLong count = usageBuffer.computeIfAbsent(redisKey + ":" + field, k -> new AtomicLong(0));
            long currentCount = count.incrementAndGet();
            bufferCount.incrementAndGet();

            // 使用 Redisson 的 addAndGet 进行原子递增
            RMap<String, Object> map = redissonService.getMap(redisKey);
            map.addAndGet(field, 1L);

            // 当缓冲区达到阈值时，批量同步到数据库
            if (bufferCount.get() >= BATCH_SIZE_THRESHOLD) {
                syncBufferToDatabaseAsync();
            }

        } catch (Exception e) {
            log.error("Failed to record interface usage", e);
        }
    }

    /**
     * 每 30 分钟同步一次数据到数据库
     */
    @Scheduled(cron = "0 0/30 * * * ?")
    public void syncToDatabase() {
        log.debug("Starting interface usage sync...");
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        syncDate(yesterday);
        syncDate(today);

        // 同步缓冲区数据
        syncBufferToDatabase();

        log.debug("Interface usage sync completed.");
    }

    /**
     * 异步批量同步缓冲区数据到数据库
     */
    private void syncBufferToDatabaseAsync() {
        // 使用独立线程异步处理，避免阻塞主线程
        threadPoolExecutor.execute(() -> {
            try {
                syncBufferToDatabase();
            } catch (Exception e) {
                log.error("异步批量同步失败", e);
            }
        });
    }

    /**
     * 批量同步缓冲区数据到数据库
     */
    private synchronized void syncBufferToDatabase() {
        if (bufferCount.get() == 0) {
            return;
        }

        log.debug("开始批量同步缓冲区数据，当前缓冲记录数：{}", bufferCount.get());

        try {
            // 按日期分组批量处理
            Map<String, List<UsageRecord>> groupedByDate = new ConcurrentHashMap<>();
            
            for (Map.Entry<String, AtomicLong> entry : usageBuffer.entrySet()) {
                String key = entry.getKey();
                long count = entry.getValue().get();
                
                if (count == 0) {
                    continue;
                }

                // 解析 key: redisKey:field
                int lastColonIndex = key.lastIndexOf(':');
                if (lastColonIndex == -1) {
                    continue;
                }

                String redisKey = key.substring(0, lastColonIndex);
                String field = key.substring(lastColonIndex + 1);

                // 提取日期
                String dateStr = redisKey.replace(USAGE_PREFIX, "");
                
                // 兼容新旧分隔符格式
                String[] parts = field.contains(FIELD_SEPARATOR) 
                    ? field.split(FIELD_SEPARATOR) 
                    : field.split(SEPARATOR);
                if (parts.length != 3) {
                    log.warn("Invalid usage field format: {}", field);
                    continue;
                }

                String userId = parts[0];
                String ip = parts[1];
                String interfaceName = parts[2];

                UsageRecord record = new UsageRecord(userId, ip, interfaceName, count);
                groupedByDate.computeIfAbsent(dateStr, k -> new ArrayList<>()).add(record);
            }

            // 批量写入数据库
            for (Map.Entry<String, List<UsageRecord>> entry : groupedByDate.entrySet()) {
                String dateStr = entry.getKey();
                List<UsageRecord> records = entry.getValue();
                
                try {
                    LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
                    
                    // 转换为实体对象
                    List<InterfaceDailyUsage> entities = records.stream()
                        .map(record -> InterfaceDailyUsage.builder()
                            .userId(record.userId)
                            .ip(record.ip)
                            .interfaceName(record.interfaceName)
                            .usageDate(date)
                            .requestCount(record.count)
                            .build())
                        .toList();
                    
                    // 检查是否有有效记录，避免空列表导致 SQL 错误
                    if (entities.isEmpty()) {
                        log.debug("日期 {} 没有有效记录需要写入数据库", dateStr);
                        continue;
                    }
                    
                    // 使用真正的批量 SQL 操作
                    repository.batchUpsertUsage(entities);
                    
                    log.debug("批量同步日期 {} 的 {} 条记录", dateStr, records.size());
                    
                } catch (Exception e) {
                    log.error("批量同步日期 {} 失败", dateStr, e);
                }
            }

            // 清空缓冲区
            usageBuffer.clear();
            bufferCount.set(0);
            
        } catch (Exception e) {
            log.error("批量同步缓冲区数据失败", e);
        }
    }

    /**
     * 内部记录类，用于批量处理
     */
    private static class UsageRecord {
        String userId;
        String ip;
        String interfaceName;
        long count;

        UsageRecord(String userId, String ip, String interfaceName, long count) {
            this.userId = userId;
            this.ip = ip;
            this.interfaceName = interfaceName;
            this.count = count;
        }
    }

    private void syncDate(LocalDate date) {
        String dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String redisKey = USAGE_PREFIX + dateStr;

        try {
            if (!redissonService.isExists(redisKey)) {
                return;
            }

            RMap<String, Object> map = redissonService.getMap(redisKey);

            // 设置过期时间，确保清理（例如 2 天）
            map.expire(java.time.Duration.ofDays(2));

            // 批量收集数据
            List<UsageEntry> batchEntries = new ArrayList<>();
            int batchSize = 0;

            // RMap 的 entrySet 迭代使用 HSCAN
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                try {
                    String field = entry.getKey();
                    Object rawCount = entry.getValue();
                    if (rawCount == null) {
                        continue;
                    }
                    long count;
                    if (rawCount instanceof Number) {
                        count = ((Number) rawCount).longValue();
                    } else if (rawCount instanceof String) {
                        try {
                            count = Long.parseLong((String) rawCount);
                        } catch (NumberFormatException e) {
                            log.warn("Invalid usage count: {}", rawCount);
                            continue;
                        }
                    } else {
                        log.warn("Invalid usage count type: {}", rawCount.getClass().getName());
                        continue;
                    }

                    String[] parts = field.contains(FIELD_SEPARATOR)
                        ? field.split(FIELD_SEPARATOR)
                        : field.split(SEPARATOR);
                    if (parts.length != 3) {
                        log.warn("Invalid usage field format: {}", field);
                        continue;
                    }

                    String userId = parts[0];
                    String ip = parts[1];
                    String interfaceName = parts[2];

                    batchEntries.add(new UsageEntry(userId, ip, interfaceName, count));
                    batchSize++;

                    // 每 100 条批量写入一次
                    if (batchSize >= 100) {
                        flushBatchToDatabase(batchEntries, date);
                        batchEntries.clear();
                        batchSize = 0;
                    }

                } catch (Exception e) {
                    log.error("Failed to process entry: {}", entry.getKey(), e);
                }
            }

            // 处理剩余数据
            if (!batchEntries.isEmpty()) {
                flushBatchToDatabase(batchEntries, date);
            }

        } catch (Exception e) {
            log.error("Failed to sync date: {}", dateStr, e);
        }
    }

    /**
     * 批量刷新数据到数据库
     */
    private void flushBatchToDatabase(List<UsageEntry> entries, LocalDate date) {
        if (entries.isEmpty()) {
            return;
        }

        try {
            // 转换为实体对象
            List<InterfaceDailyUsage> records = entries.stream()
                .map(entry -> InterfaceDailyUsage.builder()
                    .userId(entry.userId)
                    .ip(entry.ip)
                    .interfaceName(entry.interfaceName)
                    .usageDate(date)
                    .requestCount(entry.count)
                    .build())
                .toList();
            
            // 检查是否有有效记录，避免空列表导致 SQL 错误
            if (records.isEmpty()) {
                log.debug("没有有效记录需要写入数据库");
                return;
            }
            
            // 使用真正的批量 SQL 操作
            repository.batchUpsertUsage(records);
            
            log.debug("批量写入 {} 条记录到数据库", records.size());
        } catch (Exception e) {
            log.error("批量写入数据库失败", e);
        }
    }

    /**
     * 内部类，用于批量处理
     */
    private static class UsageEntry {
        String userId;
        String ip;
        String interfaceName;
        long count;

        UsageEntry(String userId, String ip, String interfaceName, long count) {
            this.userId = userId;
            this.ip = ip;
            this.interfaceName = interfaceName;
            this.count = count;
        }
    }
}
