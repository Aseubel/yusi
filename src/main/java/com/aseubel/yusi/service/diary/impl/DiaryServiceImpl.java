package com.aseubel.yusi.service.diary.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.repository.DiaryRepository;
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
    @Lazy
    private DiaryService self;

    @Override
    public Diary addDiary(Diary diary) {
        diary.generateId();
        diary.setCreateTime(LocalDateTime.now());
        diary.setUpdateTime(LocalDateTime.now());
        Diary saved = diaryRepository.save(diary);
        
        // 异步生成AI回应 (通过self调用以触发AOP)
        // self.generateAiResponse(saved.getDiaryId());
        
        return saved;
    }

    @Async
    @Override
    public void generateAiResponse(String diaryId) {
        try {
            Diary diary = diaryRepository.findByDiaryId(diaryId);
            if (diary == null) return;

            log.info("Generating AI response for diary: {}", diaryId);
            
            CompletableFuture<String> future = new CompletableFuture<>();
            StringBuilder sb = new StringBuilder();
            
            diaryAssistant.generateDiaryResponse(diary.getContent(), diary.getEntryDate().toString())
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

    @Override
    public Diary editDiary(Diary diary) {
        Diary existingDiary = diaryRepository.findByDiaryId(diary.getDiaryId());
        if (ObjectUtil.isNotEmpty(existingDiary)) {
            diary.setId(existingDiary.getId());
            diary.setUpdateTime(LocalDateTime.now());
            diary.setStatus(0);
            diary.setAiResponse(null);
            return diaryRepository.save(diary);
        }
        return null;
    }

    @Override
    public Diary getDiary(String diaryId) {
        return diaryRepository.findByDiaryId(diaryId);
    }

    @Override
    public Page<Diary> getDiaryList(String userId, int pageNum, int pageSize, String sortBy, boolean asc) {
        // 处理默认排序字段
        String actualSort = StrUtil.isBlank(sortBy) ? "createTime" : sortBy;

        // 构建分页请求（注意Spring Data页码从0开始）
        Sort sort = Sort.by(asc ? Sort.Direction.ASC : Sort.Direction.DESC, actualSort);
        PageRequest pageRequest = PageRequest.of(pageNum - 1, pageSize, sort);

        Example<Diary> example = Example.of(Diary.builder().userId(userId).build());
        return diaryRepository.findAll(example, pageRequest);

        // 如需带条件查询（示例）
        // return diaryRepository.findByUserId("当前用户ID", pageRequest);
    }
}
