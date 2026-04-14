package com.aseubel.yusi.service.cognition;

import com.aseubel.yusi.common.event.ChatCognitionIngestEvent;
import com.aseubel.yusi.common.event.DiaryCognitionIngestEvent;
import com.aseubel.yusi.common.event.EmotionPlazaCognitionIngestEvent;
import com.aseubel.yusi.pojo.dto.cognition.CognitionIngestCommand;

public interface AgentCognitionOrchestrator {

    void ingest(CognitionIngestCommand command);

    void onDiaryCognitionIngest(DiaryCognitionIngestEvent event);

    void onChatCognitionIngest(ChatCognitionIngestEvent event);

    void onEmotionPlazaCognitionIngest(EmotionPlazaCognitionIngestEvent event);
}
