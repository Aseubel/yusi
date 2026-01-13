package com.aseubel.yusi.pojo.entity;

import com.aseubel.yusi.common.utils.UuidUtils;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import jakarta.persistence.*;
import lombok.*;
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
@ToString
@Table(name = "diary")
@DynamicInsert
@DynamicUpdate
@AllArgsConstructor
@NoArgsConstructor
public class Diary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "diary_id")
    private String diaryId;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "title")
    private String title;

    /**
     * 日记内容（前端加密后的密文）
     * 注意：v2.0 起，加密工作由前端完成，服务端仅存储密文
     */
    @Column(name = "content")
    private String content;

    /**
     * 是否为客户端加密内容
     * true: 内容已由前端加密（v2.0+）
     * false/null: 旧版服务端加密内容
     */
    @Column(name = "client_encrypted")
    private Boolean clientEncrypted;

    @Column(name = "visibility")
    private Boolean visibility;

    @Column(name = "entry_date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    @JsonSerialize(using = LocalDateSerializer.class)
    @JsonDeserialize(using = LocalDateDeserializer.class)
    private LocalDate entryDate;

    @Column(name = "ai_analysis_status")
    private Integer status;

    @Column(name = "ai_response", length = 1000)
    private String aiResponse;

    @Column(name = "create_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime createTime;

    @Column(name = "update_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime updateTime;

    public String generateId() {
        this.diaryId = UuidUtils.genUuidSimple();
        return this.diaryId;
    }
}
