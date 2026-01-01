package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.SoulCard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SoulCardRepository extends JpaRepository<SoulCard, Long> {

    // Find cards not created by the user, for the feed
    Page<SoulCard> findByUserIdNotOrderByCreatedAtDesc(String userId, Pageable pageable);

    // Find cards by user for "My Posts" if needed later
    Page<SoulCard> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
}
