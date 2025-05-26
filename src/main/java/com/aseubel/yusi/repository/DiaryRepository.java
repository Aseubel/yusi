package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.Diary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * @author Aseubel
 * @date 2025/5/7 上午9:51
 */
@Repository
public interface DiaryRepository extends JpaRepository<Diary, Long> {

    Diary findByDiaryId(String diaryId);

    @Query("SELECT d FROM Diary d WHERE d.userId = :userId")
    Page<Diary> findByUserId(@Param("userId") String userId, Pageable pageable);
}
