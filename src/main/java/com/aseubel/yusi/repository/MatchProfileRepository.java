package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.MatchProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MatchProfileRepository extends JpaRepository<MatchProfile, Long> {

    Optional<MatchProfile> findByUserId(String userId);
}
