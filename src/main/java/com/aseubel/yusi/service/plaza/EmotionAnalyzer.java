package com.aseubel.yusi.service.plaza;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 情感分析AI服务
 * 用于分析用户提交内容的情感倾向
 * 
 * @author Aseubel
 */
public interface EmotionAnalyzer {

    @UserMessage("""
            请分析以下内容的主要情感倾向。

            内容：
            {{content}}

            任务：
            请从以下情感类别中选择一个最匹配的：
            - Joy (喜悦、开心、满足)
            - Sadness (悲伤、失落、遗憾)
            - Anxiety (焦虑、担忧、紧张)
            - Love (爱意、感恩、温暖)
            - Anger (愤怒、不满、烦躁)
            - Fear (恐惧、害怕、不安)
            - Hope (希望、期待、憧憬)
            - Calm (平静、淡然、释然)
            - Confusion (困惑、迷茫、纠结)
            - Neutral (中性、无明显情感)

            注意：
            1. 只返回情感类别的英文名称（如 Joy、Sadness 等）
            2. 不要返回任何其他内容
            3. 如果内容太短或无法判断，返回 Neutral
            """)
    String analyzeEmotion(@V("content") String content);
}
