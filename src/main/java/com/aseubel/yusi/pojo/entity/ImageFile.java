package com.aseubel.yusi.pojo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Entity
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "image_file", indexes = {
    @Index(name = "idx_file_md5", columnList = "fileMd5"),
    @Index(name = "idx_user_id", columnList = "userId")
})
public class ImageFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_md5", nullable = false, length = 64)
    private String fileMd5;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "content_type", length = 64)
    private String contentType;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "create_time")
    private LocalDateTime createTime;
}
