package com.aseubel.yusi.service.lifegraph;

import com.aseubel.yusi.pojo.dto.cognition.CognitionIngestCommand;
import com.aseubel.yusi.pojo.entity.Diary;

public interface LifeGraphBuildService {
    void upsertFromDiary(Diary diary, String plainContent);
    void upsertFromPlaza(CognitionIngestCommand command);
    void deleteByDiary(String userId, String diaryId);
    void deleteBySource(String userId, String sourceType, String sourceId);
}
