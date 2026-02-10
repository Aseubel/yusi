package com.aseubel.yusi.service.lifegraph.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class TimelineNode {
    private Long entityId;
    private String title;
    private LocalDate date;
    private String summary;
    private double importance; // 0.0 - 1.0
    private String emotion;
    private List<String> relatedPeople;
}
