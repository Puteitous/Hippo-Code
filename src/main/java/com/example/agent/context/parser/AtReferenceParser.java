package com.example.agent.context.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @ 引用解析器
 * 负责从用户输入中解析 @ 路径引用
 */
public class AtReferenceParser {

    private static final Logger logger = LoggerFactory.getLogger(AtReferenceParser.class);

    // 正则表达式：匹配 @ 开头的路径
    // 支持：@path/to/file, @./relative/path, @../parent/path
    private static final Pattern AT_REFERENCE_PATTERN = Pattern.compile("@([\\w\\d\\-./]+)");

    /**
     * 解析用户输入中的 @ 引用
     *
     * @param userInput 用户输入
     * @return 解析出的路径列表
     */
    public List<String> parse(String userInput) {
        List<String> references = new ArrayList<>();

        if (userInput == null || userInput.isEmpty()) {
            return references;
        }

        Matcher matcher = AT_REFERENCE_PATTERN.matcher(userInput);
        while (matcher.find()) {
            String path = matcher.group(1);
            if (path != null && !path.isEmpty()) {
                references.add(path);
                logger.debug("解析到 @ 引用: {}", path);
            }
        }

        logger.debug("共解析到 {} 个 @ 引用", references.size());
        return references;
    }

    /**
     * 验证路径是否有效
     *
     * @param path 路径
     * @return 是否有效
     */
    public boolean isValidPath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }

        // 基本验证：路径不能包含绝对路径符号（Windows 盘符或 Unix 根目录）
        return !path.matches("^[A-Za-z]:\\|^/");
    }

    /**
     * 清理引用，移除 @ 前缀
     *
     * @param reference 原始引用（如 @path/to/file）
     * @return 清理后的路径（如 path/to/file）
     */
    public String cleanupReference(String reference) {
        if (reference == null) {
            return null;
        }
        return reference.replaceFirst("^@", "");
    }

    /**
     * 检查用户输入是否包含 @ 引用
     *
     * @param userInput 用户输入
     * @return 是否包含 @ 引用
     */
    public boolean containsReferences(String userInput) {
        if (userInput == null || userInput.isEmpty()) {
            return false;
        }
        return AT_REFERENCE_PATTERN.matcher(userInput).find();
    }
}
