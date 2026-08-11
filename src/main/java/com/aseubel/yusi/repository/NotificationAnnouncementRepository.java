package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.NotificationAnnouncement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationAnnouncementRepository extends JpaRepository<NotificationAnnouncement, Long> {

    Page<NotificationAnnouncement> findAllByOrderByPublishedAtDescIdDesc(Pageable pageable);
}
