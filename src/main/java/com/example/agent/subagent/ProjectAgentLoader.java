package com.example.agent.subagent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class ProjectAgentLoader {
    private static final Logger logger = LoggerFactory.getLogger(ProjectAgentLoader.class);
    
    private static final String AGENT_DIR = ".hippo/agents";
    private static final String FILE_EXTENSION = ".md";
    
    private final Map<String, ProjectAgentDefinition> customAgents = new HashMap<>();
    private final Path workspaceRoot;
    
    public ProjectAgentLoader(String workspacePath) {
        this.workspaceRoot = Paths.get(workspacePath);
        loadProjectAgents();
    }
    
    public void loadProjectAgents() {
        Path agentDir = workspaceRoot.resolve(AGENT_DIR);
        if (!Files.exists(agentDir)) {
            logger.info("未找到自定义 Agent 目录: {}", agentDir);
            return;
        }
        
        logger.info("扫描项目自定义 Agent 目录: {}", agentDir);
        
        try (Stream<Path> files = Files.walk(agentDir, 1)) {
            files.filter(Files::isRegularFile)
                 .filter(f -> f.toString().endsWith(FILE_EXTENSION))
                 .forEach(this::loadAgentFile);
            
            logger.info("已加载 {} 个项目自定义 Agent", customAgents.size());
            
        } catch (IOException e) {
            logger.error("扫描自定义 Agent 目录失败", e);
        }
    }
    
    private void loadAgentFile(Path file) {
        try {
            String fileName = file.getFileName().toString();
            String agentType = fileName.substring(0, fileName.length() - FILE_EXTENSION.length());
            
            List<String> lines = Files.readAllLines(file);
            ProjectAgentDefinition definition = parseAgentDefinition(agentType, lines);
            
            customAgents.put(agentType.toLowerCase(), definition);
            logger.info("加载自定义 Agent: {} -> {}", agentType, definition.getDisplayName());
            
        } catch (Exception e) {
            logger.error("加载 Agent 文件失败: {}", file, e);
        }
    }
    
    private ProjectAgentDefinition parseAgentDefinition(String agentType, List<String> lines) {
        StringBuilder content = new StringBuilder();
        String displayName = agentType;
        String description = "";
        String icon = "🤖";
        SubAgentPermission permission = SubAgentPermission.DEFAULT;
        boolean useFork = false;
        
        for (String line : lines) {
            content.append(line).append("\n");
            
            if (line.startsWith("name:") || line.startsWith("# ") || line.startsWith("名称:")) {
                displayName = extractValue(line);
            }
            if (line.startsWith("description:") || line.startsWith("描述:")) {
                description = extractValue(line);
            }
            if (line.startsWith("icon:") || line.startsWith("图标:")) {
                icon = extractValue(line);
            }
            if (line.startsWith("permission:") || line.startsWith("权限:")) {
                String perm = extractValue(line).toLowerCase();
                if ("read_only".equals(perm) || "只读".equals(perm)) {
                    permission = SubAgentPermission.READ_ONLY;
                }
            }
            if (line.startsWith("fork:") || line.startsWith("缓存优化:")) {
                useFork = Boolean.parseBoolean(extractValue(line));
            }
        }
        
        return new ProjectAgentDefinition(
            agentType,
            displayName,
            description,
            permission,
            icon,
            useFork,
            content.toString()
        );
    }
    
    private String extractValue(String line) {
        int colon = line.indexOf(':');
        if (colon > 0) {
            return line.substring(colon + 1).trim();
        }
        int space = line.indexOf(' ');
        if (space > 0 && line.startsWith("#")) {
            return line.substring(space + 1).trim();
        }
        return line.trim();
    }
    
    public ProjectAgentDefinition getAgent(String agentType) {
        if (agentType == null) return null;
        return customAgents.get(agentType.toLowerCase());
    }
    
    public List<ProjectAgentDefinition> getAllAgents() {
        return new ArrayList<>(customAgents.values());
    }
    
    public String getCustomAgentMenu() {
        if (customAgents.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("\n## 📁 项目自定义专家 (").append(customAgents.size()).append(" 个)\n\n");
        
        for (ProjectAgentDefinition agent : customAgents.values()) {
            sb.append("- `").append(agent.getAgentType()).append("`: ")
              .append(agent.getIcon()).append(" **").append(agent.getDisplayName()).append("**")
              .append(" - ").append(agent.getDescription()).append("\n");
        }
        
        return sb.toString();
    }
    
    public static class ProjectAgentDefinition {
        private final String agentType;
        private final String displayName;
        private final String description;
        private final SubAgentPermission permission;
        private final String icon;
        private final boolean useForkOptimization;
        private final String systemPrompt;
        
        public ProjectAgentDefinition(String agentType, 
                                      String displayName, 
                                      String description, 
                                      SubAgentPermission permission, 
                                      String icon,
                                      boolean useForkOptimization,
                                      String systemPrompt) {
            this.agentType = agentType;
            this.displayName = displayName;
            this.description = description;
            this.permission = permission;
            this.icon = icon;
            this.useForkOptimization = useForkOptimization;
            this.systemPrompt = systemPrompt;
        }
        
        public String getAgentType() { return agentType; }
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public SubAgentPermission getPermission() { return permission; }
        public String getIcon() { return icon; }
        public boolean useForkOptimization() { return useForkOptimization; }
        public String getSystemPrompt() { return systemPrompt; }
    }
}
