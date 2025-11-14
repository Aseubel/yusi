package com.aseubel.yusi.situation;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SituationScenario {
    private String id;
    private String title;
    private String description;
}