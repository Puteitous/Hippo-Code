package com.example.agent.mcp;

import com.example.agent.config.Config;
import com.example.agent.mcp.client.McpClient;
import com.example.agent.mcp.client.McpClientFactory;
import com.example.agent.mcp.model.McpTool;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class McpIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(McpIntegrationTest.class);

    @Test
    public void testMcpConfigLoading() {
        Config config = Config.getInstance();
        
        assertNotNull(config.getMcp(), "MCP配置应该存在");
        assertTrue(config.getMcp().isEnabled(), "MCP应该启用");
        
        logger.info("MCP 配置加载成功:");
        logger.info("  - enabled: {}", config.getMcp().isEnabled());
        logger.info("  - autoConnect: {}", config.getMcp().isAutoConnect());
        logger.info("  - 服务器数量: {}", config.getMcp().getServers().size());
        
        config.getMcp().getServers().forEach(server -> {
            logger.info("    - {}: {} ({})", server.getId(), server.getName(), server.getType());
        });
    }

    @Test
    public void testEchoServerConnection() throws Exception {
        Config config = Config.getInstance();
        
        var serverConfig = config.getMcp().getServers().stream()
                .filter(s -> "echo".equals(s.getId()))
                .findFirst()
                .orElse(null);
        
        assertNotNull(serverConfig, "应该找到echo服务器配置");
        
        logger.info("开始连接 Echo MCP 服务器...");
        
        McpClient client = McpClientFactory.create(serverConfig);
        
        client.connect().get(10, TimeUnit.SECONDS);
        assertTrue(client.isConnected(), "客户端应该已连接");
        logger.info("✅ 连接成功");
        
        client.initialize().get(10, TimeUnit.SECONDS);
        logger.info("✅ 初始化成功");
        logger.info("  - 服务器名称: {}", client.getServerName());
        
        List<McpTool> tools = client.listTools().get(10, TimeUnit.SECONDS);
        logger.info("✅ 获取工具列表成功，共 {} 个工具:", tools.size());
        tools.forEach(tool -> {
            logger.info("  - {}: {}", tool.getName(), tool.getDescription());
        });
        
        assertEquals(3, tools.size(), "应该有3个工具");
        
        Map<String, Object> args = new HashMap<>();
        args.put("message", "Hello MCP!");
        Object result = client.callTool("echo", args).get(10, TimeUnit.SECONDS);
        logger.info("✅ 调用 echo 工具成功，结果: {}", result);
        
        client.disconnect().get(5, TimeUnit.SECONDS);
        logger.info("✅ 断开连接成功");
    }
}
