package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.Diary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @author Aseubel
 * @date 2025/5/7 上午9:51
 */
@Repository
public interface DiaryRepository extends JpaRepository<Diary, Long> {

    Diary findByDiaryId(String diaryId);

    @Query("SELECT d FROM Diary d WHERE d.userId = :userId")
    Page<Diary> findByUserId(@Param("userId") String userId, Pageable pageable);

    List<Diary> findTop3ByUserIdOrderByCreateTimeDesc(String userId);

    /**
     * 获取用户的所有日记（用于密钥更换时全量转换）
     */
    List<Diary> findAllByUserId(String userId);

    /**
     * 获取用户所有有地理位置的日记（用于足迹地图）
     */
    @Query("SELECT d FROM Diary d WHERE d.userId = :userId AND d.latitude IS NOT NULL AND d.longitude IS NOT NULL ORDER BY d.createTime DESC")
    List<Diary> findAllWithLocationByUserId(@Param("userId") String userId);
}
