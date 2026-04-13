package com.example.agent.mcp.registry;

import com.example.agent.mcp.client.McpClient;
import com.example.agent.mcp.model.GetPromptResult;
import com.example.agent.mcp.model.McpPrompt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class McpPromptRegistry {

    private static final Logger logger = LoggerFactory.getLogger(McpPromptRegistry.class);

    private final ConcurrentHashMap<String, McpPromptEntry> promptMap = new ConcurrentHashMap<>();

    public void registerPrompts(McpClient client, List<McpPrompt> prompts) {
        prompts.forEach(prompt -> {
            String fullName = client.getServerId() + "/" + prompt.getName();
            promptMap.put(fullName, new McpPromptEntry(client, prompt));
            logger.info("已注册MCP提示词: {} ({})", fullName, prompt.getDescription());
        });
    }

    public void unregisterPrompts(String serverId) {
        List<String> toRemove = new ArrayList<>();
        promptMap.keySet().forEach(name -> {
            if (name.startsWith(serverId + "/")) {
                toRemove.add(name);
            }
        });
        toRemove.forEach(promptMap::remove);
        logger.info("已移除服务器 {} 的所有提示词，共 {} 个", serverId, toRemove.size());
    }

    public McpPrompt getPrompt(String fullName) {
        McpPromptEntry entry = promptMap.get(fullName);
        return entry != null ? entry.prompt() : null;
    }

    public GetPromptResult renderPrompt(String fullName, Map<String, String> arguments) {
        McpPromptEntry entry = promptMap.get(fullName);
        if (entry == null) {
            throw new IllegalArgumentException("提示词不存在: " + fullName);
        }

        try {
            String promptName = entry.prompt().getName();
            return entry.client().getPrompt(promptName, arguments)
                    .get(30, TimeUnit.SECONDS);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("渲染提示词被中断", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new RuntimeException("渲染提示词失败: " + fullName, e);
        }
    }

    public String renderPromptAsText(String fullName, Map<String, String> arguments) {
        GetPromptResult result = renderPrompt(fullName, arguments);
        StringBuilder sb = new StringBuilder();
        result.getMessages().forEach(msg -> {
            sb.append("[").append(msg.getRole().toUpperCase()).append("]\n");
            sb.append(msg.getContent().getText()).append("\n\n");
        });
        return sb.toString();
    }

    public Collection<McpPromptEntry> getAllPrompts() {
        return promptMap.values();
    }

    public List<McpPromptEntry> getPromptsByServer(String serverId) {
        List<McpPromptEntry> result = new ArrayList<>();
        promptMap.values().forEach(entry -> {
            if (entry.client().getServerId().equals(serverId)) {
                result.add(entry);
            }
        });
        return result;
    }

    public int getPromptCount() {
        return promptMap.size();
    }

    public record McpPromptEntry(McpClient client, McpPrompt prompt) {
        public String getFullName() {
            return client.getServerId() + "/" + prompt.getName();
        }
    }
}
