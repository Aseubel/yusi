package com.aseubel.yusi.service.impl;

import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.repository.DiaryRepository;
import com.aseubel.yusi.service.DiaryService;
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

    @Override
    public Diary addDiary(Diary diary) {
        diary.generateId();
        return diaryRepository.save(diary);
    }
}
