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

        userPersonaService.updateUserPersona(userId, UserPersona.builder()
                .preferredName(blankToNull(routingResult.getPreferredName()))
                .location(blankToNull(routingResult.getLocation()))
                .interests(blankToNull(routingResult.getInterests()))
                .tone(blankToNull(routingResult.getTone()))
                .customInstructions(blankToNull(routingResult.getCustomInstructions()))
                .build());
    }

    private String blankToNull(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }
}
