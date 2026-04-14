package com.aseubel.yusi.service.cognition;

import com.aseubel.yusi.pojo.dto.cognition.CognitionIngestCommand;
import com.aseubel.yusi.pojo.dto.cognition.CognitionRoutingResult;

public interface CognitionRoutingService {

    CognitionRoutingResult route(CognitionIngestCommand command);
}
