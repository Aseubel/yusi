package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.SituationScenario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SituationScenarioRepository extends JpaRepository<SituationScenario, String> {
}
