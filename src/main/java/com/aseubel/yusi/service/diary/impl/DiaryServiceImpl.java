package com.aseubel.yusi.service.diary.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;

import com.aseubel.yusi.config.security.CryptoService;
import com.aseubel.yusi.common.utils.AesGcmCryptoUtils;
import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.repository.DiaryRepository;
import com.aseubel.yusi.repository.UserRepository;
import com.aseubel.yusi.service.diary.Assistant;
import com.aseubel.yusi.service.diary.DiaryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * @author Aseubel
 * @date 2025/5/7 上午9:57
 */
@Slf4j
@Service
public class DiaryServiceImpl implements DiaryService {

    @Autowired
    private DiaryRepository diaryRepository;

    @Autowired
    private Assistant diaryAssistant;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CryptoService cryptoService;

    @Autowired
    @Lazy
    private DiaryService self;

    @Override
    public Diary addDiary(Diary diary) {
        diary.generateId();
        diary.setCreateTime(LocalDateTime.now());
        diary.setUpdateTime(LocalDateTime.now());
        User user = userRepository.findByUserId(diary.getUserId());
        applyWriteCrypto(diary, user);
        String plainContent = diary.getPlainContent();
        Diary saved = diaryRepository.save(diary);
        // 保存后 entity 可能会丢失 transient 字段，这里重新设置以便后续 disruptor 使用
        saved.setPlainContent(plainContent);

        // 异步生成AI回应 (通过self调用以触发AOP)
        // self.generateAiResponse(saved.getDiaryId());

        return saved;
    }

    @Async
    @Override
    public void generateAiResponse(String diaryId) {
        try {
            Diary diary = diaryRepository.findByDiaryId(diaryId);
            if (diary == null)
                return;

            log.info("Generating AI response for diary: {}", diaryId);

            CompletableFuture<String> future = new CompletableFuture<>();
            StringBuilder sb = new StringBuilder();

            String plainContent = decryptDiaryContent(diary);
            if (StrUtil.isBlank(plainContent)) {
                return;
            }

            diaryAssistant.generateDiaryResponse(plainContent, diary.getEntryDate().toString())
                    .onPartialResponse(sb::append)
                    .onCompleteResponse(res -> future.complete(sb.toString()))
                    .onError(future::completeExceptionally)
                    .start();

            String response = future.get();

            diary.setAiResponse(response);
            diary.setStatus(1); // 1 = Analyzed
            diaryRepository.save(diary);
            log.info("AI response saved for diary: {}", diaryId);
        } catch (Exception e) {
            log.error("Failed to generate AI response for diary: {}", diaryId, e);
        }
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

    @Override
    public Diary editDiary(Diary diary) {
        Diary existingDiary = diaryRepository.findByDiaryId(diary.getDiaryId());
        if (ObjectUtil.isNotEmpty(existingDiary)) {
            diary.setId(existingDiary.getId());
            diary.setUpdateTime(LocalDateTime.now());
            diary.setStatus(0);
            diary.setAiResponse(null);
            diary.setCreateTime(existingDiary.getCreateTime());
            User user = userRepository.findByUserId(diary.getUserId());
            applyWriteCrypto(diary, user);
            String plainContent = diary.getPlainContent();
            Diary saved = diaryRepository.save(diary);
            // 保存后 entity 可能会丢失 transient 字段，这里重新设置以便后续 disruptor 使用
            saved.setPlainContent(plainContent);
            return saved;
        }
        return null;
    }

    @Override
    public Diary getDiary(String diaryId) {
        Diary diary = diaryRepository.findByDiaryId(diaryId);
        if (diary == null) {
            return null;
        }
        applyReadCrypto(diary);
        return diary;
    }

    @Override
    public Page<Diary> getDiaryList(String userId, int pageNum, int pageSize, String sortBy, boolean asc) {
        // 处理默认排序字段
        String actualSort = StrUtil.isBlank(sortBy) ? "createTime" : sortBy;

        // 构建分页请求（注意Spring Data页码从0开始）
        Sort sort = Sort.by(asc ? Sort.Direction.ASC : Sort.Direction.DESC, actualSort);
        PageRequest pageRequest = PageRequest.of(pageNum - 1, pageSize, sort);

        Example<Diary> example = Example.of(Diary.builder().userId(userId).build());
        Page<Diary> page = diaryRepository.findAll(example, pageRequest);
        if (page.hasContent()) {
            page.getContent().forEach(this::applyReadCrypto);
        }
        return page;

        // 如需带条件查询（示例）
        // return diaryRepository.findByUserId("当前用户ID", pageRequest);
    }

    @Override
    public java.util.List<Diary> getFootprints(String userId) {
        java.util.List<Diary> diaries = diaryRepository.findAllWithLocationByUserId(userId);
        diaries.forEach(this::applyReadCrypto);
        return diaries;
    }

    private void applyWriteCrypto(Diary diary, User user) {
        if (diary == null) {
            return;
        }
        if (user == null || user.getKeyMode() == null || "DEFAULT".equals(user.getKeyMode())) {
            diary.setClientEncrypted(false);
            String plain = diary.getContent();
            diary.setPlainContent(plain);
            if (StrUtil.isNotBlank(plain)) {
                diary.setContent(AesGcmCryptoUtils.encryptText(plain, cryptoService.serverAesKeyBytes()));
            }
            return;
        }
        if ("CUSTOM".equals(user.getKeyMode())) {
            diary.setClientEncrypted(true);
        }
    }

    private void applyReadCrypto(Diary diary) {
        if (diary == null) {
            return;
        }
        if (StrUtil.isBlank(diary.getContent())) {
            return;
        }
        if (Boolean.TRUE.equals(diary.getClientEncrypted())) {
            return;
        }
        try {
            diary.setContent(AesGcmCryptoUtils.decryptText(diary.getContent(), cryptoService.serverAesKeyBytes()));
        } catch (Exception e) {
            return;
        }
    }
}
