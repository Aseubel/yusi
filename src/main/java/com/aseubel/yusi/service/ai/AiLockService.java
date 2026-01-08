package com.aseubel.yusi.service.ai;

/**
 * Service for managing AI request locks per user
 * Ensures only one AI request can be processed at a time per user
 */
public interface AiLockService {

    /**
     * Attempt to acquire a lock for the user's AI request
     * 
     * @param userId the user ID
     * @return true if lock acquired, false if already locked
     */
    boolean tryAcquireLock(String userId);

    /**
     * Release the lock for the user's AI request
     * 
     * @param userId the user ID
     */
    void releaseLock(String userId);

    /**
     * Check if the user currently has an active AI request
     * 
     * @param userId the user ID
     * @return true if a request is in progress
     */
    boolean isLocked(String userId);
}
