package com.example.agent.context.language;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java 语言处理器
 * 智能截断 Java 代码，保留关键结构
 */
public class JavaLanguageProvider implements LanguageProvider {

    private static final Logger logger = LoggerFactory.getLogger(JavaLanguageProvider.class);

    @Override
    public String truncate(String content, int maxTokens) {
        if (content == null || content.isEmpty()) {
            return "";
        }

        int currentTokens = estimateTokens(content);
        if (currentTokens <= maxTokens) {
            return content;
        }

        logger.debug("Java 代码需要截断: {} tokens > {} tokens", currentTokens, maxTokens);

        StringBuilder result = new StringBuilder();

        // 1. 保留 package 声明
        String packageDecl = extractPackage(content);
        if (packageDecl != null) {
            result.append(packageDecl).append("\n\n");
        }

        // 2. 保留 import 语句
        List<String> imports = extractImports(content);
        for (String imp : imports) {
            result.append(imp).append("\n");
        }
        if (!imports.isEmpty()) {
            result.append("\n");
        }

        // 3. 保留类/接口/枚举定义（包括注解）
        String classSignature = extractClassSignature(content);
        if (classSignature != null) {
            result.append(classSignature).append(" {\n");
        }

        // 4. 保留字段定义
        List<String> fields = extractFields(content);
        for (String field : fields) {
            result.append("    ").append(field).append("\n");
        }
        if (!fields.isEmpty()) {
            result.append("\n");
        }

        // 5. 处理方法体 - 保留方法签名，截断方法体
        List<MethodInfo> methods = extractMethods(content);
        int methodCount = 0;
        for (MethodInfo method : methods) {
            if (methodCount >= 10) { // 最多保留 10 个方法
                result.append("    // ... 更多方法省略 ...\n");
                break;
            }

            // 添加方法签名
            result.append("    ").append(method.signature).append(" {\n");

            // 截断方法体
            String truncatedBody = truncateMethodBody(method.body, maxTokens / methods.size());
            if (truncatedBody != null && !truncatedBody.isEmpty()) {
                // 缩进处理
                String[] lines = truncatedBody.split("\n");
                for (String line : lines) {
                    if (!line.trim().isEmpty()) {
                        result.append("        ").append(line.trim()).append("\n");
                    }
                }
            }

            result.append("    }\n\n");
            methodCount++;
        }

        result.append("}");

        // 添加截断提示
        result.append("\n\n// [文件已截断，显示关键结构]");

        return result.toString();
    }

    @Override
    public String getLanguageName() {
        return "Java";
    }

    @Override
    public String[] getSupportedExtensions() {
        return new String[]{"java"};
    }

    /**
     * 提取 package 声明
     */
    private String extractPackage(String content) {
        Pattern pattern = Pattern.compile("^\\s*package\\s+[\\w.]+\\s*;", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group().trim();
        }
        return null;
    }

    /**
     * 提取 import 语句
     */
    private List<String> extractImports(String content) {
        List<String> imports = new ArrayList<>();
        Pattern pattern = Pattern.compile("^\\s*import\\s+[\\w.*]+\\s*;", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            imports.add(matcher.group().trim());
        }
        return imports;
    }

    /**
     * 提取类签名（包括注解）
     */
    private String extractClassSignature(String content) {
        // 匹配类/接口/枚举定义，包括前面的注解
        Pattern pattern = Pattern.compile(
            "((?:\\s*@\\w+(?:\\([^)]*\\))?\\s*)*)" + // 注解
            "\\s*(public|private|protected)?\\s*" + // 访问修饰符
            "\\s*(abstract|final)?\\s*" + // 其他修饰符
            "\\s*(class|interface|enum|record)\\s+" + // 类型
            "(\\w+)" + // 类名
            "(?:\\s*<[^>]+>)?" + // 泛型
            "(?:\\s+extends\\s+\\w+)?" + // 继承
            "(?:\\s+implements\\s+[\\w,\\s]+)?", // 实现
            Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group().trim();
        }
        return null;
    }

    /**
     * 提取字段定义
     */
    private List<String> extractFields(String content) {
        List<String> fields = new ArrayList<>();
        // 匹配字段定义（简单匹配，不包括方法）
        Pattern pattern = Pattern.compile(
            "^\\s*((?:@\\w+\\s*)*)" + // 注解
            "(public|private|protected)?\\s*" + // 访问修饰符
            "(static|final|transient|volatile)?\\s*" + // 修饰符
            "([\\w<>,\\s]+)\\s+" + // 类型
            "(\\w+)\\s*" + // 字段名
            "(?:=\\s*[^;]+)?\\s*;", // 初始化（可选）
            Pattern.MULTILINE
        );
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            String field = matcher.group().trim();
            // 排除方法（包含括号的是方法）
            if (!field.contains("(") && fields.size() < 20) { // 最多保留 20 个字段
                fields.add(field);
            }
        }
        return fields;
    }

    /**
     * 提取方法信息
     */
    private List<MethodInfo> extractMethods(String content) {
        List<MethodInfo> methods = new ArrayList<>();

        // 匹配方法定义
        Pattern pattern = Pattern.compile(
            "((?:\\s*@\\w+(?:\\([^)]*\\))?\\s*)*)" + // 注解
            "\\s*(public|private|protected)?\\s*" + // 访问修饰符
            "\\s*(static|final|abstract|synchronized)?\\s*" + // 修饰符
            "\\s*([\\w<>,\\s]+)\\s+" + // 返回类型
            "(\\w+)\\s*\\(" + // 方法名和参数开始
            "([^)]*)\\)\\s*" + // 参数
            "(?:throws\\s+[\\w,\\s]+)?\\s*\\{", // throws 和方法体开始
            Pattern.DOTALL
        );

        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            String signature = matcher.group().trim();
            // 去掉最后的 {
            if (signature.endsWith("{")) {
                signature = signature.substring(0, signature.length() - 1).trim();
            }

            // 提取方法体
            int start = matcher.end();
            int braceCount = 1;
            int end = start;
            while (end < content.length() && braceCount > 0) {
                char c = content.charAt(end);
                if (c == '{') braceCount++;
                else if (c == '}') braceCount--;
                end++;
            }

            String body = content.substring(start, end - 1);
            methods.add(new MethodInfo(signature, body));
        }

        return methods;
    }

    /**
     * 截断方法体
     */
    private String truncateMethodBody(String body, int maxTokens) {
        if (body == null || body.isEmpty()) {
            return "// 空方法体";
        }

        int tokens = estimateTokens(body);
        if (tokens <= maxTokens) {
            return body;
        }

        // 保留前 N 行和后 N 行
        String[] lines = body.split("\n");
        int keepLines = Math.min(5, lines.length / 3);

        StringBuilder result = new StringBuilder();

        // 前 N 行
        for (int i = 0; i < keepLines && i < lines.length; i++) {
            result.append(lines[i]).append("\n");
        }

        result.append("        // ... 方法体省略 ...\n");

        // 后 N 行
        for (int i = Math.max(keepLines, lines.length - keepLines); i < lines.length; i++) {
            result.append(lines[i]).append("\n");
        }

        return result.toString();
    }

    /**
     * 方法信息
     */
    private static class MethodInfo {
        final String signature;
        final String body;

        MethodInfo(String signature, String body) {
            this.signature = signature;
            this.body = body;
        }
    }
}
