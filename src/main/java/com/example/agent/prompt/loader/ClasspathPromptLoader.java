package com.example.agent.prompt.loader;

import com.example.agent.prompt.model.Prompt;
import com.example.agent.prompt.model.PromptType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

public class ClasspathPromptLoader implements PromptLoader {

    private static final Logger logger = LoggerFactory.getLogger(ClasspathPromptLoader.class);
    private static final String PROMPTS_BASE = "prompts";
    private static final String CONFIG_FILE = "prompts/prompts.yaml";

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @Override
    public List<Prompt> loadAll() {
        List<Prompt> prompts = new ArrayList<>();

        try {
            JsonNode config = loadConfig();
            if (config != null && config.has("prompts")) {
                for (JsonNode promptConfig : config.get("prompts")) {
                    Optional<Prompt> prompt = loadPrompt(promptConfig);
                    prompt.ifPresent(prompts::add);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to load prompts config: {}", e.getMessage());
        }

        if (prompts.isEmpty()) {
            logger.info("No prompts found in config, falling back to default coding prompt");
            prompts.add(createDefaultCodingPrompt());
        }

        logger.info("Loaded {} prompts from classpath", prompts.size());
        return prompts;
    }

    private JsonNode loadConfig() throws IOException {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE);
        if (inputStream == null) {
            logger.warn("Prompt config file not found: {}", CONFIG_FILE);
            return null;
        }
        return yamlMapper.readTree(inputStream);
    }

    private Optional<Prompt> loadPrompt(JsonNode config) {
        try {
            String typeStr = config.get("type").asText();
            PromptType type = PromptType.valueOf(typeStr);
            String file = config.has("file") ? config.get("file").asText() : null;
            String version = config.has("version") ? config.get("version").asText() : "1.0.0";
            boolean enabled = !config.has("enabled") || config.get("enabled").asBoolean();
            int priority = config.has("priority") ? config.get("priority").asInt() : 0;

            String content = "";
            if (file != null) {
                content = loadContentFromFile(PROMPTS_BASE + "/" + file);
            }

            return Optional.of(Prompt.builder()
                    .type(type)
                    .content(content)
                    .version(version)
                    .enabled(enabled)
                    .priority(priority)
                    .build());
        } catch (Exception e) {
            logger.warn("Failed to load prompt: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String loadContentFromFile(String resourcePath) {
        try {
            URL resourceUrl = getClass().getClassLoader().getResource(resourcePath);
            if (resourceUrl == null) {
                logger.warn("Prompt file not found: {}", resourcePath);
                return "";
            }

            URI uri = resourceUrl.toURI();
            Path path;
            if (uri.getScheme().equals("jar")) {
                FileSystem fileSystem = FileSystems.newFileSystem(uri, Collections.emptyMap());
                path = fileSystem.getPath(resourcePath);
            } else {
                path = Paths.get(uri);
            }

            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.warn("Failed to read prompt file {}: {}", resourcePath, e.getMessage());
            return "";
        }
    }

    private Prompt createDefaultCodingPrompt() {
        String content = getLegacySystemPrompt();
        return Prompt.builder()
                .type(PromptType.BASE_CODING)
                .content(content)
                .version("1.0.0")
                .priority(100)
                .build();
    }

    private String getLegacySystemPrompt() {
        return """
            你是一个编程助手，可以帮助用户进行软件开发任务。
            
            你可以访问以下工具：
            - read_file: 读取文件内容（支持缓存和智能截断）
            - write_file: 写入文件内容（覆盖整个文件）
            - edit_file: 精确编辑文件内容（替换特定文本片段）
            - list_directory: 列出目录内容，支持递归显示目录树
            - glob: 使用 glob 模式查找文件（如 **/*.java 查找所有 Java 文件）
            - grep: 在文件内容中搜索文本（支持正则表达式）
            - search_code: 语义检索代码库，查找相关代码文件
            - ask_user: 向用户提问并等待回答（用于确认或获取信息）
            - bash: 执行终端命令（如 git, mvn, npm 等，有安全限制）
            
            === 自主决策原则 ===
            
            🔍 上下文自主发现：
            - 不要等待用户告诉你"读哪个文件"，你应该主动判断需要哪些信息
            - 如果你对代码库不了解，先用 list_directory、glob、grep 探索项目结构
            - 如果回答问题需要上下文，主动调用 read_file 读取相关文件
            - 可以多次调用工具获取信息，直到你有足够的上下文回答问题
            
            📌 @引用语法糖支持：
            - 用户输入中的 @path/to/file 表示"引用这个文件"
            - 看到 @path/to/file 时，你应该主动调用 read_file 读取该文件
            - 例如："请重构 @src/main/Example.java" → 你需要先读取 Example.java 再回答
            - 支持相对路径和绝对路径
            
            请始终使用中文回复。
            """;
    }
}
