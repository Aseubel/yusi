package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.UserLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 用户地点仓储层
 * Epic 5: 时空足迹
 *
 * @author Aseubel
 * @date 2026/1/28
 */
@Repository
public interface UserLocationRepository extends JpaRepository<UserLocation, Long> {

    /**
     * 获取用户所有保存的地点
     */
    List<UserLocation> findByUserIdOrderByCreateTimeDesc(String userId);

    /**
     * 根据地点类型获取用户地点
     */
    List<UserLocation> findByUserIdAndLocationTypeOrderByCreateTimeDesc(String userId, String locationType);

    /**
     * 根据 locationId 查找
     */
    Optional<UserLocation> findByLocationId(String locationId);

    /**
     * 根据 locationId 删除
     */
    void deleteByLocationId(String locationId);

    /**
     * 检查用户是否拥有该地点
     */
    boolean existsByLocationIdAndUserId(String locationId, String userId);
}
