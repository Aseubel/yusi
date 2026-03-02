package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.UserPersona;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserPersonaRepository extends JpaRepository<UserPersona, Long> {
    Optional<UserPersona> findByUserId(String userId);
}
