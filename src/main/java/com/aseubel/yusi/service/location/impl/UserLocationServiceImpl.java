package com.aseubel.yusi.service.location.impl;

import com.aseubel.yusi.pojo.dto.location.AddLocationRequest;
import com.aseubel.yusi.pojo.dto.location.UpdateLocationRequest;
import com.aseubel.yusi.pojo.entity.UserLocation;
import com.aseubel.yusi.repository.UserLocationRepository;
import com.aseubel.yusi.service.location.UserLocationService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户地点服务实现
 * Epic 5: 时空足迹
 *
 * @author Aseubel
 * @date 2026/1/28
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserLocationServiceImpl implements UserLocationService {

    private final UserLocationRepository userLocationRepository;

    @Override
    public List<UserLocation> getUserLocations(String userId) {
        return userLocationRepository.findByUserIdOrderByCreateTimeDesc(userId);
    }

    @Override
    public List<UserLocation> getUserLocationsByType(String userId, String locationType) {
        return userLocationRepository.findByUserIdAndLocationTypeOrderByCreateTimeDesc(userId, locationType);
    }

    @Override
    @Transactional
    public UserLocation addLocation(AddLocationRequest request) {
        UserLocation location = request.toEntity();
        location.generateId();
        location.setCreateTime(LocalDateTime.now());
        location.setUpdateTime(LocalDateTime.now());

        log.info("Adding new location for user {}: {}", request.getUserId(), request.getName());
        return userLocationRepository.save(location);
    }

    @Override
    @Transactional
    public UserLocation updateLocation(UpdateLocationRequest request) {
        UserLocation existing = userLocationRepository.findByLocationId(request.getLocationId())
                .orElseThrow(() -> new IllegalArgumentException("地点不存在"));

        if (!existing.getUserId().equals(request.getUserId())) {
            throw new IllegalArgumentException("无权限修改此地点");
        }

        if (request.getName() != null) {
            existing.setName(request.getName());
        }
        if (request.getLatitude() != null) {
            existing.setLatitude(request.getLatitude());
        }
        if (request.getLongitude() != null) {
            existing.setLongitude(request.getLongitude());
        }
        if (request.getAddress() != null) {
            existing.setAddress(request.getAddress());
        }
        if (request.getPlaceId() != null) {
            existing.setPlaceId(request.getPlaceId());
        }
        if (request.getLocationType() != null) {
            existing.setLocationType(request.getLocationType());
        }
        if (request.getIcon() != null) {
            existing.setIcon(request.getIcon());
        }
        existing.setUpdateTime(LocalDateTime.now());

        log.info("Updating location {} for user {}", request.getLocationId(), request.getUserId());
        return userLocationRepository.save(existing);
    }

    @Override
    @Transactional
    public void deleteLocation(String userId, String locationId) {
        if (!userLocationRepository.existsByLocationIdAndUserId(locationId, userId)) {
            throw new IllegalArgumentException("地点不存在或无权限删除");
        }

        log.info("Deleting location {} for user {}", locationId, userId);
        userLocationRepository.deleteByLocationId(locationId);
    }
}
