package com.aseubel.yusi.service.room;

import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface SituationRoomAgent {
    @UserMessage("""
        场景描述：{{scenario}}
        
        用户回答数据：
        {{userAnswers}}
        """)
    TokenStream analyze(@V("scenario") String scenario, @V("userAnswers") String userAnswersJson);

    @UserMessage("""
        场景描述：{{scenario}}
        
        用户回答数据：
        {{userAnswers}}
        """)
    String analyzeReport(@V("scenario") String scenario, @V("userAnswers") String userAnswersJson);
}
