package com.aseubel.yusi.service.memory;

public interface MidMemoryUpdateService {

    void appendSnapshot(String userId, String summary, Double importance);

    void appendSnapshot(String userId, String summary, Double importance, String category);
}
