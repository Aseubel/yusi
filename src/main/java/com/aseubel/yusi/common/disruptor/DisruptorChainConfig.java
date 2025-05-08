package com.aseubel.yusi.common.disruptor;

import com.aseubel.yusi.common.repochain.ProcessorChain;
import com.aseubel.yusi.service.ai.EmbeddingService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Aseubel
 * @date 2025/5/7 下午4:44
 */
@Configuration
public class DisruptorChainConfig {

    @Bean(name = "disruptorChain")
    public ProcessorChain<Element> disruptorChain() {
        ProcessorChain<Element> processorChain = new ProcessorChain<>();
        processorChain.addProcessor(new EmbeddingService());
        return processorChain;
    }
}
