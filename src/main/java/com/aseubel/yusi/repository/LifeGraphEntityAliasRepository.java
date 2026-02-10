package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.LifeGraphEntityAlias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LifeGraphEntityAliasRepository extends JpaRepository<LifeGraphEntityAlias, Long> {
    Optional<LifeGraphEntityAlias> findByUserIdAndAliasNorm(String userId, String aliasNorm);
    List<LifeGraphEntityAlias> findByUserIdAndEntityId(String userId, Long entityId);
    List<LifeGraphEntityAlias> findTop200ByUserIdOrderByConfidenceDesc(String userId);
}
