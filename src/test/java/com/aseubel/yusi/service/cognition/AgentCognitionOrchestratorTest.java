package com.aseubel.yusi.service.cognition;

import com.aseubel.yusi.pojo.dto.cognition.CognitionIngestCommand;
import com.aseubel.yusi.service.cognition.impl.AgentCognitionOrchestratorImpl;
import com.aseubel.yusi.service.lifegraph.LifeGraphCognitionBridgeService;
import com.aseubel.yusi.service.match.MatchProfileAssembler;
import com.aseubel.yusi.service.memory.MidMemoryUpdateService;
import com.aseubel.yusi.service.persona.UserPersonaUpdateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AgentCognitionOrchestratorTest {

    @Mock
    private CognitionRoutingService cognitionRoutingService;

    @Mock
    private UserPersonaUpdateService userPersonaUpdateService;

    @Mock
    private MidMemoryUpdateService midMemoryUpdateService;

    @Mock
    private LifeGraphCognitionBridgeService lifeGraphCognitionBridgeService;

    @Mock
    private MatchProfileAssembler matchProfileAssembler;

    @Mock
    private ImageUnderstandingService imageUnderstandingService;

    @Test
    void emptyDiaryIngestRemovesPreviousMemoryWithoutCallingCognition() {
        CognitionIngestCommand command = CognitionIngestCommand.builder()
                .userId("user-1")
                .sourceType("DIARY")
                .sourceId("diary-1")
                .maskedText("")
                .build();

        service().ingest(command);

        verify(midMemoryUpdateService).removeBySource("user-1", "DIARY", "diary-1");
        verifyNoInteractions(cognitionRoutingService, userPersonaUpdateService,
                lifeGraphCognitionBridgeService, matchProfileAssembler, imageUnderstandingService);
    }

    private AgentCognitionOrchestratorImpl service() {
        return new AgentCognitionOrchestratorImpl(cognitionRoutingService, userPersonaUpdateService,
                midMemoryUpdateService, lifeGraphCognitionBridgeService, matchProfileAssembler,
                imageUnderstandingService);
    }
}
