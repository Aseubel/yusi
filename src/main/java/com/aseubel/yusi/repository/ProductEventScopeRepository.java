package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.ProductEventScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductEventScopeRepository extends JpaRepository<ProductEventScope, Long> {

    boolean existsByEventIdAndUserId(String eventId, String userId);
}
