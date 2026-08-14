package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.ProductEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductEventRepository extends JpaRepository<ProductEvent, Long> {

    Optional<ProductEvent> findByEventId(String eventId);

    Optional<ProductEvent> findByIdempotencyKey(String idempotencyKey);
}
