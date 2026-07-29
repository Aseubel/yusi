package com.aseubel.yusi.service.diary;

import org.springframework.web.multipart.MultipartFile;

public interface SpeechToTextClient {

    String modelId();

    TranscriptionResult transcribe(MultipartFile audio);
}
