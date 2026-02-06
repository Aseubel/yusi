package com.aseubel.yusi.service.diary;

import com.aseubel.yusi.pojo.entity.Diary;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * @author Aseubel
 * @date 2025/5/7 上午9:57
 */
public interface DiaryService {

    Diary addDiary(Diary diary);

    Diary editDiary(Diary diary);

    Diary getDiary(String diaryId);

    Page<Diary> getDiaryList(String userId, int pageNum, int pageSize, String sortBy, boolean asc);

    void generateAiResponse(String diaryId);

    void evictDiaryCache(String diaryId);

    void evictListCache(String userId);

    void evictFootprintsCache(String userId);

    /**
     * 获取用户足迹列表（有地理位置的日记）
     */
    List<Diary> getFootprints(String userId);
}
