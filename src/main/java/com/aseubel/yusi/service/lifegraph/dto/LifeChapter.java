package com.aseubel.yusi.service.lifegraph.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class LifeChapter {
    private String title;
    private LocalDate startDate;
    private LocalDate endDate;
    private List<String> keywords;
    private List<TimelineNode> nodes;
    private String summary;
}
