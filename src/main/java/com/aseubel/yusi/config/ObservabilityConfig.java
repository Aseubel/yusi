package com.aseubel.yusi.config;

import com.aseubel.yusi.observability.task.TaskHealthRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Infrastructure wiring for low-sensitivity health state. */
@Configuration(proxyBeanMethods = false)
public class ObservabilityConfig {

    @Bean
    @ConditionalOnMissingBean
    public TaskHealthRegistry taskHealthRegistry() {
        return new TaskHealthRegistry();
    }
}
