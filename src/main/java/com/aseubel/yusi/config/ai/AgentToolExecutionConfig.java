package com.aseubel.yusi.config.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class AgentToolExecutionConfig {

    @Bean(name = "agentToolExecutionExecutor", destroyMethod = "shutdown")
    public ExecutorService agentToolExecutionExecutor(
            @Value("${agent.tool.execution.pool-size:8}") int poolSize) {
        int effectivePoolSize = Math.max(1, poolSize);
        AtomicInteger threadNumber = new AtomicInteger();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable,
                    "agent-tool-exec-" + threadNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newFixedThreadPool(effectivePoolSize, threadFactory);
    }
}
