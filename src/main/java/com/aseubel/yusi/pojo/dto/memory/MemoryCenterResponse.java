package com.aseubel.yusi.pojo.dto.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryCenterResponse {

    private List<MemoryCenterItem> memories;
    private long activeCount;
    private long hiddenCount;
    private long forgottenCount;
    private long matchableCount;
}
