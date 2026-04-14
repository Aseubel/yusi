package com.aseubel.yusi.pojo.dto.cognition;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CognitionIngestCommand {

    private String userId;
    private String sourceType;
    private String sourceId;
    private String maskedText;
    private String title;
    private String topic;
    private String placeName;
    private LocalDateTime timestamp;
    private Double confidenceHint;
}
