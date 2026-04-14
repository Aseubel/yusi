package com.aseubel.yusi.service.cognition.impl;

import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.common.event.ChatCognitionIngestEvent;
import com.aseubel.yusi.common.event.DiaryCognitionIngestEvent;
import com.aseubel.yusi.common.event.EmotionPlazaCognitionIngestEvent;
import com.aseubel.yusi.pojo.dto.cognition.CognitionIngestCommand;
import com.aseubel.yusi.pojo.dto.cognition.CognitionRoutingResult;
import com.aseubel.yusi.service.cognition.AgentCognitionOrchestrator;
import com.aseubel.yusi.service.cognition.CognitionRoutingService;
import com.aseubel.yusi.service.lifegraph.LifeGraphCognitionBridgeService;
import com.aseubel.yusi.service.match.MatchProfileAssembler;
import com.aseubel.yusi.service.memory.MidMemoryUpdateService;
import com.aseubel.yusi.service.persona.UserPersonaUpdateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentCognitionOrchestratorImpl implements AgentCognitionOrchestrator {

    private final CognitionRoutingService cognitionRoutingService;
    private final UserPersonaUpdateService userPersonaUpdateService;
    private final MidMemoryUpdateService midMemoryUpdateService;
    private final LifeGraphCognitionBridgeService lifeGraphCognitionBridgeService;
    private final MatchProfileAssembler matchProfileAssembler;

    @Override
    public void ingest(CognitionIngestCommand command) {
        if (command == null || StrUtil.isBlank(command.getUserId()) || StrUtil.isBlank(command.getMaskedText())) {
            return;
        }
        CognitionRoutingResult routingResult = cognitionRoutingService.route(command);
        userPersonaUpdateService.mergeFromRouting(command.getUserId(), routingResult);
        if (routingResult != null
                && StrUtil.isNotBlank(routingResult.getMidMemorySummary())
                && !"CHAT_SUMMARY".equalsIgnoreCase(command.getSourceType())) {
            midMemoryUpdateService.appendSnapshot(
                    command.getUserId(),
                    routingResult.getMidMemorySummary(),
                    routingResult.getMidMemoryImportance());
        }
        lifeGraphCognitionBridgeService.bridge(command, routingResult);
        matchProfileAssembler.refreshProfile(command.getUserId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDiaryCognitionIngest(DiaryCognitionIngestEvent event) {
        log.debug("收到日记认知摄取事件: userId={}, sourceId={}",
                event.getCommand().getUserId(), event.getCommand().getSourceId());
        ingest(event.getCommand());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onChatCognitionIngest(ChatCognitionIngestEvent event) {
        log.debug("收到聊天认知摄取事件: userId={}, sourceId={}",
                event.getCommand().getUserId(), event.getCommand().getSourceId());
        ingest(event.getCommand());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onEmotionPlazaCognitionIngest(EmotionPlazaCognitionIngestEvent event) {
        log.debug("收到广场认知摄取事件: userId={}, sourceId={}",
                event.getCommand().getUserId(), event.getCommand().getSourceId());
        ingest(event.getCommand());
    }
}
