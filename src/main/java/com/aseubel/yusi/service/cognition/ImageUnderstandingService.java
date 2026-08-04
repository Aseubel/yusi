package com.aseubel.yusi.service.cognition;

import java.util.List;

public interface ImageUnderstandingService {
    String describe(String userId, List<String> imageObjectKeys);
}
