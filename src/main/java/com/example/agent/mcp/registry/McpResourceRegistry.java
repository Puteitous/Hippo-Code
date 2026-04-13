package com.example.agent.mcp.registry;

import com.example.agent.mcp.client.McpClient;
import com.example.agent.mcp.model.McpResource;
import com.example.agent.mcp.model.ReadResourceResult;
import com.example.agent.mcp.model.ResourceContents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class McpResourceRegistry {

    private static final Logger logger = LoggerFactory.getLogger(McpResourceRegistry.class);

    private final ConcurrentHashMap<String, McpResourceEntry> resourceMap = new ConcurrentHashMap<>();

    public void registerResources(McpClient client, List<McpResource> resources) {
        resources.forEach(resource -> {
            String fullUri = "mcp://" + client.getServerId() + "/" + resource.getUri();
            resourceMap.put(fullUri, new McpResourceEntry(client, resource));
            logger.info("已注册MCP资源: {} ({})", fullUri, resource.getName());
        });
    }

    public void unregisterResources(String serverId) {
        List<String> toRemove = new ArrayList<>();
        resourceMap.keySet().forEach(uri -> {
            if (uri.startsWith("mcp://" + serverId + "/")) {
                toRemove.add(uri);
            }
        });
        toRemove.forEach(resourceMap::remove);
        logger.info("已移除服务器 {} 的所有资源，共 {} 个", serverId, toRemove.size());
    }

    public McpResource getResource(String fullUri) {
        McpResourceEntry entry = resourceMap.get(fullUri);
        return entry != null ? entry.resource() : null;
    }

    public String readResourceContent(String fullUri) {
        McpResourceEntry entry = resourceMap.get(fullUri);
        if (entry == null) {
            throw new IllegalArgumentException("资源不存在: " + fullUri);
        }

        try {
            ReadResourceResult result = entry.client().readResource(entry.resource().getUri())
                    .get(30, TimeUnit.SECONDS);

            if (result.getContents().isEmpty()) {
                return "";
            }

            ResourceContents contents = result.getContents().getFirst();
            return contents.getContent();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("读取资源被中断", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new RuntimeException("读取资源失败: " + fullUri, e);
        }
    }

    public Collection<McpResourceEntry> getAllResources() {
        return resourceMap.values();
    }

    public List<McpResourceEntry> getResourcesByServer(String serverId) {
        List<McpResourceEntry> result = new ArrayList<>();
        resourceMap.values().forEach(entry -> {
            if (entry.client().getServerId().equals(serverId)) {
                result.add(entry);
            }
        });
        return result;
    }

    public int getResourceCount() {
        return resourceMap.size();
    }

    public record McpResourceEntry(McpClient client, McpResource resource) {
        public String getFullUri() {
            return "mcp://" + client.getServerId() + "/" + resource.getUri();
        }
    }
}
