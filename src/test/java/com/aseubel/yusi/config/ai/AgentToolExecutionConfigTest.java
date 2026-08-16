package com.aseubel.yusi.config.ai;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentToolExecutionConfigTest {

    @Test
    void usesBoundedQueueAndRejectsOverflowOnTheCallerThread() throws Exception {
        ThreadPoolExecutor executor = (ThreadPoolExecutor) new AgentToolExecutionConfig()
                .agentToolExecutionExecutor(1, 1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            executor.execute(() -> await(release));
            executor.execute(() -> {
            });

            assertEquals(1, executor.getCorePoolSize());
            assertEquals(1, executor.getMaximumPoolSize());
            assertEquals(0, executor.getQueue().remainingCapacity());
            assertThrows(RejectedExecutionException.class, () -> executor.execute(() -> {
            }));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private void await(CountDownLatch release) {
        try {
            release.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
