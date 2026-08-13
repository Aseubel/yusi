package com.aseubel.yusi.service.lifegraph;

import com.aseubel.yusi.common.event.PlazaCardChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs full LifeGraph extraction only for cards authored by the card owner.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlazaLifeGraphListener {

    private final LifeGraphBuildService lifeGraphBuildService;

    @Async("threadPoolExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCardChanged(PlazaCardChangedEvent event) {
        if (event == null || event.getCommand() == null) {
            return;
        }
        try {
            if (event.getType() == PlazaCardChangedEvent.Type.DELETE) {
                lifeGraphBuildService.deleteBySource(
                        event.getCommand().getUserId(), "PLAZA", event.getCommand().getSourceId());
            } else {
                lifeGraphBuildService.upsertFromPlaza(event.getCommand());
            }
        } catch (Exception exception) {
            log.warn("Plaza LifeGraph source processing failed: sourceId={}",
                    event.getCommand().getSourceId(), exception);
        }
    }
}
