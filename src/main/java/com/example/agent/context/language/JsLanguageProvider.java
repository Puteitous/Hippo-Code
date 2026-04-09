package com.example.agent.context.language;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JavaScript/TypeScript 语言处理器
 * 智能截断 JS/TS 代码，保留关键结构
 */
public class JsLanguageProvider implements LanguageProvider {

    private static final Logger logger = LoggerFactory.getLogger(JsLanguageProvider.class);

    @Override
    public String truncate(String content, int maxTokens) {
        if (content == null || content.isEmpty()) {
            return "";
        }

        int currentTokens = estimateTokens(content);
        if (currentTokens <= maxTokens) {
            return content;
        }

        logger.debug("JS/TS 代码需要截断: {} tokens > {} tokens", currentTokens, maxTokens);

        StringBuilder result = new StringBuilder();

        // 1. 保留 shebang
        String shebang = extractShebang(content);
        if (shebang != null) {
            result.append(shebang).append("\n\n");
        }

        // 2. 保留 "use strict" 等指令
        String useStrict = extractUseStrict(content);
        if (useStrict != null) {
            result.append(useStrict).append("\n\n");
        }

        // 3. 保留 import/export 语句
        List<String> imports = extractImports(content);
        for (String imp : imports) {
            result.append(imp).append("\n");
        }
        if (!imports.isEmpty()) {
            result.append("\n");
        }

        // 4. 保留全局变量和常量
        List<String> globals = extractGlobalVariables(content);
        for (String global : globals) {
            result.append(global).append("\n");
        }
        if (!globals.isEmpty()) {
            result.append("\n");
        }

        // 5. 保留类定义
        List<ClassInfo> classes = extractClasses(content);
        for (ClassInfo cls : classes) {
            result.append(cls.definition).append(" {\n");

            // 保留类成员
            for (String member : cls.members) {
                result.append("    ").append(member).append("\n");
            }

            result.append("}\n\n");
        }

        // 6. 保留函数定义
        List<FunctionInfo> functions = extractFunctions(content);
        int funcCount = 0;
        for (FunctionInfo func : functions) {
            if (funcCount >= 10) { // 最多保留 10 个函数
                result.append("// ... 更多函数省略 ...\n");
                break;
            }

            result.append(func.definition).append(" {\n");

            // 截断函数体
            String truncatedBody = truncateFunctionBody(func.body, maxTokens / functions.size());
            if (truncatedBody != null && !truncatedBody.isEmpty()) {
                String[] lines = truncatedBody.split("\n");
                for (String line : lines) {
                    if (!line.trim().isEmpty()) {
                        result.append("    ").append(line.trim()).append("\n");
                    }
                }
            }

            result.append("}\n\n");
            funcCount++;
        }

        // 添加截断提示
        result.append("\n// [文件已截断，显示关键结构]");

        return result.toString();
    }

    @Override
    public String getLanguageName() {
        return "JavaScript/TypeScript";
    }

    @Override
    public String[] getSupportedExtensions() {
        return new String[]{"js", "ts", "jsx", "tsx"};
    }

    /**
     * 提取 shebang
     */
    private String extractShebang(String content) {
        if (content.startsWith("#!/")) {
            int end = content.indexOf('\n');
            if (end > 0) {
                return content.substring(0, end).trim();
            }
        }
        return null;
    }

    /**
     * 提取 use strict 等指令
     */
    private String extractUseStrict(String content) {
        Pattern pattern = Pattern.compile("^\\s*['\"]use strict['\"];?", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group().trim();
        }
        return null;
    }

    /**
     * 提取 import/export 语句
     */
    private List<String> extractImports(String content) {
        List<String> imports = new ArrayList<>();
        // 匹配 import 语句
        Pattern pattern = Pattern.compile(
            "^\\s*(?:import|export)\\s+(?:[^;]+);?",
            Pattern.MULTILINE
        );
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            imports.add(matcher.group().trim());
        }
        return imports;
    }

    /**
     * 提取全局变量和常量
     */
    private List<String> extractGlobalVariables(String content) {
        List<String> globals = new ArrayList<>();
        // 匹配 const/let/var 声明
        Pattern pattern = Pattern.compile(
            "^\\s*(?:const|let|var)\\s+\\w+\\s*=",
            Pattern.MULTILINE
        );
        Matcher matcher = pattern.matcher(content);
        while (matcher.find() && globals.size() < 20) {
            String line = matcher.group().trim();
            // 找到完整的语句
            int start = content.indexOf(line);
            int end = content.indexOf(';', start);
            if (end > start) {
                globals.add(content.substring(start, end + 1).trim());
            }
        }
        return globals;
    }

    /**
     * 提取类定义
     */
    private List<ClassInfo> extractClasses(String content) {
        List<ClassInfo> classes = new ArrayList<>();
        Pattern pattern = Pattern.compile(
            "^\\s*(?:export\\s+)?(?:abstract\\s+)?class\\s+\\w+(?:\\s*<[^>]+>)?(?:\\s+extends\\s+\\w+)?(?:\\s+implements\\s+[\\w,\\s]+)?",
            Pattern.MULTILINE
        );

        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            String classDef = matcher.group().trim();
            int start = matcher.end();

            // 找到类体
            int braceCount = 0;
            int end = start;
            boolean inClass = false;
            while (end < content.length()) {
                char c = content.charAt(end);
                if (c == '{') {
                    braceCount++;
                    inClass = true;
                } else if (c == '}') {
                    braceCount--;
                    if (inClass && braceCount == 0) {
                        end++;
                        break;
                    }
                }
                end++;
            }

            String classBody = content.substring(start, end);
            List<String> members = extractClassMembers(classBody);

            classes.add(new ClassInfo(classDef, members));
        }

        return classes;
    }

    /**
     * 提取类成员
     */
    private List<String> extractClassMembers(String classBody) {
        List<String> members = new ArrayList<>();

        // 提取属性
        Pattern pattern = Pattern.compile(
            "^(\\s*)(?:private|protected|public|readonly|static)?\\s*\\w+\\s*[:?]?",
            Pattern.MULTILINE
        );
        Matcher matcher = pattern.matcher(classBody);
        while (matcher.find() && members.size() < 15) {
            String member = matcher.group().trim();
            if (!member.isEmpty() && !members.contains(member)) {
                members.add(member + ";");
            }
        }

        // 提取方法
        pattern = Pattern.compile(
            "^(\\s*)(?:private|protected|public|async|static)?\\s*\\w+\\s*\\([^)]*\\)",
            Pattern.MULTILINE
        );
        matcher = pattern.matcher(classBody);
        while (matcher.find() && members.size() < 15) {
            String member = matcher.group().trim();
            if (!member.isEmpty() && !members.contains(member + ";")) {
                members.add(member + ";");
            }
        }

        return members;
    }

    /**
     * 提取函数定义
     */
    private List<FunctionInfo> extractFunctions(String content) {
        List<FunctionInfo> functions = new ArrayList<>();

        // 匹配函数定义（多种格式）
        Pattern pattern = Pattern.compile(
            "^(\\s*)(?:export\\s+)?(?:async\\s+)?(?:function\\s+)?\\w+\\s*\\([^)]*\\)(?:\\s*:\\s*[^{]+)?\\s*{",
            Pattern.MULTILINE
        );

        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            String funcDef = matcher.group().trim();
            // 去掉最后的 {
            if (funcDef.endsWith("{")) {
                funcDef = funcDef.substring(0, funcDef.length() - 1).trim();
            }

            int start = matcher.end();

            // 找到函数体
            int braceCount = 1;
            int end = start;
            while (end < content.length() && braceCount > 0) {
                char c = content.charAt(end);
                if (c == '{') braceCount++;
                else if (c == '}') braceCount--;
                end++;
            }

            String funcBody = content.substring(start, end - 1);
            functions.add(new FunctionInfo(funcDef, funcBody));
        }

        return functions;
    }

    /**
     * 截断函数体
     */
    private String truncateFunctionBody(String body, int maxTokens) {
        if (body == null || body.isEmpty()) {
            return "// 空函数体";
        }

        int tokens = estimateTokens(body);
        if (tokens <= maxTokens) {
            return body;
        }

        // 保留前 N 行和后 N 行
        String[] lines = body.split("\n");
        int keepLines = Math.min(3, lines.length / 3);

        StringBuilder result = new StringBuilder();

        // 前 N 行
        for (int i = 0; i < keepLines && i < lines.length; i++) {
            if (!lines[i].trim().isEmpty()) {
                result.append(lines[i]).append("\n");
            }
        }

        result.append("// ... 函数体省略 ...\n");

        // 后 N 行
        for (int i = Math.max(keepLines, lines.length - keepLines); i < lines.length; i++) {
            if (!lines[i].trim().isEmpty()) {
                result.append(lines[i]).append("\n");
            }
        }

        return result.toString();
    }

    /**
     * 类信息
     */
    private static class ClassInfo {
        final String definition;
        final List<String> members;

        ClassInfo(String definition, List<String> members) {
            this.definition = definition;
            this.members = members;
        }
    }

    /**
     * 函数信息
     */
    private static class FunctionInfo {
        final String definition;
        final String body;

        FunctionInfo(String definition, String body) {
            this.definition = definition;
            this.body = body;
        }
    }
}