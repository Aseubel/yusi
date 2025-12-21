package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.SituationRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SituationRoomRepository extends JpaRepository<SituationRoom, String> {
    @Query(value = "SELECT * FROM situation_room WHERE members LIKE CONCAT('%\"', :userId, '\"%') ORDER BY created_at DESC", nativeQuery = true)
    List<SituationRoom> findByMembersContainingOrderByCreatedAtDesc(@Param("userId") String userId);
}
