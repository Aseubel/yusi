package com.aseubel.yusi.service.persona.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.IdUtil;
import com.aseubel.yusi.pojo.dto.cognition.CognitionRoutingResult;
import com.aseubel.yusi.pojo.constant.TaskExecutionKeys;
import com.aseubel.yusi.pojo.constant.TaskExecutionSourceType;
import com.aseubel.yusi.pojo.constant.TaskExecutionType;
import com.aseubel.yusi.pojo.entity.UserPersona;
import com.aseubel.yusi.service.persona.UserPersonaUpdateService;
import com.aseubel.yusi.service.task.TaskExecutionCommand;
import com.aseubel.yusi.service.task.TaskExecutionService;
import com.aseubel.yusi.service.user.UserPersonaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserPersonaUpdateServiceImpl implements UserPersonaUpdateService {

    private final UserPersonaService userPersonaService;
    private final TaskExecutionService taskExecutionService;

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

        String taskSourceType = StrUtil.isBlank(sourceType)
                ? TaskExecutionSourceType.PERSONA.code() : sourceType;
        String taskSourceId = StrUtil.isBlank(sourceId) ? userId : sourceId;
        String invocationId = IdUtil.fastSimpleUUID();
        var execution = taskExecutionService.createOrGet(TaskExecutionCommand.builder()
                .taskType(TaskExecutionType.PERSONA)
                .ownerUserId(userId)
                .sourceType(taskSourceType)
                .sourceId(taskSourceId)
                .sourceVersion(invocationId)
                .idempotencyKey(TaskExecutionKeys.invocation(TaskExecutionType.PERSONA, userId,
                        taskSourceId, invocationId))
                .build());

        UserPersona.UserPersonaBuilder update = UserPersona.builder()
                .preferredName(blankToNull(routingResult.getPreferredName()))
                .location(blankToNull(routingResult.getLocation()))
                .interests(blankToNull(routingResult.getInterests()))
                .tone(blankToNull(routingResult.getTone()))
                .customInstructions(blankToNull(routingResult.getCustomInstructions()));
        if (StrUtil.isNotBlank(sourceType)) {
            update.sourceType(sourceType).sourceId(sourceId).confidence(0.5);
        }
        try {
            userPersonaService.updateUserPersona(userId, update.build());
            taskExecutionService.succeed(execution.getTaskId(), null, null);
        } catch (RuntimeException exception) {
            taskExecutionService.fail(execution.getTaskId(), null, null, null);
            throw exception;
        }
    }

    private String blankToNull(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }
}
