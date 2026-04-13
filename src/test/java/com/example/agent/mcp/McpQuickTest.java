package com.example.agent.mcp;

import com.example.agent.config.Config;
import com.example.agent.mcp.client.McpClient;
import com.example.agent.mcp.client.McpClientFactory;
import com.example.agent.mcp.model.McpTool;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class McpQuickTest {
    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("  MCP 快速测试");
        System.out.println("========================================");
        System.out.println();

        Config config = Config.getInstance();
        
        System.out.println("✅ 配置加载成功");
        System.out.println("  MCP 启用: " + config.getMcp().isEnabled());
        System.out.println("  服务器数量: " + config.getMcp().getServers().size());
        System.out.println();

        var serverConfig = config.getMcp().getServers().stream()
                .filter(s -> "echo".equals(s.getId()))
                .findFirst()
                .orElse(null);

        if (serverConfig == null) {
            System.err.println("❌ 找不到 echo 服务器配置");
            return;
        }

        System.out.println("✅ 找到 Echo 服务器配置");
        System.out.println("  ID: " + serverConfig.getId());
        System.out.println("  命令: " + serverConfig.getCommand() + " " + serverConfig.getArgs());
        System.out.println();

        System.out.println("🔌 正在连接...");
        McpClient client = McpClientFactory.create(serverConfig);
        
        client.connect().get(15, TimeUnit.SECONDS);
        System.out.println("✅ 连接成功!");
        System.out.println();

        System.out.println("⚙️  正在初始化...");
        client.initialize().get(15, TimeUnit.SECONDS);
        System.out.println("✅ 初始化成功!");
        System.out.println("  服务器名称: " + client.getServerName());
        System.out.println();

        System.out.println("🔧 获取工具列表...");
        List<McpTool> tools = client.listTools().get(10, TimeUnit.SECONDS);
        System.out.println("✅ 工具列表 (" + tools.size() + " 个):");
        tools.forEach(tool -> {
            System.out.println("  - " + tool.getName() + ": " + tool.getDescription());
        });
        System.out.println();

        System.out.println("🧪 测试工具调用 - echo:");
        Map<String, Object> args1 = new HashMap<>();
        args1.put("message", "Hello from Hippo Agent!");
        Object result = client.callTool("echo", args1).get(10, TimeUnit.SECONDS);
        System.out.println("✅ 调用成功!");
        System.out.println("  结果: " + result);
        System.out.println();

        System.out.println("🧪 测试工具调用 - add(123, 456):");
        Map<String, Object> args2 = new HashMap<>();
        args2.put("a", 123);
        args2.put("b", 456);
        Object result2 = client.callTool("add", args2).get(10, TimeUnit.SECONDS);
        System.out.println("✅ 调用成功!");
        System.out.println("  结果: " + result2);
        System.out.println();

        System.out.println("🔌 正在断开连接...");
        client.disconnect().get(5, TimeUnit.SECONDS);
        System.out.println("✅ 断开连接成功!");
        System.out.println();
        System.out.println("========================================");
        System.out.println("  🎉 MCP 集成测试全部通过!");
        System.out.println("========================================");
    }
}
