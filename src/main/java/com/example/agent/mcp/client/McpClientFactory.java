package com.example.agent.mcp.client;

import com.example.agent.mcp.config.McpConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class McpClientFactory {

    private static final Logger logger = LoggerFactory.getLogger(McpClientFactory.class);

    private McpClientFactory() {
    }

    public static McpClient create(McpConfig.McpServerConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("McpServerConfig不能为null");
        }
        if (config.getId() == null || config.getId().trim().isEmpty()) {
            throw new IllegalArgumentException("MCP服务器ID不能为空");
        }

        String type = config.getType() != null ? config.getType() : "stdio";

        switch (type.toLowerCase()) {
            case "stdio":
                logger.debug("创建Stdio类型MCP客户端: {}", config.getId());
                return new StdioMcpClient(config);
            case "sse":
            case "http":
                logger.warn("SSE类型MCP客户端尚未实现，使用Stdio模式: {}", config.getId());
                return new StdioMcpClient(config);
            default:
                logger.warn("未知的MCP连接类型: {}, 使用Stdio模式", type);
                return new StdioMcpClient(config);
        }
    }
}
