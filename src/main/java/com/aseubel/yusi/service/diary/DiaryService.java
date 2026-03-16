package com.aseubel.yusi.service.diary;

import com.aseubel.yusi.pojo.entity.Diary;
import org.springframework.data.domain.Page;

import java.util.List;

public interface DiaryService {

    Diary addDiary(Diary diary);

    Diary editDiary(Diary diary);

    Diary getDiary(String diaryId);

    Page<Diary> getDiaryList(String userId, int pageNum, int pageSize, String sortBy, boolean asc);

    String decryptDiaryContent(Diary diary);

    void evictDiaryCache(String diaryId);

    void evictListCache(String userId);

    void evictFootprintsCache(String userId);

    List<Diary> getFootprints(String userId);

    String convertImagesToUrls(String imagesJson);
}
