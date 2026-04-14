package com.aseubel.yusi.service.lifegraph;

import com.aseubel.yusi.pojo.dto.cognition.CognitionIngestCommand;
import com.aseubel.yusi.pojo.dto.cognition.CognitionRoutingResult;

public interface LifeGraphCognitionBridgeService {

    void bridge(CognitionIngestCommand command, CognitionRoutingResult routingResult);
}
