package com.aseubel.yusi.service.ai.asr;

public interface StreamingSpeechToTextClient {

    String modelId();

    StreamingSpeechToTextSession start(StreamingSpeechToTextListener listener);
}
