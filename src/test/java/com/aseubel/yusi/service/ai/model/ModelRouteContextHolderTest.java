package com.aseubel.yusi.service.ai.model;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ModelRouteContextHolderTest {

    @AfterEach
    void clearContext() {
        while (ModelRouteContextHolder.get() != null) {
            ModelRouteContextHolder.clear();
        }
    }

    @Test
    void closingNestedContextRestoresOuterCorrelation() {
        ModelRouteContext outer = ModelRouteContext.builder()
                .runId("run-outer")
                .userId("user-1")
                .scene("cognition")
                .build();
        ModelRouteContext inner = ModelRouteContext.builder()
                .scene("image")
                .promptKey("image-understanding")
                .build();

        try (ModelRouteContextHolder.Scope ignored = ModelRouteContextHolder.open(outer)) {
            ModelRouteContextHolder.set(inner);
            assertEquals("image", ModelRouteContextHolder.get().getScene());
            assertEquals("run-outer", ModelRouteContextHolder.getEffective().getRunId());
            assertEquals("user-1", ModelRouteContextHolder.getEffective().getUserId());

            ModelRouteContextHolder.clear();
            assertEquals("run-outer", ModelRouteContextHolder.get().getRunId());
        }

        assertNull(ModelRouteContextHolder.get());
    }
}
