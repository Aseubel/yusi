package com.aseubel.yusi.pojo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Table(name = "situation_scenario")
@AllArgsConstructor
@NoArgsConstructor
public class SituationScenario {

    @Id
    @Column(length = 32)
    private String id;

    @Column(length = 100)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;
}