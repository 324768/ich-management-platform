package com.hyang.ich.omnitrix.infrastructure.mcp;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * MCP Client 工厂 - 根据配置创建 MCP 客户端
 * 支持 Streamable HTTP 和 STDIO 两种传输模式
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "omnitrix.mcp", name = "enabled", havingValue = "true")
public class McpClientFactory {

    private final McpProperties properties;
    private final List<McpClient> mcpClients = new ArrayList<>();

    public McpClientFactory(McpProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        if (!properties.isEnabled() || properties.getServers() == null) {
            return;
        }

        for (McpProperties.ServerConfig server : properties.getServers()) {
            try {
                McpClient client = createClient(server);
                if (client != null) {
                    mcpClients.add(client);
                    log.info("MCP Server [{}] 已连接, type={}", server.getName(), server.getType());
                }
            } catch (Exception e) {
                log.error("连接 MCP Server [{}] 失败: {}", server.getName(), e.getMessage());
            }
        }
    }

    /**
     * 创建 MCP Client
     */
    private McpClient createClient(McpProperties.ServerConfig server) {
        // STDIO 模式
        if ("stdio".equals(server.getType())) {
            return createStdioClient(server);
        }

        // Streamable HTTP 模式（默认）
        return createStreamableHttpClient(server);
    }

    /**
     * 创建 Streamable HTTP 客户端
     */
    private McpClient createStreamableHttpClient(McpProperties.ServerConfig server) {
        McpTransport transport = new StreamableHttpMcpTransport.Builder()
                .url(server.getUrl())
                .logRequests(server.isLogRequests())
                .logResponses(server.isLogResponses())
                .build();

        return new DefaultMcpClient.Builder()
                .key(server.getName())
                .transport(transport)
                .build();
    }

    /**
     * 创建 STDIO 客户端
     */
    private McpClient createStdioClient(McpProperties.ServerConfig server) {
        List<String> command = buildCommand(server);

        McpTransport transport = new StdioMcpTransport.Builder()
                .command(command)
                .logEvents(server.isLogRequests())
                .build();

        return new DefaultMcpClient.Builder()
                .key(server.getName())
                .transport(transport)
                .build();
    }

    /**
     * 构建命令列表
     */
    private List<String> buildCommand(McpProperties.ServerConfig server) {
        List<String> cmd = new ArrayList<>();
        cmd.add(server.getCommand());
        if (server.getArgs() != null) {
            cmd.addAll(server.getArgs());
        }
        return cmd;
    }

    public List<McpClient> getMcpClients() {
        return mcpClients;
    }
}
