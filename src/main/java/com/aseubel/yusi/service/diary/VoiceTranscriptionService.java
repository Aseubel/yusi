package com.aseubel.yusi.service.diary;

import org.springframework.web.multipart.MultipartFile;

public interface VoiceTranscriptionService {
    String transcribe(MultipartFile audio);
}
