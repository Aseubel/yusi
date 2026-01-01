package com.aseubel.yusi.monitor;

import com.aseubel.yusi.redis.IRedisService;
import com.aseubel.yusi.repository.InterfaceDailyUsageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterfaceUsageMonitor {

    private final IRedisService redisService;
    private final InterfaceDailyUsageRepository repository;

    private static final String REDIS_KEY_PREFIX = "yusi:usage:";
    private static final String SEPARATOR = "::";

    /**
     * Record interface usage to Redis
     */
    public void recordUsage(String userId, String ip, String interfaceName) {
        try {
            if (userId == null) userId = "anonymous";
            if (ip == null) ip = "unknown";
            
            String dateStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            String redisKey = REDIS_KEY_PREFIX + dateStr;
            String field = userId + SEPARATOR + ip + SEPARATOR + interfaceName;

            // Use Redisson's addAndGet for atomic increment
            // Note: IRedisService needs to expose getMap properly or we cast/use RedissonClient directly
            // Assuming redisService.getMap returns RMap
            RMap<String, Long> map = redisService.getMap(redisKey);
            map.addAndGet(field, 1L);
            
            // Set expiration if new (e.g., 2 days)
            // We can check if it's a new key by checking TTL, but simplify by setting expire occasionally or just let it be
            // A simple way is to set expire every time or just assume it's set. 
            // Better: Set expire if we just created it. But addAndGet doesn't tell us.
            // Let's rely on the sync task to cleanup or set expire there.
            // Or just set it blindly with a long TTL (48h) every time? A bit wasteful on network.
            // Let's do it in the sync task.
        } catch (Exception e) {
            log.error("Failed to record interface usage", e);
        }
    }

    /**
     * Sync Redis data to Database every 30 minutes
     */
    @Scheduled(cron = "0 0/30 * * * ?")
    public void syncToDatabase() {
        log.debug("Starting interface usage sync...");
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        syncDate(yesterday);
        syncDate(today);
        
        log.debug("Interface usage sync completed.");
    }

    private void syncDate(LocalDate date) {
        String dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String redisKey = REDIS_KEY_PREFIX + dateStr;
        
        try {
            if (!redisService.isExists(redisKey)) {
                return;
            }

            RMap<String, Long> map = redisService.getMap(redisKey);
            
            // Set expiration to ensure cleanup (e.g., 2 days from now)
            map.expire(java.time.Duration.ofDays(2));

            // Iterate and sync
            // RMap entrySet iteration uses HSCAN
            for (Map.Entry<String, Long> entry : map.entrySet()) {
                try {
                    String field = entry.getKey();
                    Long count = entry.getValue();
                    
                    String[] parts = field.split(SEPARATOR);
                    if (parts.length != 3) {
                        log.warn("Invalid usage field format: {}", field);
                        continue;
                    }

                    String userId = parts[0];
                    String ip = parts[1];
                    String interfaceName = parts[2];

                    repository.upsertUsage(userId, ip, interfaceName, date, count);
                } catch (Exception e) {
                    log.error("Failed to sync entry: {}", entry.getKey(), e);
                }
            }
        } catch (Exception e) {
            log.error("Failed to sync date: {}", dateStr, e);
        }
    }
}
