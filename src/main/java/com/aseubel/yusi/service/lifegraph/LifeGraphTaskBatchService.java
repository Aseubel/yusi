package com.aseubel.yusi.service.lifegraph;

import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.config.security.CryptoService;
import com.aseubel.yusi.common.utils.AesGcmCryptoUtils;
import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.pojo.entity.LifeGraphTask;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.pojo.constant.KeyMode;
import com.aseubel.yusi.repository.DiaryRepository;
import com.aseubel.yusi.repository.LifeGraphTaskRepository;
import com.aseubel.yusi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LifeGraphTaskBatchService {

    private final LifeGraphTaskRepository taskRepository;
    private final LifeGraphTaskClaimService taskClaimService;
    private final DiaryRepository diaryRepository;
    private final UserRepository userRepository;
    private final CryptoService cryptoService;
    private final LifeGraphBuildService lifeGraphBuildService;

    private static final int BATCH_SIZE = 10;
    private static final long PROCESSING_TIMEOUT_MINUTES = 30;

    public void processPendingTasks() {
        LocalDateTime now = LocalDateTime.now();
        List<LifeGraphTask> tasks = taskClaimService.claimPendingTasks(now, BATCH_SIZE);
        if (tasks.isEmpty()) {
            return;
        }

        for (LifeGraphTask task : tasks) {
            try {
                if (task.getTaskType() == LifeGraphTask.TaskType.DELETE) {
                    lifeGraphBuildService.deleteByDiary(task.getUserId(), task.getDiaryId());
                    taskRepository.markAsCompleted(task.getId(), now);
                    continue;
                }

                Diary diary = diaryRepository.findByDiaryIdAndUserId(task.getDiaryId(), task.getUserId());
                if (diary == null) {
                    lifeGraphBuildService.deleteByDiary(task.getUserId(), task.getDiaryId());
                    taskRepository.markAsCompleted(task.getId(), now);
                    continue;
                }

                String plain = decryptDiaryContent(diary);
                if (StrUtil.isBlank(plain)) {
                    lifeGraphBuildService.deleteByDiary(task.getUserId(), task.getDiaryId());
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

    /**
     * 定期回收进程崩溃或 worker 超时遗留的 PROCESSING 任务。
     */
    @Transactional
    public void recoverStaleTasks() {
        LocalDateTime now = LocalDateTime.now();
        int recovered = taskRepository.recoverStaleProcessing(
                now.minusMinutes(PROCESSING_TIMEOUT_MINUTES), now,
                "任务处理超时，已自动回收并重试");
        if (recovered > 0) {
            log.warn("回收 {} 个超时 LifeGraph 任务", recovered);
        }
    }

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
                lifeGraphBuildService.deleteByDiary(diary.getUserId(), diary.getDiaryId());
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
        if (keyMode == null || KeyMode.DEFAULT.code().equals(keyMode)) {
            return AesGcmCryptoUtils.decryptText(diary.getContent(), cryptoService.serverAesKeyBytes());
        }

        if (KeyMode.CUSTOM.code().equals(keyMode)) {
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
