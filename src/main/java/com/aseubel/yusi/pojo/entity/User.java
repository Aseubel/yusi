package com.aseubel.yusi.pojo.entity;

import com.aseubel.yusi.common.utils.UuidUtils;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import java.util.UUID;

/**
 * @author Aseubel
 * @date 2025/5/7 上午1:04
 */
@Data
@Entity
@Builder
@Table(name = "user")
@DynamicInsert
@DynamicUpdate
@AllArgsConstructor
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "username")
    private String userName;

    @Column(name = "password")
    private String password;

    @Column(name = "email")
    private String email;

    @Column(name = "is_match_enabled")
    private Boolean isMatchEnabled = false;

    @Column(name = "match_intent")
    private String matchIntent;

    public String generateUserId() {
        this.userId = UuidUtils.genUuidSimple();
        return this.userId;
    }
}
