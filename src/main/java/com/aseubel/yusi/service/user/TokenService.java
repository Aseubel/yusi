package com.aseubel.yusi.service.user;

import java.util.Set;

public interface TokenService {

    void saveRefreshToken(String userId, String refreshToken);

    String getRefreshToken(String userId);

    void deleteRefreshToken(String userId);

    void addToBlacklist(String token);

    boolean isBlacklisted(String token);

    // Multi-device login management (max 3 devices)

    /**
     * Add a device token for a user
     * 
     * @param userId      the user ID
     * @param accessToken the access token
     * @param deviceInfo  optional device information
     */
    void addDeviceToken(String userId, String accessToken, String deviceInfo);

    /**
     * Get the count of active devices for a user
     */
    int getActiveDeviceCount(String userId);

    /**
     * Remove all device tokens for a user (on logout all)
     */
    void removeAllDeviceTokens(String userId);

    /**
     * Remove a specific device token
     */
    void removeDeviceToken(String userId, String accessToken);

    /**
     * Check if a token is a valid device token for the user
     */
    boolean isValidDeviceToken(String userId, String accessToken);

    /**
     * Get all active device tokens for a user
     */
    Set<String> getActiveDeviceTokens(String userId);

    /**
     * Enforce device limit - removes oldest tokens if limit exceeded
     * 
     * @param userId     the user ID
     * @param maxDevices maximum allowed devices
     */
    void enforceDeviceLimit(String userId, int maxDevices);
}
