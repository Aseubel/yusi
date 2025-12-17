package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.SituationRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SituationRoomRepository extends JpaRepository<SituationRoom, String> {
}
