package com.example.agent.mcp;

import com.example.agent.config.Config;
import com.example.agent.mcp.client.AbstractMcpClient;
import com.example.agent.mcp.client.McpClient;
import com.example.agent.mcp.client.McpClientFactory;
import com.example.agent.mcp.config.McpConfig;
import com.example.agent.mcp.registry.McpToolAdapter;
import com.example.agent.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public class McpServiceManager {

    private static final Logger logger = LoggerFactory.getLogger(McpServiceManager.class);

    private final Config config;
    private final ToolRegistry toolRegistry;
    private final ConcurrentHashMap<String, McpClient> activeClients = new ConcurrentHashMap<>();
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final List<Runnable> shutdownHooks = new ArrayList<>();
    private ScheduledExecutorService reconnectExecutor;

    public McpServiceManager(Config config, ToolRegistry toolRegistry) {
        this.config = config;
        this.toolRegistry = toolRegistry;
    }

    public void initialize() {
        if (!config.getMcp().isEnabled()) {
            logger.info("MCP服务已在配置中禁用");
            return;
        }

        if (initialized.compareAndSet(false, true)) {
            logger.info("初始化MCP服务管理器...");
            logger.info("自动重连: {} (最多 {} 次，间隔 {} 秒)",
                    config.getMcp().isAutoReconnect() ? "启用" : "禁用",
                    config.getMcp().getMaxReconnectAttempts(),
                    config.getMcp().getReconnectDelaySeconds());

            this.reconnectExecutor = Executors.newSingleThreadScheduledExecutor();
            
            Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

            if (config.getMcp().isAutoConnect()) {
                connectAllConfiguredServers();
            }
        }
    }

    public void connectAllConfiguredServers() {
        List<McpConfig.McpServerConfig> servers = config.getMcp().getServers();
        if (servers == null || servers.isEmpty()) {
            logger.info("没有配置MCP服务器");
            return;
        }

        logger.info("开始连接 {} 个配置的MCP服务器...", servers.size());

        for (McpConfig.McpServerConfig serverConfig : servers) {
            connectServer(serverConfig);
        }
    }

    public void connectServer(McpConfig.McpServerConfig serverConfig) {
        String serverId = serverConfig.getId();

        if (activeClients.containsKey(serverId)) {
            logger.warn("MCP服务器 {} 已连接，跳过", serverId);
            return;
        }

        try {
            McpClient client = McpClientFactory.create(serverConfig);

            if (client instanceof AbstractMcpClient) {
                AbstractMcpClient abstractClient = (AbstractMcpClient) client;
                abstractClient.setReconnectExecutor(reconnectExecutor);
                abstractClient.setDisconnectListener(disconnectedClient -> {
                    logger.warn("MCP服务器 {} 连接已丢失，将不再重试", disconnectedClient.getServerId());
                    activeClients.remove(disconnectedClient.getServerId());
                });
            }

            client.connect()
                    .thenCompose(v -> {
                        logger.info("MCP服务器 {} 连接成功，正在初始化...", serverId);
                        return client.initialize();
                    })
                    .thenCompose(v -> {
                        logger.info("MCP服务器 {} 初始化成功，正在获取工具列表...", serverId);
                        return client.listTools();
                    })
                    .thenAccept(tools -> {
                        activeClients.put(serverId, client);

                        if (serverConfig.isAutoRegisterTools()) {
                            tools.forEach(tool -> {
                                McpToolAdapter adapter = new McpToolAdapter(client, tool);
                                toolRegistry.register(adapter);
                                logger.info("已注册MCP工具: {} ({})",
                                        adapter.getName(),
                                        tool.getDescription());
                            });
                        }

                        logger.info("MCP服务器 {} 就绪！共注册了 {} 个工具",
                                serverConfig.getName(),
                                tools.size());
                    })
                    .exceptionally(e -> {
                        logger.error("MCP服务器 {} 连接/初始化失败: {}",
                                serverId,
                                e.getMessage(),
                                e);
                        if (client instanceof AbstractMcpClient) {
                            ((AbstractMcpClient) client).onConnectionLost();
                        }
                        return null;
                    });

        } catch (Exception e) {
            logger.error("创建MCP客户端失败: {} - {}", serverId, e.getMessage(), e);
        }
    }

    public void disconnectServer(String serverId) {
        McpClient client = activeClients.remove(serverId);
        if (client != null) {
            try {
                client.disconnect().get();
                logger.info("MCP服务器 {} 已断开连接", serverId);
            } catch (Exception e) {
                logger.warn("断开MCP服务器 {} 连接时出错: {}", serverId, e.getMessage());
            }
        }
    }

    public List<McpClient> getActiveClients() {
        return new ArrayList<>(activeClients.values());
    }

    public McpClient getClient(String serverId) {
        return activeClients.get(serverId);
    }

    public void shutdown() {
        if (initialized.get()) {
            logger.info("关闭MCP服务管理器...");

            if (reconnectExecutor != null) {
                reconnectExecutor.shutdownNow();
            }

            List<String> serverIds = new ArrayList<>(activeClients.keySet());
            for (String serverId : serverIds) {
                disconnectServer(serverId);
            }

            for (Runnable hook : shutdownHooks) {
                try {
                    hook.run();
                } catch (Exception e) {
                    logger.warn("执行关闭钩子时出错", e);
                }
            }

            initialized.set(false);
            logger.info("MCP服务管理器已关闭");
        }
    }

    public boolean isInitialized() {
        return initialized.get();
    }

    public void addShutdownHook(Runnable hook) {
        shutdownHooks.add(hook);
    }
}
