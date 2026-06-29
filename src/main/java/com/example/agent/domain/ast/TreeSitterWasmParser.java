package com.example.agent.domain.ast;

import com.dylibso.chicory.runtime.ExportFunction;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.Memory;
import com.dylibso.chicory.wasi.WasiOptions;
import com.dylibso.chicory.wasi.WasiPreview1;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 基于 Rust WASM (wasm32-wasip1) + Chicory 的 Tree-sitter 语法解析器。
 * <p>
 * 加载单个 tree-sitter-parser.wasm（Rust 编译），通过 WasiPreview1 提供 WASI 支持，
 * 零手写 stubs。调用 parse(code, lang) 返回 JSON 格式的诊断结果。
 * <p>
 * 旧版（Emscripten 编译的 NPM WASM + 16 个手写 stubs）已废弃。
 */
public class TreeSitterWasmParser implements CodeParser {

    private static final Logger log = LoggerFactory.getLogger(TreeSitterWasmParser.class);

    private static final ObjectMapper JSON = new ObjectMapper();

    /** WASM 文件路径（classpath） */
    private static final String WASM_RESOURCE = "/tree-sitter/tree-sitter-parser.wasm";

    /** 语言扩展名映射 */
    private static final Map<String, List<String>> LANGUAGE_EXTENSIONS = Map.of(
        "java", List.of(".java"),
        "javascript", List.of(".js", ".mjs", ".cjs", ".jsx"),
        "typescript", List.of(".ts", ".tsx"),
        "python", List.of(".py"),
        "go", List.of(".go"),
        "rust", List.of(".rs"),
        "html", List.of(".html", ".htm"),
        "css", List.of(".css", ".scss", ".less"),
        "json", List.of(".json")
    );

    /** WASM 实例是否可用 */
    private static volatile boolean available = false;
    private static volatile boolean initAttempted = false;
    private static Instance instance;
    private static ExportFunction parseFn;
    private static ExportFunction allocFn;
    private static ExportFunction deallocFn;
    private static final ReentrantLock initLock = new ReentrantLock();

    private final String language;

    public TreeSitterWasmParser(String language) {
        this.language = normalizeLanguage(language);
        ensureInitialized();
    }

    // ==================== 静态工厂方法 ====================

    /** 检查 WASM 解析器是否可用 */
    public static boolean isAvailable() {
        ensureInitialized();
        return available;
    }

    /** @deprecated 保留兼容，新版本不区分语言可用性 */
    @Deprecated
    public static boolean isAvailable(String language) {
        return isAvailable();
    }

    /** 检查是否有任意语言可用 */
    public static boolean hasAnyAvailable() {
        return isAvailable();
    }

    /** 根据文件路径自动选择对应语言的 parser */
    public static TreeSitterWasmParser forFile(String filePath) {
        if (filePath == null || !available) return null;
        String lower = filePath.toLowerCase();
        for (var entry : LANGUAGE_EXTENSIONS.entrySet()) {
            for (String ext : entry.getValue()) {
                if (lower.endsWith(ext)) {
                    return new TreeSitterWasmParser(entry.getKey());
                }
            }
        }
        return null;
    }

    // ==================== CodeParser 接口实现 ====================

    @Override
    public String language() {
        return language;
    }

    @Override
    public boolean supports(String filePath) {
        if (filePath == null || !available) return false;
        String lower = filePath.toLowerCase();
        List<String> exts = LANGUAGE_EXTENSIONS.get(language);
        return exts != null && exts.stream().anyMatch(lower::endsWith);
    }

    @Override
    public ParseResult parse(String content) throws Exception {
        if (content == null || content.trim().isEmpty()) {
            return new ParseResult(true, List.of(), content);
        }
        if (!available) {
            log.warn("Tree-sitter WASM not available, skipping parse");
            return new ParseResult(true, List.of(), content);
        }

        byte[] codeBytes = content.getBytes(StandardCharsets.UTF_8);
        byte[] langBytes = language.getBytes(StandardCharsets.UTF_8);

        synchronized (TreeSitterWasmParser.class) {
            // 1. 分配内存并写入输入
            int codePtr = (int) allocFn.apply(codeBytes.length)[0];
            int langPtr = (int) allocFn.apply(langBytes.length)[0];
            Memory mem = instance.memory();
            mem.write(codePtr, codeBytes);
            mem.write(langPtr, langBytes);

            // 2. 调用 parse
            long resultPacked = parseFn.apply(codePtr, codeBytes.length, langPtr, langBytes.length)[0];
            int resultPtr = (int) (resultPacked >>> 32);
            int resultLen = (int) (resultPacked & 0xFFFFFFFFL);

            // 3. 读取 JSON 结果
            String json = new String(mem.readBytes(resultPtr, resultLen), StandardCharsets.UTF_8);

            // 4. 释放结果内存
            deallocFn.apply(resultPtr, resultLen);

            // 5. 解析 JSON
            return parseJsonResult(json, content);
        }
    }

    // ==================== 内部方法 ====================

    private static void ensureInitialized() {
        if (initAttempted) return;
        initLock.lock();
        try {
            if (initAttempted) return;
            initAttempted = true;
            initWasm();
        } finally {
            initLock.unlock();
        }
    }

    private static void initWasm() {
        try {
            byte[] wasmBytes = readResource(WASM_RESOURCE);
            if (wasmBytes == null) {
                log.warn("Tree-sitter WASM not found: {}", WASM_RESOURCE);
                return;
            }

            var wasiOpts = WasiOptions.builder().inheritSystem().build();
            var wasi = WasiPreview1.builder().withOptions(wasiOpts).build();

            instance = Instance.builder(com.dylibso.chicory.wasm.Parser.parse(wasmBytes))
                .withImportValues(
                    com.dylibso.chicory.runtime.ImportValues.builder()
                        .addFunction(wasi.toHostFunctions())
                        .build())
                .build();

            parseFn = instance.export("parse");
            allocFn = instance.export("alloc");
            deallocFn = instance.export("dealloc");

            available = true;
            log.info("Tree-sitter WASM loaded ({} bytes)", wasmBytes.length);
        } catch (Exception e) {
            log.error("Failed to initialize Tree-sitter WASM: {}", e.getMessage(), e);
            available = false;
        }
    }

    private static byte[] readResource(String path) {
        try (InputStream is = TreeSitterWasmParser.class.getResourceAsStream(path)) {
            if (is == null) return null;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            return baos.toByteArray();
        } catch (Exception e) {
            log.warn("Failed to read resource {}: {}", path, e.getMessage());
            return null;
        }
    }

    private ParseResult parseJsonResult(String json, String content) throws JsonProcessingException {
        JsonNode root = JSON.readTree(json);

        // 错误响应（如不支持的语）
        JsonNode errorNode = root.get("error");
        if (errorNode != null && !errorNode.isNull()) {
            String msg = errorNode.asText();
            log.debug("Tree-sitter parse error: {}", msg);
            return new ParseResult(false, List.of(new SyntaxError(0, 0, msg, "")), content);
        }

        // 正常响应
        boolean valid = root.get("valid").asBoolean();
        if (valid) {
            return new ParseResult(true, List.of(), content);
        }

        List<SyntaxError> errors = new ArrayList<>();
        JsonNode errorsNode = root.get("errors");
        if (errorsNode != null && errorsNode.isArray()) {
            for (JsonNode err : errorsNode) {
                int row = err.get("row").asInt() + 1; // 0-based → 1-based
                int col = err.get("column").asInt() + 1;
                String kind = err.get("kind").asText();
                String message = err.get("message").asText();
                errors.add(new SyntaxError(row, col, "[" + kind + "] " + message, ""));
            }
        }

        return new ParseResult(errors.isEmpty(), errors, content);
    }

    private static String normalizeLanguage(String lang) {
        if (lang == null) return "java";
        return switch (lang.toLowerCase()) {
            case "js" -> "javascript";
            case "ts" -> "typescript";
            case "py" -> "python";
            case "rs" -> "rust";
            case "htm" -> "html";
            case "golang" -> "go";
            case "scss", "less" -> "css";
            default -> lang;
        };
    }
}
