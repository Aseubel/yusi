package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.ImageFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ImageFileRepository extends JpaRepository<ImageFile, Long> {

    Optional<ImageFile> findByFileMd5(String fileMd5);

    Optional<ImageFile> findByFileMd5AndUserId(String fileMd5, String userId);

    boolean existsByFileMd5(String fileMd5);
}
