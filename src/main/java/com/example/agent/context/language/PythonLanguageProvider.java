package com.example.agent.context.language;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Python 语言处理器
 * 智能截断 Python 代码，保留关键结构
 */
public class PythonLanguageProvider implements LanguageProvider {

    private static final Logger logger = LoggerFactory.getLogger(PythonLanguageProvider.class);

    @Override
    public String truncate(String content, int maxTokens) {
        if (content == null || content.isEmpty()) {
            return "";
        }

        int currentTokens = estimateTokens(content);
        if (currentTokens <= maxTokens) {
            return content;
        }

        logger.debug("Python 代码需要截断: {} tokens > {} tokens", currentTokens, maxTokens);

        StringBuilder result = new StringBuilder();

        // 1. 保留 shebang
        String shebang = extractShebang(content);
        if (shebang != null) {
            result.append(shebang).append("\n\n");
        }

        // 2. 保留模块文档字符串
        String moduleDocstring = extractModuleDocstring(content);
        if (moduleDocstring != null) {
            result.append(moduleDocstring).append("\n\n");
        }

        // 3. 保留 import 语句
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
            result.append(cls.definition).append("\n");

            // 保留类文档字符串
            if (cls.docstring != null) {
                result.append("    ").append(cls.docstring.replace("\n", "\n    ")).append("\n\n");
            }

            // 保留类属性和方法签名
            for (String member : cls.members) {
                result.append("    ").append(member).append("\n");
            }

            result.append("\n");
        }

        // 6. 保留函数定义
        List<FunctionInfo> functions = extractFunctions(content);
        int funcCount = 0;
        for (FunctionInfo func : functions) {
            if (funcCount >= 10) { // 最多保留 10 个函数
                result.append("# ... 更多函数省略 ...\n");
                break;
            }

            result.append(func.definition).append("\n");

            // 保留函数文档字符串
            if (func.docstring != null) {
                result.append("    ").append(func.docstring.replace("\n", "\n    ")).append("\n");
            }

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

            result.append("\n");
            funcCount++;
        }

        // 添加截断提示
        result.append("\n# [文件已截断，显示关键结构]");

        return result.toString();
    }

    @Override
    public String getLanguageName() {
        return "Python";
    }

    @Override
    public String[] getSupportedExtensions() {
        return new String[]{"py"};
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
     * 提取模块文档字符串
     */
    private String extractModuleDocstring(String content) {
        // 匹配开头的三引号字符串
        Pattern pattern = Pattern.compile("^\\s*\"\"\"(.*?)\"\"\"", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group().trim();
        }
        // 尝试单引号
        pattern = Pattern.compile("^\\s*'''(.*?)'''", Pattern.DOTALL);
        matcher = pattern.matcher(content);
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
        Pattern pattern = Pattern.compile("^(?:from\\s+\\S+\\s+)?import\\s+[^#]+", Pattern.MULTILINE);
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
        // 匹配大写的常量或全局变量
        Pattern pattern = Pattern.compile(
            "^[A-Z_][A-Z0-9_]*\\s*=\\s*[^#]+",
            Pattern.MULTILINE
        );
        Matcher matcher = pattern.matcher(content);
        while (matcher.find() && globals.size() < 20) {
            globals.add(matcher.group().trim());
        }
        return globals;
    }

    /**
     * 提取类定义
     */
    private List<ClassInfo> extractClasses(String content) {
        List<ClassInfo> classes = new ArrayList<>();
        Pattern pattern = Pattern.compile(
            "^class\\s+(\\w+)(?:\\([^)]*\\))?\\s*:",
            Pattern.MULTILINE
        );

        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            String classDef = matcher.group().trim();
            int start = matcher.end();

            // 提取类体
            int end = findBlockEnd(content, start);
            String classBody = content.substring(start, end);

            // 提取文档字符串
            String docstring = extractDocstring(classBody);

            // 提取类成员（简化处理）
            List<String> members = extractClassMembers(classBody);

            classes.add(new ClassInfo(classDef, docstring, members));
        }

        return classes;
    }

    /**
     * 提取函数定义
     */
    private List<FunctionInfo> extractFunctions(String content) {
        List<FunctionInfo> functions = new ArrayList<>();
        Pattern pattern = Pattern.compile(
            "^(async\\s+)?def\\s+(\\w+)\\s*\\([^)]*\\)\\s*(?:->\\s*[^:]+)?\\s*:",
            Pattern.MULTILINE
        );

        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            String funcDef = matcher.group().trim();
            int start = matcher.end();

            // 提取函数体
            int end = findBlockEnd(content, start);
            String funcBody = content.substring(start, end);

            // 提取文档字符串
            String docstring = extractDocstring(funcBody);

            functions.add(new FunctionInfo(funcDef, docstring, funcBody));
        }

        return functions;
    }

    /**
     * 查找代码块结束位置
     */
    private int findBlockEnd(String content, int start) {
        String[] lines = content.substring(start).split("\n");
        int lineCount = 0;
        int baseIndent = -1;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().isEmpty()) {
                lineCount++;
                continue;
            }

            int indent = getIndentLevel(line);
            if (baseIndent == -1 && indent > 0) {
                baseIndent = indent;
            }

            // 如果遇到相同或更少缩进的非空行，说明块结束
            if (baseIndent > 0 && indent < baseIndent && !line.trim().isEmpty()) {
                break;
            }

            lineCount++;
        }

        // 计算结束位置
        int end = start;
        for (int i = 0; i < lineCount && i < lines.length; i++) {
            end += lines[i].length() + 1; // +1 for newline
        }

        return Math.min(end, content.length());
    }

    /**
     * 获取缩进级别
     */
    private int getIndentLevel(String line) {
        int count = 0;
        for (char c : line.toCharArray()) {
            if (c == ' ') count++;
            else if (c == '\t') count += 4;
            else break;
        }
        return count;
    }

    /**
     * 提取文档字符串
     */
    private String extractDocstring(String content) {
        Pattern pattern = Pattern.compile("^\\s*\"\"\"(.*?)\"\"\"", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group().trim();
        }
        pattern = Pattern.compile("^\\s*'''(.*?)'''", Pattern.DOTALL);
        matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group().trim();
        }
        return null;
    }

    /**
     * 提取类成员
     */
    private List<String> extractClassMembers(String classBody) {
        List<String> members = new ArrayList<>();

        // 提取方法定义
        Pattern pattern = Pattern.compile(
            "^(\\s+)(async\\s+)?def\\s+(\\w+)\\s*\\([^)]*\\)",
            Pattern.MULTILINE
        );
        Matcher matcher = pattern.matcher(classBody);
        while (matcher.find() && members.size() < 15) {
            members.add(matcher.group().trim() + ":");
        }

        // 提取类属性
        pattern = Pattern.compile(
            "^(\\s+)(self\\.\\w+)\\s*=",
            Pattern.MULTILINE
        );
        matcher = pattern.matcher(classBody);
        while (matcher.find() && members.size() < 15) {
            members.add(matcher.group().trim());
        }

        return members;
    }

    /**
     * 截断函数体
     */
    private String truncateFunctionBody(String body, int maxTokens) {
        if (body == null || body.isEmpty()) {
            return "pass";
        }

        int tokens = estimateTokens(body);
        if (tokens <= maxTokens) {
            return body;
        }

        // 保留前 N 行
        String[] lines = body.split("\n");
        int keepLines = Math.min(3, lines.length);

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < keepLines; i++) {
            if (!lines[i].trim().isEmpty()) {
                result.append(lines[i]).append("\n");
            }
        }

        result.append("# ... 函数体省略 ...\n");
        result.append("pass");

        return result.toString();
    }

    /**
     * 类信息
     */
    private static class ClassInfo {
        final String definition;
        final String docstring;
        final List<String> members;

        ClassInfo(String definition, String docstring, List<String> members) {
            this.definition = definition;
            this.docstring = docstring;
            this.members = members;
        }
    }

    /**
     * 函数信息
     */
    private static class FunctionInfo {
        final String definition;
        final String docstring;
        final String body;

        FunctionInfo(String definition, String docstring, String body) {
            this.definition = definition;
            this.docstring = docstring;
            this.body = body;
        }
    }
}
