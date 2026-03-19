package com.hyang.ich.omnitrix.infrastructure.mcp;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.McpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;

/**
 * MCP ToolProvider 工厂 - 将 MCP 工具转换为 LangChain4j ToolProvider
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "omnitrix.mcp", name = "enabled", havingValue = "true")
public class McpToolProviderFactory {

    private final McpClientFactory mcpClientFactory;
    private McpToolProvider toolProvider;

    public McpToolProviderFactory(McpClientFactory mcpClientFactory) {
        this.mcpClientFactory = mcpClientFactory;
    }

    @PostConstruct
    public void init() {
        List<McpClient> clients = mcpClientFactory.getMcpClients();
        if (clients == null || clients.isEmpty()) {
            log.info("未配置 MCP Servers");
            return;
        }

        this.toolProvider = McpToolProvider.builder()
                .mcpClients(clients)
                .failIfOneServerFails(false)
                .build();

        log.info("MCP ToolProvider 已创建，包含 {} 个 MCP Clients", clients.size());
    }

    public McpToolProvider getToolProvider() {
        return toolProvider;
    }
}
