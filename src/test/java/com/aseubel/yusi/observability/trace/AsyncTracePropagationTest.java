package com.aseubel.yusi.observability.trace;

import com.aseubel.yusi.config.ThreadPoolConfig;
import com.aseubel.yusi.config.ThreadPoolConfigProperties;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncTracePropagationTest {

    @Test
    void executorCopiesTraceIdAndClearsItBeforeWorkerReuse() throws Exception {
        ThreadPoolTaskExecutor executor = new ThreadPoolConfig()
                .threadPoolExecutor(new ThreadPoolConfigProperties());
        try {
            MDC.put(TraceIdSupport.MDC_KEY, "async-trace-1");
            Future<String> first = executor.submit(() -> MDC.get(TraceIdSupport.MDC_KEY));
            assertThat(first.get()).isEqualTo("async-trace-1");

            MDC.put(TraceIdSupport.MDC_KEY, "async-trace-2");
            Future<String> second = executor.submit(() -> MDC.get(TraceIdSupport.MDC_KEY));
            assertThat(second.get()).isEqualTo("async-trace-2");

            MDC.clear();
            Future<String> cleared = executor.submit(() -> MDC.get(TraceIdSupport.MDC_KEY));
            assertThat(cleared.get()).isNull();
        } finally {
            MDC.clear();
            executor.shutdown();
        }
    }
}
