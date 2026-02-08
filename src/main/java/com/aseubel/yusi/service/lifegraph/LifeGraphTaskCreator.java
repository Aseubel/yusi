package com.aseubel.yusi.service.lifegraph;

import com.aseubel.yusi.common.disruptor.Element;
import com.aseubel.yusi.common.disruptor.EventType;
import com.aseubel.yusi.common.repochain.Processor;
import com.aseubel.yusi.common.repochain.ProcessorChain;
import com.aseubel.yusi.common.repochain.Result;
import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.pojo.entity.LifeGraphTask;
import com.aseubel.yusi.repository.LifeGraphTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LifeGraphTaskCreator implements Processor<Element> {

    private final LifeGraphTaskRepository taskRepository;
    private final LifeGraphTaskBatchService batchService;
    private final ThreadPoolTaskExecutor threadPoolExecutor;

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public Result<Element> process(Element data, int index, ProcessorChain<Element> chain) {
        if (data.getEventType() != EventType.DIARY_WRITE
                && data.getEventType() != EventType.DIARY_MODIFY
                && data.getEventType() != EventType.DIARY_DELETE) {
            return chain.process(data, index);
        }

        Diary diary = (Diary) data.getData();

        switch (data.getEventType()) {
            case DIARY_WRITE:
            case DIARY_MODIFY:
                createUpsertTask(diary);
                break;
            case DIARY_DELETE:
                createDeleteTask(diary);
                break;
            default:
                break;
        }

        return chain.process(data, index);
    }

    private void createUpsertTask(Diary diary) {
        List<LifeGraphTask> pending = taskRepository.findPendingByDiaryId(diary.getDiaryId());
        if (!pending.isEmpty()) {
            return;
        }

        String plainContent = diary.getPlainContent();
        boolean canProcessImmediately = plainContent != null && !plainContent.isBlank();

        LifeGraphTask task = LifeGraphTask.createUpsertTask(diary.getDiaryId(), diary.getUserId());
        if (canProcessImmediately) {
            task.setStatus(LifeGraphTask.TaskStatus.PROCESSING);
        }

        LifeGraphTask saved = taskRepository.save(task);

        if (canProcessImmediately) {
            threadPoolExecutor.execute(() -> {
                try {
                    batchService.processSingleTask(saved.getId(), diary, plainContent);
                } catch (Exception e) {
                    LocalDateTime now = LocalDateTime.now();
                    taskRepository.incrementRetryAndSetNextAttempt(saved.getId(), e.getMessage(),
                            batchService.calculateNextRetry(saved.getRetryCount() + 1), now);
                }
            });
        }
    }

    private void createDeleteTask(Diary diary) {
        LifeGraphTask task = LifeGraphTask.createDeleteTask(diary.getDiaryId(), diary.getUserId());
        taskRepository.save(task);
    }
}
