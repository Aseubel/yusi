package com.aseubel.yusi.config.ai;

import com.aseubel.yusi.service.lifegraph.ai.LifeGraphExtractor;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

@Configuration
@RequiredArgsConstructor
public class LifeGraphAiConfig {

    @Bean
    LifeGraphExtractor lifeGraphExtractor(@Qualifier("jsonChatModel") ChatModel jsonChatModel) {
        return AiServices.builder(LifeGraphExtractor.class)
                .chatModel(jsonChatModel)
                .build();
    }

}
