package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.SituationScenario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SituationScenarioRepository extends JpaRepository<SituationScenario, String> {
    List<SituationScenario> findByStatusGreaterThanEqual(Integer status);
    List<SituationScenario> findByStatus(Integer status);
    List<SituationScenario> findBySubmitterId(String submitterId);
}
