package com.aseubel.yusi.service.ai.asr;

import org.springframework.web.multipart.MultipartFile;

public interface SpeechToTextClient {

    String modelId();

    TranscriptionResult transcribe(MultipartFile audio);
}
