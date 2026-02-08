package com.aseubel.yusi.service.lifegraph;

import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.config.security.CryptoService;
import com.aseubel.yusi.common.utils.AesGcmCryptoUtils;
import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.pojo.entity.LifeGraphTask;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.repository.DiaryRepository;
import com.aseubel.yusi.repository.LifeGraphTaskRepository;
import com.aseubel.yusi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LifeGraphTaskBatchService {

    private final LifeGraphTaskRepository taskRepository;
    private final DiaryRepository diaryRepository;
    private final UserRepository userRepository;
    private final CryptoService cryptoService;
    private final LifeGraphBuildService lifeGraphBuildService;

    private static final int BATCH_SIZE = 10;

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void processPendingTasks() {
        LocalDateTime now = LocalDateTime.now();
        List<LifeGraphTask> tasks = taskRepository.findPendingTasksForUpdate(now, BATCH_SIZE);
        if (tasks.isEmpty()) {
            return;
        }

        List<Long> taskIds = tasks.stream().map(LifeGraphTask::getId).collect(Collectors.toList());
        taskRepository.markAsProcessing(taskIds, now);

        for (LifeGraphTask task : tasks) {
            try {
                if (task.getTaskType() == LifeGraphTask.TaskType.DELETE) {
                    lifeGraphBuildService.deleteByDiary(task.getUserId(), task.getDiaryId());
                    taskRepository.markAsCompleted(task.getId(), now);
                    continue;
                }

                Diary diary = diaryRepository.findByDiaryId(task.getDiaryId());
                if (diary == null) {
                    taskRepository.markAsCompleted(task.getId(), now);
                    continue;
                }

                String plain = decryptDiaryContent(diary);
                if (StrUtil.isBlank(plain)) {
                    taskRepository.markAsCompleted(task.getId(), now);
                    continue;
                }

                lifeGraphBuildService.upsertFromDiary(diary, plain);
                taskRepository.markAsCompleted(task.getId(), now);
            } catch (Exception e) {
                LocalDateTime nextRetry = calculateNextRetry(task.getRetryCount() + 1);
                taskRepository.incrementRetryAndSetNextAttempt(task.getId(), e.getMessage(), nextRetry, now);
            }
        }
    }

    @Scheduled(fixedDelay = 3600000)
    @Transactional
    public void cleanupCompletedTasks() {
        LocalDateTime before = LocalDateTime.now().minusHours(24);
        int deleted = taskRepository.deleteCompletedBefore(before);
        if (deleted > 0) {
            log.info("清理 {} 个已完成的 LifeGraph 任务", deleted);
        }
    }

    public void processSingleTask(Long taskId, Diary diary, String plainContent) {
        LocalDateTime now = LocalDateTime.now();
        try {
            if (diary == null) {
                taskRepository.markAsCompleted(taskId, now);
                return;
            }
            String plain = StrUtil.isNotBlank(plainContent) ? plainContent : decryptDiaryContent(diary);
            if (StrUtil.isBlank(plain)) {
                taskRepository.markAsCompleted(taskId, now);
                return;
            }
            lifeGraphBuildService.upsertFromDiary(diary, plain);
            taskRepository.markAsCompleted(taskId, now);
        } catch (Exception e) {
            LocalDateTime nextRetry = calculateNextRetry(1);
            taskRepository.incrementRetryAndSetNextAttempt(taskId, e.getMessage(), nextRetry, now);
        }
    }

    LocalDateTime calculateNextRetry(int retryCount) {
        long delaySeconds = (long) Math.pow(5, retryCount);
        return LocalDateTime.now().plusSeconds(Math.min(delaySeconds, 3600));
    }

    private String decryptDiaryContent(Diary diary) {
        if (diary == null) {
            return null;
        }
        if (StrUtil.isNotBlank(diary.getPlainContent())) {
            return diary.getPlainContent();
        }
        if (StrUtil.isBlank(diary.getContent())) {
            return null;
        }

        User user = userRepository.findByUserId(diary.getUserId());
        if (user == null) {
            return null;
        }

        String keyMode = user.getKeyMode();
        if (keyMode == null || "DEFAULT".equals(keyMode)) {
            return AesGcmCryptoUtils.decryptText(diary.getContent(), cryptoService.serverAesKeyBytes());
        }

        if ("CUSTOM".equals(keyMode)) {
            if (!Boolean.TRUE.equals(user.getHasCloudBackup())) {
                return null;
            }
            if (StrUtil.isBlank(user.getEncryptedBackupKey())) {
                return null;
            }
            byte[] keyBytes = cryptoService.decryptBackupKeyBase64(user.getEncryptedBackupKey());
            if (keyBytes.length != 32) {
                return null;
            }
            return AesGcmCryptoUtils.decryptText(diary.getContent(), keyBytes);
        }

        return null;
    }
}
