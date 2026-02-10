package com.aseubel.yusi.service.lifegraph.ai;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface LifeGraphExtractor {

    @UserMessage("""
            {{prompt}}

            已知实体库（规范名/别名，供消歧映射使用）：
            {{knownEntities}}

            日记元信息：
            - 日期：{{entryDate}}
            - 标题：{{title}}
            - 地点名称：{{placeName}}
            - 地址：{{address}}
            - 坐标：{{coordinates}}

            日记内容：
            {{content}}
            """)
    String extract(@V("prompt") String prompt,
            @V("knownEntities") String knownEntities,
            @V("entryDate") String entryDate,
            @V("title") String title,
            @V("placeName") String placeName,
            @V("address") String address,
            @V("coordinates") String coordinates,
            @V("content") String content);
}
