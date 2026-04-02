package com.example.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class WriteFileTool implements ToolExecutor {

    @Override
    public String getName() {
        return "write_file";
    }

    @Override
    public String getDescription() {
        return "将内容写入指定路径的文件。如果文件不存在则创建，如果存在则覆盖。只能访问项目目录内的文件。";
    }

    @Override
    public String getParametersSchema() {
        return """
            {
                "type": "object",
                "properties": {
                    "path": {
                        "type": "string",
                        "description": "要写入的文件路径（绝对路径或相对路径，只能访问项目目录内）"
                    },
                    "content": {
                        "type": "string",
                        "description": "要写入文件的内容"
                    }
                },
                "required": ["path", "content"]
            }
            """;
    }

    @Override
    public String execute(JsonNode arguments) throws ToolExecutionException {
        if (!arguments.has("path")) {
            throw new ToolExecutionException("缺少必需参数: path");
        }
        if (!arguments.has("content")) {
            throw new ToolExecutionException("缺少必需参数: content");
        }

        String filePath = arguments.get("path").asText();
        String content = arguments.get("content").asText();

        Path path = PathSecurityUtils.validateAndResolve(filePath);

        try {
            Path parentDir = path.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            boolean fileExisted = Files.exists(path);
            
            Files.writeString(path, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            String relativePath = PathSecurityUtils.getRelativePath(path);
            String action = fileExisted ? "覆盖" : "创建";
            
            return String.format("文件%s成功: %s (%d 字符)", action, relativePath, content.length());
        } catch (IOException e) {
            throw new ToolExecutionException("写入文件失败: " + e.getMessage(), e);
        }
    }
}
