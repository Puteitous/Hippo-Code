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

        String type = config.getType() != null ? config.getType().trim().toLowerCase() : "stdio";

        if ("sse".equals(type) || "http".equals(type)) {
            if (config.getUrl() == null || config.getUrl().trim().isEmpty()) {
                throw new IllegalArgumentException("SSE类型服务器必须配置URL");
            }
        } else {
            if (config.getCommand() == null || config.getCommand().trim().isEmpty()) {
                throw new IllegalArgumentException("Stdio类型服务器必须配置启动命令");
            }
        }

        switch (type) {
            case "stdio":
                logger.debug("创建Stdio类型MCP客户端: {}", config.getId());
                return new StdioMcpClient(config);
            case "sse":
            case "http":
                logger.debug("创建SSE类型MCP客户端: {}", config.getId());
                return new SseMcpClient(config);
            default:
                logger.warn("未知的MCP连接类型: {}, 使用Stdio模式", type);
                return new StdioMcpClient(config);
        }
    }
}
