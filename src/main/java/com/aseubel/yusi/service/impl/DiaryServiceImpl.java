package com.aseubel.yusi.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.repository.DiaryRepository;
import com.aseubel.yusi.service.DiaryService;
import com.aseubel.yusi.service.ai.Assistant;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author Aseubel
 * @date 2025/5/7 上午9:57
 */
@Service
public class DiaryServiceImpl implements DiaryService {

    @Autowired
    private DiaryRepository diaryRepository;

    @Resource
    private Assistant diaryAssistant;

    @Override
    public Diary addDiary(Diary diary) {
        diary.generateId();
        return diaryRepository.save(diary);
    }

    @Override
    public Diary editDiary(Diary diary) {
        Diary existingDiary = diaryRepository.findByDiaryId(diary.getDiaryId());
        if (ObjectUtil.isNotEmpty(existingDiary)) {
            diary.setId(existingDiary.getId());
            diaryRepository.save(existingDiary);
        }
        return null;
    }

    @Override
    public Diary getDiary(String diaryId) {
        return diaryRepository.findByDiaryId(diaryId);
    }

    @Override
    public String chatWithDiary(String userId, String query) {
        return diaryAssistant.chat(userId, query);
    }
}
