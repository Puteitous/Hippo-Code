package com.example.agent.domain.rule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HippoRulesParser {

    private static final Logger logger = LoggerFactory.getLogger(HippoRulesParser.class);

    private final List<String> contextFilters = new ArrayList<>();
    private final List<String> globalRules = new ArrayList<>();
    private final List<String> memoryGuidelines = new ArrayList<>();
    private boolean loaded = false;

    public void loadFromWorkspace() {
        loadFromWorkspace(System.getProperty("user.dir"));
    }

    public void loadFromWorkspace(String workspacePath) {
        Path hippoRulesPath = Paths.get(workspacePath, ".hipporules");

        if (!Files.exists(hippoRulesPath)) {
            loaded = true;
            return;
        }

        try {
            String content = Files.readString(hippoRulesPath);
            parseRules(content);
            loaded = true;
        } catch (IOException e) {
            loaded = true;
        }
    }

    private void parseRules(String content) {
        String[] sections = content.split("(?=# )");

        for (String section : sections) {
            section = section.trim();
            if (section.isEmpty()) continue;

            String firstLine = section.split("\n", 2)[0].trim();

            if (firstLine.contains("Context Filter") || firstLine.contains("上下文过滤")) {
                parseFilterSection(section);
            } else if (firstLine.contains("Global Rule") || firstLine.contains("全局规则")) {
                parseRuleSection(section);
            } else if (firstLine.contains("Memory") || firstLine.contains("记忆")) {
                parseMemorySection(section);
            }
        }
    }

    private void parseFilterSection(String section) {
        Pattern pattern = Pattern.compile("^[-*]\\s+(.+)$", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(section);
        while (matcher.find()) {
            contextFilters.add(matcher.group(1).trim());
        }
    }

    private void parseRuleSection(String section) {
        Pattern pattern = Pattern.compile("^[-*]\\s+(.+)$", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(section);
        while (matcher.find()) {
            globalRules.add(matcher.group(1).trim());
        }
    }

    private void parseMemorySection(String section) {
        Pattern pattern = Pattern.compile("^[-*]\\s+(.+)$", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(section);
        while (matcher.find()) {
            memoryGuidelines.add(matcher.group(1).trim());
        }
    }

    public String applyFilters(String content) {
        if (!loaded) loadFromWorkspace();

        String result = content;
        for (String filter : contextFilters) {
            try {
                result = result.replaceAll("(?i)" + Pattern.quote(filter), "");
            } catch (Exception e) {
                logger.warn("过滤规则内容失败", e);
            }
        }
        return result;
    }

    public String getRulesAsSystemPrompt() {
        if (!loaded) loadFromWorkspace();

        if (globalRules.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## 项目规则 (.hipporules)\n\n");
        for (String rule : globalRules) {
            sb.append("- ").append(rule).append("\n");
        }
        sb.append("\n---\n\n");
        return sb.toString();
    }

    public List<String> getContextFilters() {
        if (!loaded) loadFromWorkspace();
        return new ArrayList<>(contextFilters);
    }

    public List<String> getGlobalRules() {
        if (!loaded) loadFromWorkspace();
        return new ArrayList<>(globalRules);
    }

    public List<String> getMemoryGuidelines() {
        if (!loaded) loadFromWorkspace();
        return new ArrayList<>(memoryGuidelines);
    }

    public boolean isLoaded() {
        return loaded;
    }
}
