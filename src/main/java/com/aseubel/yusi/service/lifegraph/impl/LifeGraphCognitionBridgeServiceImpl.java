package com.aseubel.yusi.service.lifegraph.impl;

import com.aseubel.yusi.pojo.dto.cognition.CognitionIngestCommand;
import com.aseubel.yusi.pojo.dto.cognition.CognitionRoutingResult;
import com.aseubel.yusi.service.lifegraph.LifeGraphCognitionBridgeService;
import com.aseubel.yusi.service.lifegraph.LifeGraphTaskCreator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LifeGraphCognitionBridgeServiceImpl implements LifeGraphCognitionBridgeService {

    private final LifeGraphTaskCreator lifeGraphTaskCreator;

    /**
     * 认知管道 → 图谱链路的统一分叉点。
     *
     * <p>这里只负责"派发任务"，绝不直接做抽取（extract）或晋升（promote）：
     * 1. 抽取是 LLM 调用，高延迟易熔断，必须由任务账本异步驱动，失败独立重试，
     *    不能拖累认知管道的记忆写入；
     * 2. 长期图谱写入必须走证据抽取器 + 晋升准入硬校验，
     *    认知路由结果（CognitionRoutingResult）不得直写图谱——这是本桥接层存在的边界。
     */
    @Override
    public void bridge(CognitionIngestCommand command, CognitionRoutingResult routingResult) {
        lifeGraphTaskCreator.dispatchFromCognition(command);
    }
}
