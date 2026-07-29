package com.aseubel.yusi.service.match;

import com.aseubel.yusi.pojo.entity.MatchProfile;
import com.aseubel.yusi.pojo.entity.SituationScenario;
import com.aseubel.yusi.repository.SituationScenarioRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConnectionGuideServiceTest {

    private final SituationScenarioRepository scenarioRepository = mock(SituationScenarioRepository.class);
    private final ConnectionGuideService service = new ConnectionGuideService(scenarioRepository);

    @Test
    void suggestScenario_selectsApprovedScenarioRelevantToBothProfiles() {
        SituationScenario scenario = new SituationScenario("scenario-1", "夜晚散步与城市观察",
                "聊聊最近让你平静的散步时刻", "author", null,
                SituationScenario.STATUS_MANUAL_APPROVED);
        when(scenarioRepository.findByStatusGreaterThanEqual(SituationScenario.STATUS_AI_APPROVED))
                .thenReturn(List.of(scenario));

        MatchProfile profileA = MatchProfile.builder().midMemorySummary("最近常在夜晚散步，观察城市灯光").build();
        MatchProfile profileB = MatchProfile.builder().midMemorySummary("最近喜欢夜晚散步，也在寻找平静").build();

        assertEquals("夜晚散步与城市观察：聊聊最近让你平静的散步时刻",
                service.suggestScenario(profileA, profileB));
        verify(scenarioRepository).findByStatusGreaterThanEqual(SituationScenario.STATUS_AI_APPROVED);
    }

    @Test
    void suggestScenario_returnsNullWhenProfilesAreInsufficient() {
        assertNull(service.suggestScenario(null, MatchProfile.builder().midMemorySummary("近期状态").build()));
    }
}
