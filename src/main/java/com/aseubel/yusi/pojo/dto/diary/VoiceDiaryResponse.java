package com.aseubel.yusi.pojo.dto.diary;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class VoiceDiaryResponse {
    String transcript;
}
