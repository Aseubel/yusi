package com.aseubel.yusi.config.ai;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.service.tool.ToolProvider;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP (Model Context Protocol) 配置类
 * 
 * 通过 MCP 协议连接到 Go 实现的 MCP Server，获取外部工具（如 web_search）。
 * 这使得 DiaryAssistant 可以访问实时网络搜索等能力。
 * 
 * 配置项：
 * - mcp.enabled: 是否启用 MCP 集成（默认 false）
 * - mcp.server.url: MCP Server 的 SSE 端点 URL
 * 
 * @author Aseubel
 * @date 2025/12/31
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "mcp.enabled", havingValue = "true", matchIfMissing = false)
public class McpConfig {

    @Value("${mcp.server.url:http://localhost:8080/sse}")
    private String mcpServerUrl;

    private McpClient mcpClient;
    private McpTransport mcpTransport;

    /**
     * 创建 MCP Transport
     * 
     * 使用 HTTP SSE 传输层连接到 Go MCP Server。
     * Go Server 提供 /sse 端点用于建立 SSE 连接。
     * 
     * 注意：HttpMcpTransport 已被标记为弃用，推荐使用 StreamableHttpMcpTransport。
     * 但当前 Go MCP Server 使用的是 legacy SSE 协议（/sse + /messages 端点），
     * 需要等 Go Server 升级到 Streamable HTTP 协议后才能切换。
     * 
     * @see dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport
     */
    @SuppressWarnings("deprecation")
    @Bean(name = "mcpTransport")
    public McpTransport mcpTransport() {
        log.info("正在创建 MCP Transport (legacy SSE)，连接到: {}", mcpServerUrl);

        this.mcpTransport = HttpMcpTransport.builder()
                .sseUrl(mcpServerUrl)
                .logRequests(true)
                .logResponses(true)
                .build();

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
    public ToolProvider mcpToolProvider(McpClient mcpClient) {
        log.info("正在创建 MCP Tool Provider");

        McpToolProvider toolProvider = McpToolProvider.builder()
                .mcpClients(mcpClient)
                // 只允许使用 web_search 工具，避免暴露过多工具
                .filterToolNames("web_search")
                .build();

        log.info("MCP Tool Provider 创建成功，已注册工具过滤器: web_search");
        return toolProvider;
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
