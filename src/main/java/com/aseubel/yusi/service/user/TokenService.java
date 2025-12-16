package com.aseubel.yusi.service.user;

public interface TokenService {

    void saveRefreshToken(String userId, String refreshToken);

    String getRefreshToken(String userId);

    void deleteRefreshToken(String userId);

    void addToBlacklist(String token);

    boolean isBlacklisted(String token);
}
