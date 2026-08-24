package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.WebAccessPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WebAccessPolicyRepository extends JpaRepository<WebAccessPolicy, Long> {
}
