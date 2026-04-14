package com.aseubel.yusi.pojo.dto.cognition;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CognitionRoutingResult {

    private String preferredName;
    private String location;
    private String interests;
    private String tone;
    private String customInstructions;
    private String midMemorySummary;
    private Double midMemoryImportance;
}
