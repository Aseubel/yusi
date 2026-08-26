package com.aseubel.yusi.monitor;

import com.aseubel.yusi.redis.service.IRedisService;
import com.aseubel.yusi.repository.InterfaceDailyUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RMap;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterfaceUsageMonitorTest {

    private static final String TARGET_USER = "fixture-usage-delete-target";

    @Mock
    private IRedisService redisService;
    @Mock
    private InterfaceDailyUsageRepository repository;
    @Mock
    private ThreadPoolTaskExecutor threadPoolExecutor;
    @Mock
    private RMap<Object, Object> usageMap;

    private InterfaceUsageMonitor monitor;

    @BeforeEach
    void setUp() {
        monitor = new InterfaceUsageMonitor(redisService, repository, threadPoolExecutor);
        when(redisService.getMap(anyString())).thenReturn(usageMap);
    }

    @Test
    void suppressedUserUsageIsRemovedFromBufferAndNeverSynced() {
        monitor.recordUsage(TARGET_USER, "fixture-ip", "fixture-interface");

        monitor.suppressUserFromUsage(TARGET_USER);
        monitor.syncToDatabase();
        monitor.recordUsage(TARGET_USER, "fixture-ip", "fixture-interface");

        verify(redisService).removeUsageFields(TARGET_USER);
        verify(usageMap, times(1)).addAndGet(anyString(), eq(1L));
        verify(repository, never()).batchUpsertUsage(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void releasedUserUsageCanBeRecordedAgain() {
        monitor.suppressUserFromUsage(TARGET_USER);
        monitor.releaseUserFromUsage(TARGET_USER);

        monitor.recordUsage(TARGET_USER, "fixture-ip", "fixture-interface");

        verify(usageMap).addAndGet(anyString(), eq(1L));
    }
}
