package com.aseubel.yusi.pojo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * @author Aseubel
 * @date 2025/5/7 上午9:42
 */
@Data
@Entity
@Builder
@Table(name = "diary")
@DynamicInsert
@DynamicUpdate
@AllArgsConstructor
@NoArgsConstructor
public class Diary {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "diary_id")
    private String diaryId;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "title")
    private String title;

    @Column(name = "content")
    private String content;

    @Column(name = "visibility")
    private Boolean visibility;

    @Column(name = "entry_date")
    private LocalDate entryDate;

    @Column(name = "ai_analysis_status")
    private Integer status;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    public String generateId() {
        this.diaryId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return this.diaryId;
    }
}
