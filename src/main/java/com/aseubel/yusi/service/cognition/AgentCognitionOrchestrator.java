package com.aseubel.yusi.service.cognition;

import com.aseubel.yusi.pojo.dto.cognition.CognitionIngestCommand;

public interface AgentCognitionOrchestrator {

    void ingest(CognitionIngestCommand command);
}
