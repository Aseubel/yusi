package com.aseubel.yusi.service.persona.impl;

import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.pojo.dto.cognition.CognitionRoutingResult;
import com.aseubel.yusi.pojo.entity.UserPersona;
import com.aseubel.yusi.service.persona.UserPersonaUpdateService;
import com.aseubel.yusi.service.user.UserPersonaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserPersonaUpdateServiceImpl implements UserPersonaUpdateService {

    private final UserPersonaService userPersonaService;

    @Override
    @Transactional
    public void mergeFromRouting(String userId, CognitionRoutingResult routingResult) {
        mergeFromRouting(userId, routingResult, null, null);
    }

    @Override
    @Transactional
    public void mergeFromRouting(String userId, CognitionRoutingResult routingResult,
            String sourceType, String sourceId) {
        if (StrUtil.isBlank(userId) || routingResult == null) {
            return;
        }

        if (StrUtil.isAllBlank(
                routingResult.getPreferredName(),
                routingResult.getLocation(),
                routingResult.getInterests(),
                routingResult.getTone(),
                routingResult.getCustomInstructions())) {
            return;
        }

        UserPersona.UserPersonaBuilder update = UserPersona.builder()
                .preferredName(blankToNull(routingResult.getPreferredName()))
                .location(blankToNull(routingResult.getLocation()))
                .interests(blankToNull(routingResult.getInterests()))
                .tone(blankToNull(routingResult.getTone()))
                .customInstructions(blankToNull(routingResult.getCustomInstructions()));
        if (StrUtil.isNotBlank(sourceType)) {
            update.sourceType(sourceType).sourceId(sourceId).confidence(0.5);
        }
        userPersonaService.updateUserPersona(userId, update.build());
    }

    private String blankToNull(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }
}
