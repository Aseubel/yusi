package com.aseubel.yusi.service.ai.asr;

public interface StreamingSpeechToTextListener {

    void onEvent(StreamingTranscriptionEvent event);

    void onComplete();

    void onError(Exception exception);
}
