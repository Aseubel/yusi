package com.aseubel.yusi.service.ai;

import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.service.lifegraph.LifeGraphQueryService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemorySearchTool {

    private final DiarySearchTool diarySearchTool;
    private final LifeGraphQueryService lifeGraphQueryService;

    @Tool(name = "searchMemories", value = """
            统一的“记忆检索”工具：内部会结合图谱检索（精准事实/关系路径）与向量检索（日记片段）并进行合并排序。

            参数说明：
            - query: 用户的问题或要检索的主题（必填）
            - startDate: 日期范围开始，格式 YYYY-MM-DD（可选）
            - endDate: 日期范围结束，格式 YYYY-MM-DD（可选）

            返回：
            - GRAPH: 图谱实体/关系/证据
            - DIARY: 向量检索出的日记片段
            """)
    public String searchMemories(
            @P("用户问题或检索主题") String query,
            @P("开始日期，格式 YYYY-MM-DD，不指定时间范围则传 null") String startDate,
            @P("结束日期，格式 YYYY-MM-DD，不指定时间范围则传 null") String endDate) {

        String userId = UserContext.getUserId();
        if (StrUtil.isBlank(userId)) {
            return "无法验证用户身份，请重新登录后再试。";
        }
        if (StrUtil.isBlank(query)) {
            return "query 不能为空。";
        }

        String graph = lifeGraphQueryService.localSearch(userId, query, 5, 20, 10);
        List<String> diary = diarySearchTool.searchDiary(query, startDate, endDate);

        StringBuilder sb = new StringBuilder();
        if (StrUtil.isNotBlank(graph)) {
            sb.append("GRAPH:\n").append(graph).append("\n");
        } else {
            sb.append("GRAPH:\n").append("无匹配图谱结果。\n\n");
        }

        sb.append("DIARY:\n");
        for (String s : diary) {
            sb.append("- ").append(s).append("\n");
        }

        log.info("MemorySearchTool: userId={}, graphLen={}, diaryCount={}", userId, graph.length(), diary.size());
        return sb.toString();
    }
}
