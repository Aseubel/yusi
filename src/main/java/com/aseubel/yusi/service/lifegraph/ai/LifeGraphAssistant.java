package com.aseubel.yusi.service.lifegraph.ai;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface LifeGraphAssistant {

    @UserMessage("""
            请从以下用户问题中提取关键实体名称（人名、地名、事件名等），用于在知识图谱中检索。
            只输出关键词，用逗号分隔，不要输出任何其他解释性文字。
            如果问题中包含"我"、"自己"等指代词，请忽略，不要作为关键词。
            
            问题：{{query}}
            """)
    String extractKeywords(@V("query") String query);

    @UserMessage("""
            {{prompt}}
            
            === 相关人生图谱信息 ===
            {{context}}
            ======================
            
            基于以上图谱信息，回答用户的问题。如果图谱信息不足以回答，请说明。
            
            用户问题：{{query}}
            """)
    String answer(@V("prompt") String prompt, @V("context") String context, @V("query") String query);

    @UserMessage("""
            {{prompt}}
            
            候选实体对列表（JSON）：
            {{candidates}}
            """)
    String analyzeMergeCandidates(@V("prompt") String prompt, @V("candidates") String candidatesJson);
}
