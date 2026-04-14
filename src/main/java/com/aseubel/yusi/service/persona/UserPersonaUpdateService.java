package com.aseubel.yusi.service.persona;

import com.aseubel.yusi.pojo.dto.cognition.CognitionRoutingResult;

public interface UserPersonaUpdateService {

    void mergeFromRouting(String userId, CognitionRoutingResult routingResult);
}
