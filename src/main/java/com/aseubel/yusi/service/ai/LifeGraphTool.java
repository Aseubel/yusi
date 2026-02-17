package com.aseubel.yusi.service.ai;

import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.service.lifegraph.LifeGraphQueryService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 人生图谱搜索工具 - 用于 Chat Agent 调用
 * 
 * 允许 LLM 从用户的个性化知识图谱中检索实体关系信息，用于回答关于人际关系、事件关联等问题。
 * 
 * @author Aseubel
 * @date 2026/02/10
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LifeGraphTool {

    private final LifeGraphQueryService queryService;

    @Tool(name = "searchLifeGraph", value = """
            搜索用户的人生知识图谱（Life Graph）。
            当用户询问关于具体人物（如"小明"）、地点、事件之间的关系，或者需要了解某些实体的背景信息时使用此工具。
            
            该工具会返回与查询词最相关的实体、它们之间的关系（如 RELATED_TO, HAPPENED_AT）以及原文提及片段。
            返回格式为结构化的文本，请基于这些信息回答用户问题。
            """)
    public String searchLifeGraph(
            @ToolMemoryId String memoryId,
            @P("搜索查询词，通常是人名、地名或事件名") String query) {
        
        String userId = memoryId;
        if (StrUtil.isBlank(userId)) {
            return "无法验证用户身份。";
        }
        
        if (StrUtil.isBlank(query)) {
            return "查询词不能为空。";
        }

        log.info("LifeGraphTool: 用户 {} 搜索图谱, query='{}'", userId, query);

        // 使用默认的搜索参数：Top 3 实体，每个实体 30 条关系，5 条提及
        // 这些参数在性能和上下文窗口大小之间取得了平衡
        try {
            String result = queryService.localSearch(userId, query, 3, 30, 5);
            if (StrUtil.isBlank(result)) {
                return "在人生图谱中未找到关于 '" + query + "' 的相关信息。";
            }
            return result;
        } catch (Exception e) {
            log.error("LifeGraphTool: 搜索出错", e);
            return "搜索图谱时发生错误。";
        }
    }
}
