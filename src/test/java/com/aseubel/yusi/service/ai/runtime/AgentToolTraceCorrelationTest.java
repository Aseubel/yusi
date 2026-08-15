package com.aseubel.yusi.service.ai.runtime;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentToolTraceCorrelationTest {

    @Test
    void resolvesByRequestIdentityBeforeUpstreamId() {
        AgentToolTraceCorrelation correlation = new AgentToolTraceCorrelation();
        Object request = new Object();
        correlation.register(request, "upstream-1", "web_search", "local-1");

        assertEquals(Optional.of("local-1"), correlation.resolve(request, "upstream-other", "web_search"));
    }

    @Test
    void resolvesNullUpstreamIdsInToolOrderAndReturnsEmptyWhenDrained() {
        AgentToolTraceCorrelation correlation = new AgentToolTraceCorrelation();
        correlation.register(new Object(), null, "searchMemories", "local-1");
        correlation.register(new Object(), null, "searchMemories", "local-2");

        assertEquals(Optional.of("local-1"), correlation.resolve(new Object(), null, "searchMemories"));
        assertEquals(Optional.of("local-2"), correlation.resolve(new Object(), null, "searchMemories"));
        assertTrue(correlation.resolve(new Object(), null, "searchMemories").isEmpty());
    }

    @Test
    void clearDropsAllPendingCorrelationHandles() {
        AgentToolTraceCorrelation correlation = new AgentToolTraceCorrelation();
        correlation.register(new Object(), "upstream-1", "web_search", "local-1");

        correlation.clear();

        assertTrue(correlation.resolve(new Object(), "upstream-1", "web_search").isEmpty());
    }
}
