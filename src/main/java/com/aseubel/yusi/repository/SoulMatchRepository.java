package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.SoulMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @author Aseubel
 * @date 2025/12/21
 */
@Repository
public interface SoulMatchRepository extends JpaRepository<SoulMatch, Long> {

    // Find matches where user is A
    List<SoulMatch> findByUserAId(String userAId);

    // Find matches where user is B
    List<SoulMatch> findByUserBId(String userBId);

    // Find specific match pair (bidirectional check might be needed in service, but here specific)
    SoulMatch findByUserAIdAndUserBId(String userAId, String userBId);
    
    @Query("SELECT s FROM SoulMatch s WHERE (s.userAId = ?1 OR s.userBId = ?1) AND s.isMatched = true")
    List<SoulMatch> findSuccessfulMatches(String userId);
}
