package com.aseubel.yusi.service.ai.asr;

public record StreamingTranscriptionEvent(String text, boolean sentenceEnd, Long sentenceId) {
}
