package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.Diary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * @author Aseubel
 * @date 2025/5/7 上午9:51
 */
@Repository
public interface DiaryRepository extends JpaRepository<Diary, Long> {

    Diary findByDiaryId(String diaryId);
}
