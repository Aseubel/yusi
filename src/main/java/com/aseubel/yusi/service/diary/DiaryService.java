package com.aseubel.yusi.service.diary;

import com.aseubel.yusi.pojo.entity.Diary;
import org.springframework.data.domain.Page;

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
}
