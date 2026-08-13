package com.aseubel.yusi.service.lifegraph.impl;

import com.aseubel.yusi.pojo.dto.cognition.CognitionIngestCommand;
import com.aseubel.yusi.pojo.dto.cognition.CognitionRoutingResult;
import com.aseubel.yusi.service.lifegraph.LifeGraphCognitionBridgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LifeGraphCognitionBridgeServiceImpl implements LifeGraphCognitionBridgeService {

    @Override
    public void bridge(CognitionIngestCommand command, CognitionRoutingResult routingResult) {
        // Cognition routing owns persona and mid-term memory. Long-term
        // LifeGraph writes must go through the evidence-backed extractor.
    }
}
