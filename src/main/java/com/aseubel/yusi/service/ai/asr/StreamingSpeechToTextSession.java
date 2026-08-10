package com.aseubel.yusi.service.ai.asr;

import java.nio.ByteBuffer;

public interface StreamingSpeechToTextSession {

    String modelId();

    void sendAudioFrame(ByteBuffer audioFrame);

    void finish();

    void cancel();
}
