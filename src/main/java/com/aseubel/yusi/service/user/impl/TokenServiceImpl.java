package com.aseubel.yusi.service.user.impl;

import com.aseubel.yusi.config.JwtProperties;
import com.aseubel.yusi.redis.IRedisService;
import com.aseubel.yusi.service.user.TokenService;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class TokenServiceImpl implements TokenService {

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private IRedisService redissonService;

    @Autowired
    private JwtProperties jwtProperties;

    private static final String REFRESH_TOKEN_KEY = "auth:refresh:";
    private static final String BLACKLIST_KEY = "auth:blacklist:";

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
}
