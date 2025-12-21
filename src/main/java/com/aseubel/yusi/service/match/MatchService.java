package com.aseubel.yusi.service.match;

import com.aseubel.yusi.pojo.entity.SoulMatch;
import java.util.List;

/**
 * @author Aseubel
 * @date 2025/12/21
 */
public interface MatchService {

    /**
     * Trigger daily matching process.
     * Can be called by a scheduler or manually for testing.
     */
    void runDailyMatching();

    /**
     * Get matches for a user (either A or B).
     */
    List<SoulMatch> getMatches(String userId);

    /**
     * Handle user action (Interested / Skip).
     * @param userId Current user ID
     * @param matchId Match ID
     * @param action 1: Interested, 2: Skipped
     */
    SoulMatch handleMatchAction(String userId, Long matchId, Integer action);
}
