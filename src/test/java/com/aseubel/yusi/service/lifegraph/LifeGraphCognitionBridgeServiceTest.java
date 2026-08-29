package com.aseubel.yusi.service.lifegraph;

import com.aseubel.yusi.pojo.dto.cognition.CognitionIngestCommand;
import com.aseubel.yusi.pojo.dto.cognition.CognitionRoutingResult;
import com.aseubel.yusi.service.lifegraph.impl.LifeGraphCognitionBridgeServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LifeGraphCognitionBridgeServiceTest {

    @Mock
    private LifeGraphTaskCreator lifeGraphTaskCreator;

    @Test
    void bridgeOnlyDelegatesDispatchAndNeverTouchesLongTermGraph() {
        // 桥接层只应把任务派发委托给任务创建器，自身不得有任何图谱写入依赖
        LifeGraphCognitionBridgeServiceImpl service =
                new LifeGraphCognitionBridgeServiceImpl(lifeGraphTaskCreator);
        CognitionIngestCommand command = CognitionIngestCommand.builder()
                .userId("user-1")
                .sourceType("EMOTION_PLAZA")
                .sourceId("card-1")
                .topic("Joy")
                .placeName("广州")
                .maskedText("最近喜欢看电影")
                .build();

        service.bridge(command, CognitionRoutingResult.builder().interests("看电影").build());

        verify(lifeGraphTaskCreator).dispatchFromCognition(same(command));
    }

    @Test
    void bridgeToleratesNullRoutingResult() {
        // 派发决策只依据来源与内容，不依赖认知路由结果（图谱抽取独立于记忆路由）
        LifeGraphCognitionBridgeServiceImpl service =
                new LifeGraphCognitionBridgeServiceImpl(lifeGraphTaskCreator);
        CognitionIngestCommand command = CognitionIngestCommand.builder()
                .userId("user-1")
                .sourceType("DIARY")
                .sourceId("diary-1")
                .maskedText("今天去爬了山")
                .build();

        service.bridge(command, null);

        verify(lifeGraphTaskCreator).dispatchFromCognition(same(command));
    }
}
