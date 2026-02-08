package com.aseubel.yusi.service.lifegraph;

import com.aseubel.yusi.pojo.entity.Diary;

public interface LifeGraphBuildService {
    void upsertFromDiary(Diary diary, String plainContent);
    void deleteByDiary(String userId, String diaryId);
}
