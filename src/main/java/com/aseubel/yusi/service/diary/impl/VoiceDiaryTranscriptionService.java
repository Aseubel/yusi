package com.aseubel.yusi.service.diary.impl;

import com.aseubel.yusi.service.ai.asr.SpeechModelRegistry;
import com.aseubel.yusi.service.diary.VoiceTranscriptionService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class VoiceDiaryTranscriptionService implements VoiceTranscriptionService {

    private final SpeechModelRegistry speechModelRegistry;

    public VoiceDiaryTranscriptionService(SpeechModelRegistry speechModelRegistry) {
        this.speechModelRegistry = speechModelRegistry;
    }

    @Override
    public String transcribe(MultipartFile audio) {
        return speechModelRegistry.transcribe(audio).text();
    }
}
