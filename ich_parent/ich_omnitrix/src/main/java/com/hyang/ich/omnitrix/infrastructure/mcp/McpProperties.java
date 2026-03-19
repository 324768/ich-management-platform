package com.hyang.ich.omnitrix.infrastructure.mcp;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * MCP 客户端配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "omnitrix.mcp")
public class McpProperties {

    /** 是否启用 MCP 客户端 */
    private boolean enabled = false;

    /** MCP Server 配置列表 */
    private List<ServerConfig> servers;

    @Data
    public static class ServerConfig {
        /** Server 名称（唯一标识） */
        private String name;

        /** Server 类型: stdio / streamable-http */
        private String type = "streamable-http";

        /** Streamable HTTP 模式：URL */
        private String url;

        /** STDIO 模式：命令行 */
        private String command;

        /** STDIO 模式：参数 */
        private List<String> args;

        /** 环境变量 */
        private Map<String, String> env;

        /** 是否开启请求/响应日志 */
        private boolean logRequests = false;

        private boolean logResponses = false;
    }
}
