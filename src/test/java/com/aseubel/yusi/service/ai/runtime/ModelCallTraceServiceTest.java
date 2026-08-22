package com.aseubel.yusi.service.ai.runtime;

import com.aseubel.yusi.repository.ModelCallTraceRepository;
import com.aseubel.yusi.observability.metrics.YusiMetrics;
import com.aseubel.yusi.service.ai.model.ModelUsageSnapshot;
import com.aseubel.yusi.pojo.entity.ModelCallTrace;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ModelCallTraceServiceTest {

    @Mock
    private ModelCallTraceRepository traceRepository;

    @Test
    void persistCopiesPromptIdentityWithoutPersistingPromptTemplate() {
        ModelCallTraceService service = new ModelCallTraceService(traceRepository);
        service.persist(new ModelCallAttemptEvent(
                "request-1", "attempt-1", "run-1", "user-1", "chat",
                "chat", "v7", "zh-CN", "policy", 1L, "route", "primary", "primary",
                "model-1", "provider", "model", ModelUsageSnapshot.unavailable("price-v1"),
                10L, null, 0, false, "SUCCESS", null, "stop"));

        ArgumentCaptor<ModelCallTrace> captor = ArgumentCaptor.forClass(ModelCallTrace.class);
        verify(traceRepository).save(captor.capture());
        ModelCallTrace trace = captor.getValue();
        assertEquals("run-1", trace.getRunId());
        assertEquals("user-1", trace.getUserId());
        assertEquals("chat", trace.getPromptKey());
        assertEquals("v7", trace.getPromptVersion());
        assertEquals("zh-CN", trace.getPromptLocale());
        assertFalse(Arrays.stream(ModelCallTrace.class.getDeclaredFields())
                .map(Field::getName)
                .anyMatch("template"::equals));
    }

    @Test
    void recordsSpecificModelFailureCategoryForMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ModelCallTraceService service = new ModelCallTraceService(traceRepository, new YusiMetrics(registry));

        service.persist(new ModelCallAttemptEvent(
                "request-2", "attempt-2", "run-2", "user-2", "chat",
                "chat", "v7", "zh-CN", "policy", 1L, "route", "primary", "primary",
                "model-2", "provider", "model", ModelUsageSnapshot.unavailable("price-v1"),
                10L, null, 0, false, "FAILED", "AUTHENTICATION", null));

        assertEquals("authentication", registry.find("model_call_total").counter()
                .getId().getTag("failure_category"));
    }
}
