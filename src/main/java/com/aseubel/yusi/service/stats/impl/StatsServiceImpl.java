package com.aseubel.yusi.service.stats.impl;

import com.aseubel.yusi.pojo.dto.stats.PlatformStatsResponse;
import com.aseubel.yusi.repository.*;
import com.aseubel.yusi.service.stats.StatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 平台统计服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    private final UserRepository userRepository;
    private final DiaryRepository diaryRepository;
    private final SoulCardRepository soulCardRepository;
    private final SituationRoomRepository situationRoomRepository;
    private final SoulResonanceRepository soulResonanceRepository;
    private final StringRedisTemplate redisTemplate;

    private static final String STATS_CACHE_KEY = "yusi:stats:platform";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    @Override
    public PlatformStatsResponse getPlatformStats() {
        // 尝试从缓存获取
        String cached = redisTemplate.opsForValue().get(STATS_CACHE_KEY);
        if (cached != null) {
            try {
                String[] parts = cached.split(",");
                if (parts.length == 5) {
                    return PlatformStatsResponse.builder()
                            .userCount(Long.parseLong(parts[0]))
                            .diaryCount(Long.parseLong(parts[1]))
                            .soulCardCount(Long.parseLong(parts[2]))
                            .roomCount(Long.parseLong(parts[3]))
                            .resonanceCount(Long.parseLong(parts[4]))
                            .build();
                }
            } catch (Exception e) {
                log.warn("Failed to parse cached stats", e);
            }
        }

        // 从数据库获取统计数据
        long userCount = userRepository.count();
        long diaryCount = diaryRepository.count();
        long soulCardCount = soulCardRepository.count();
        long roomCount = situationRoomRepository.count();
        long resonanceCount = soulResonanceRepository.count();

        // 缓存结果
        String cacheValue = String.format("%d,%d,%d,%d,%d",
                userCount, diaryCount, soulCardCount, roomCount, resonanceCount);
        redisTemplate.opsForValue().set(STATS_CACHE_KEY, cacheValue, CACHE_TTL);

        return PlatformStatsResponse.builder()
                .userCount(userCount)
                .diaryCount(diaryCount)
                .soulCardCount(soulCardCount)
                .roomCount(roomCount)
                .resonanceCount(resonanceCount)
                .build();
    }
}
