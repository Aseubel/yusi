package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.SoulConnectionEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SoulConnectionEventRepository extends JpaRepository<SoulConnectionEvent, Long> {

    Optional<SoulConnectionEvent> findByEventId(String eventId);

    List<SoulConnectionEvent> findByConnectionIdOrderByOccurredAtAscIdAsc(Long connectionId);
}
