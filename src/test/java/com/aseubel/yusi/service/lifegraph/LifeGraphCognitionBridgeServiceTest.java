package com.aseubel.yusi.service.lifegraph;

import com.aseubel.yusi.pojo.dto.cognition.CognitionIngestCommand;
import com.aseubel.yusi.pojo.dto.cognition.CognitionRoutingResult;
import com.aseubel.yusi.service.lifegraph.impl.LifeGraphCognitionBridgeServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class LifeGraphCognitionBridgeServiceTest {

    @Test
    void cognitionRoutingDoesNotWriteLongTermLifeGraphEntities() {
        LifeGraphCognitionBridgeServiceImpl service = new LifeGraphCognitionBridgeServiceImpl();
        CognitionIngestCommand command = CognitionIngestCommand.builder()
                .userId("user-1")
                .sourceType("EMOTION_PLAZA")
                .sourceId("card-1")
                .topic("Joy")
                .placeName("广州")
                .maskedText("最近喜欢看电影")
                .build();

        service.bridge(command, CognitionRoutingResult.builder().interests("看电影").build());

    }
}
