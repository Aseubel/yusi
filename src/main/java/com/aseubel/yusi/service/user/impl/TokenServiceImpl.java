package com.aseubel.yusi.service.user.impl;

import com.aseubel.yusi.config.JwtProperties;
import com.aseubel.yusi.redis.IRedisService;
import com.aseubel.yusi.service.user.TokenService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.aseubel.yusi.redis.RedisKey.*;

@Service
@Slf4j
public class TokenServiceImpl implements TokenService {

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private IRedisService redissonService;

    @Autowired
    private JwtProperties jwtProperties;

    @Override
    public void saveRefreshToken(String userId, String refreshToken) {
        redissonService.setValue(REFRESH_TOKEN_KEY + userId, refreshToken, jwtProperties.getRefreshTokenExpiration());
    }

    @Override
    public String getRefreshToken(String userId) {
        return redissonService.getValue(REFRESH_TOKEN_KEY + userId);
    }

    @Override
    public void deleteRefreshToken(String userId) {
        redissonService.remove(REFRESH_TOKEN_KEY + userId);
    }

    @Override
    public void addToBlacklist(String token) {
        RBucket<String> bucket = redissonClient.getBucket(BLACKLIST_KEY + token);
        // Blacklist for the duration of access token expiration (to be safe)
        bucket.set("true", jwtProperties.getAccessTokenExpiration(), TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean isBlacklisted(String token) {
        RBucket<String> bucket = redissonClient.getBucket(BLACKLIST_KEY + token);
        return bucket.isExists();
    }

    // ==================== Multi-device login management ====================

    @Override
    public void addDeviceToken(String userId, String accessToken, String deviceInfo) {
        RScoredSortedSet<String> deviceSet = redissonClient.getScoredSortedSet(DEVICE_TOKENS_KEY + userId);
        // Use current timestamp as score for ordering (oldest first)
        double score = System.currentTimeMillis();
        deviceSet.add(score, accessToken);
        // Set expiration same as access token
        deviceSet.expire(java.time.Duration.ofMillis(jwtProperties.getAccessTokenExpiration()));
        log.debug("Added device token for user {}, device info: {}", userId, deviceInfo);
    }

    @Override
    public int getActiveDeviceCount(String userId) {
        RScoredSortedSet<String> deviceSet = redissonClient.getScoredSortedSet(DEVICE_TOKENS_KEY + userId);
        return deviceSet.size();
    }

    @Override
    public void removeAllDeviceTokens(String userId) {
        RScoredSortedSet<String> deviceSet = redissonClient.getScoredSortedSet(DEVICE_TOKENS_KEY + userId);
        // Add all tokens to blacklist before removing
        Collection<String> tokens = deviceSet.readAll();
        for (String token : tokens) {
            addToBlacklist(token);
        }
        deviceSet.delete();
        log.debug("Removed all device tokens for user {}", userId);
    }

    @Override
    public void removeDeviceToken(String userId, String accessToken) {
        RScoredSortedSet<String> deviceSet = redissonClient.getScoredSortedSet(DEVICE_TOKENS_KEY + userId);
        deviceSet.remove(accessToken);
        addToBlacklist(accessToken);
        log.debug("Removed device token for user {}", userId);
    }

    @Override
    public boolean isValidDeviceToken(String userId, String accessToken) {
        RScoredSortedSet<String> deviceSet = redissonClient.getScoredSortedSet(DEVICE_TOKENS_KEY + userId);
        return deviceSet.contains(accessToken);
    }

    @Override
    public Set<String> getActiveDeviceTokens(String userId) {
        RScoredSortedSet<String> deviceSet = redissonClient.getScoredSortedSet(DEVICE_TOKENS_KEY + userId);
        return new HashSet<>(deviceSet.readAll());
    }

    @Override
    public void enforceDeviceLimit(String userId, int maxDevices) {
        RScoredSortedSet<String> deviceSet = redissonClient.getScoredSortedSet(DEVICE_TOKENS_KEY + userId);
        int currentCount = deviceSet.size();

        if (currentCount >= maxDevices) {
            // Remove oldest tokens (lowest scores = oldest timestamps)
            int tokensToRemove = currentCount - maxDevices + 1;
            Collection<String> oldestTokens = deviceSet.valueRange(0, tokensToRemove - 1);

            for (String oldToken : oldestTokens) {
                deviceSet.remove(oldToken);
                addToBlacklist(oldToken);
                log.info("Removed oldest device token for user {} due to device limit", userId);
            }
        }
    }
}
