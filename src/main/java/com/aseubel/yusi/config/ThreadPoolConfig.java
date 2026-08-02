package com.aseubel.yusi.config;

import com.aseubel.yusi.common.auth.UserContext;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

@EnableAsync
@Configuration
@EnableConfigurationProperties(ThreadPoolConfigProperties.class)
public class ThreadPoolConfig {

    @Bean
    @ConditionalOnMissingBean(ThreadPoolTaskExecutor.class)
    public ThreadPoolTaskExecutor threadPoolExecutor(ThreadPoolConfigProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getCorePoolSize());
        executor.setMaxPoolSize(properties.getMaxPoolSize());
        executor.setKeepAliveSeconds(properties.getKeepAliveSeconds());
        executor.setQueueCapacity(properties.getBlockQueueSize());
        executor.setThreadNamePrefix("yusi-service-");
        executor.setRejectedExecutionHandler(createRejectedExecutionHandler(properties.getPolicy()));
        executor.setTaskDecorator(contextTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    private RejectedExecutionHandler createRejectedExecutionHandler(String policy) {
        return switch (policy) {
            case "DiscardPolicy" -> new ThreadPoolExecutor.DiscardPolicy();
            case "DiscardOldestPolicy" -> new ThreadPoolExecutor.DiscardOldestPolicy();
            case "CallerRunsPolicy" -> new ThreadPoolExecutor.CallerRunsPolicy();
            default -> new ThreadPoolExecutor.AbortPolicy();
        };
    }

    private TaskDecorator contextTaskDecorator() {
        return runnable -> {
            Map<String, String> context = MDC.getCopyOfContextMap();
            String userId = UserContext.getUserId();
            return () -> {
                try {
                    if (context != null) {
                        MDC.setContextMap(context);
                    }
                    if (userId != null) {
                        UserContext.setUserId(userId);
                    }
                    runnable.run();
                } finally {
                    MDC.clear();
                    UserContext.clear();
                }
            };
        };
    }
}
