package com.aseubel.yusi.service.ai.runtime;

import java.util.Optional;

@FunctionalInterface
public interface AgentToolInvocationContextProvider {

    AgentToolInvocationContextProvider NOOP = requestIdentity -> Optional.empty();

    Optional<AgentToolInvocationContext> find(Object requestIdentity);
}
