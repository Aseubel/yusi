package com.aseubel.yusi.pojo.entity;

import com.aseubel.yusi.config.jpa.SituationConverters;
import com.aseubel.yusi.pojo.dto.situation.SituationReport;
import com.aseubel.yusi.pojo.contant.RoomStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.*;

@Data
@Builder
@Entity
@Table(name = "situation_room")
@NoArgsConstructor
@AllArgsConstructor
public class SituationRoom {

    @Id
    @Column(length = 32)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private RoomStatus status;

    @Column(name = "owner_id")
    private String ownerId;

    @Column(name = "scenario_id")
    private String scenarioId;

    @Convert(converter = SituationConverters.StringSetConverter.class)
    @Column(columnDefinition = "TEXT")
    private Set<String> members;

    @Convert(converter = SituationConverters.StringMapConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<String, String> submissions;

    @Convert(converter = SituationConverters.BooleanMapConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<String, Boolean> submissionVisibility;

    @Convert(converter = SituationConverters.StringSetConverter.class)
    @Column(columnDefinition = "TEXT")
    private Set<String> cancelVotes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Convert(converter = SituationConverters.SituationReportConverter.class)
    @Column(columnDefinition = "TEXT")
    private SituationReport report;

    @Transient
    private Map<String, String> memberNames;

    @Transient
    private com.aseubel.yusi.pojo.entity.SituationScenario scenario;

    public boolean allSubmitted() {
        return members != null && submissions != null && submissions.size() == members.size() && members.size() >= 2;
    }
}