package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.SoulConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SoulConnectionRepository extends JpaRepository<SoulConnection, Long> {

    Optional<SoulConnection> findByMatchId(Long matchId);
}
