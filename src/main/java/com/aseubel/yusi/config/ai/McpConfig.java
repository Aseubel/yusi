package com.aseubel.yusi.config.ai;

import com.aseubel.yusi.pojo.constant.AgentToolConstants;
import com.aseubel.yusi.service.ai.tool.AgentToolCapabilityCatalog;
import com.aseubel.yusi.service.ai.tool.AgentToolExecutionPolicyService;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.service.tool.ToolProvider;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * MCP (Model Context Protocol) 配置类
 * 
 * 通过内部 MCP 入口连接到 Go 实现的 MCP Server，获取部署内部工具（如 web_search）。
 * 这使得 DiaryAssistant 可以访问实时网络搜索等能力。
 * 
 * 使用 Streamable HTTP 传输协议（推荐）：
 * - 单一 POST 端点 (/internal/mcp)
 * - 响应为 SSE 流
 * - 无状态，每次请求独立
 * 
 * 配置项：
 * - mcp.enabled: 是否启用 MCP 集成（默认 false）
 * - mcp.server.url: 内部 MCP Server 端点 URL（/internal/mcp）
 * - mcp.server.service-key: 内部 MCP 网关服务密钥，不是用户开发者 API Key
 * 
 * @author Aseubel
 * @date 2025/12/31
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "mcp.enabled", havingValue = "true", matchIfMissing = false)
public class McpConfig {

    @Value("${mcp.server.url:http://localhost:11611/internal/mcp}")
    private String mcpServerUrl;

    @Value("${mcp.server.service-key:}")
    private String mcpServerServiceKey;

    private McpClient mcpClient;
    private McpTransport mcpTransport;

    /**
     * 创建 MCP Transport
     * 
     * 使用 Streamable HTTP 传输层连接到 Go MCP Server。
     * Go Server 提供 POST /internal/mcp 端点用于处理内部请求。
     */
    @Bean(name = "mcpTransport")
    public McpTransport mcpTransport() {
        log.info("正在创建 MCP Transport (Streamable HTTP)，连接到: {}", mcpServerUrl);

        if (mcpServerServiceKey == null || mcpServerServiceKey.isBlank()) {
            throw new IllegalStateException("mcp.server.service-key must be set when MCP is enabled");
        }

        StreamableHttpMcpTransport.Builder builder = StreamableHttpMcpTransport.builder()
                .url(mcpServerUrl)
                .logRequests(false)
                .logResponses(false);
        builder.customHeaders(Map.of("X-MCP-Service-Key", mcpServerServiceKey));
        this.mcpTransport = builder.build();

        return this.mcpTransport;
    }

    /**
     * 创建 MCP Client
     * 
     * MCP Client 负责与 MCP Server 通信，发现并执行工具。
     */
    @Bean(name = "mcpClient")
    public McpClient mcpClient(McpTransport mcpTransport) {
        log.info("正在创建 MCP Client");

        this.mcpClient = DefaultMcpClient.builder()
                .key("yusi-mcp-client")
                .transport(mcpTransport)
                .build();

        log.info("MCP Client 创建成功，已连接到 MCP Server");
        return this.mcpClient;
    }

    /**
     * 创建 MCP Tool Provider
     * 
     * Tool Provider 封装了 MCP Client，使其可以被 LangChain4j AiServices 使用。
     * 配置只获取需要的工具（web_search），避免工具过多导致 LLM 混淆。
     */
    @Bean(name = "mcpToolProvider")
    public ToolProvider mcpToolProvider(McpClient mcpClient,
            AgentToolCapabilityCatalog agentToolCapabilityCatalog,
            AgentToolExecutionPolicyService agentToolExecutionPolicyService) {
        log.info("正在创建 MCP Tool Provider");

        McpToolProvider toolProvider = McpToolProvider.builder()
                .mcpClients(mcpClient)
                // 只允许使用 web_search 工具，避免暴露过多工具
                .filterToolNames(AgentToolConstants.WEB_SEARCH)
                .toolSpecificationMapper(agentToolCapabilityCatalog::mapMcpSpecification)
                .build();

        log.info("MCP Tool Provider 创建成功，已注册工具过滤器: {}", AgentToolConstants.WEB_SEARCH);
        return agentToolExecutionPolicyService.wrapProvider(toolProvider);
    }

    /**
     * 应用关闭时清理 MCP 资源
     */
    @PreDestroy
    public void cleanup() {
        log.info("正在关闭 MCP 连接...");
        try {
            if (mcpClient != null) {
                mcpClient.close();
            }
        } catch (Exception e) {
            log.warn("关闭 MCP Client 时发生错误: {}", e.getMessage());
        }
    }
}
