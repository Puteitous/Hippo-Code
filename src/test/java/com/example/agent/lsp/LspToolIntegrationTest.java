package com.example.agent.lsp;

import com.example.agent.lsp.model.Location;
import com.example.agent.lsp.model.Position;
import com.example.agent.lsp.model.Range;
import com.example.agent.lsp.tools.FindReferencesTool;
import com.example.agent.lsp.tools.GoToDefinitionTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LspToolIntegrationTest {

    private LspClient mockLspClient;
    private GoToDefinitionTool gotoTool;
    private FindReferencesTool referencesTool;
    private JsonNodeFactory nodeFactory;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        mockLspClient = mock(LspClient.class);
        gotoTool = new GoToDefinitionTool(mockLspClient, "java");
        referencesTool = new FindReferencesTool(mockLspClient, "java");
        nodeFactory = JsonNodeFactory.instance;
    }

    @Test
    void gotoDefinition_shouldReturnSuccessWhenLspReturnsResult() throws Exception {
        when(mockLspClient.isInitialized()).thenReturn(true);

        Location mockLocation = createMockLocation("src/main/Test.java", 10, 5);
        when(mockLspClient.definition(anyString(), anyInt(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(List.of(mockLocation)));

        ObjectNode args = nodeFactory.objectNode();
        args.put("file", "src/main/Source.java");
        args.put("line", 5);
        args.put("column", 3);

        String result = gotoTool.execute(args);

        assertThat(result)
                .contains("✅ 找到")
                .contains("Test.java")
                .contains("行: 10")
                .contains("列: 5");
    }

    @Test
    void gotoDefinition_shouldReturnEmptyMessageWhenNoResult() throws Exception {
        when(mockLspClient.isInitialized()).thenReturn(true);

        when(mockLspClient.definition(anyString(), anyInt(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(List.of()));

        ObjectNode args = nodeFactory.objectNode();
        args.put("file", "src/main/Source.java");
        args.put("line", 5);
        args.put("column", 3);

        String result = gotoTool.execute(args);

        assertThat(result)
                .contains("未找到定义")
                .contains("调整行号列号");
    }

    @Test
    void gotoDefinition_shouldFallbackWhenLspNotInitialized() throws Exception {
        when(mockLspClient.isInitialized()).thenReturn(false);

        ObjectNode args = nodeFactory.objectNode();
        args.put("file", "src/main/Source.java");
        args.put("line", 5);
        args.put("column", 3);

        String result = gotoTool.execute(args);

        assertThat(result)
                .contains("LSP 服务正在启动中")
                .contains("grep")
                .contains("60-120 秒");
    }

    @Test
    void findReferences_shouldReturnSuccessWhenLspReturnsResult() throws Exception {
        when(mockLspClient.isInitialized()).thenReturn(true);

        Location loc1 = createMockLocation("src/main/A.java", 20, 10);
        Location loc2 = createMockLocation("src/main/B.java", 30, 15);

        when(mockLspClient.references(anyString(), anyInt(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(List.of(loc1, loc2)));

        ObjectNode args = nodeFactory.objectNode();
        args.put("file", "src/main/Source.java");
        args.put("line", 5);
        args.put("column", 3);

        String result = referencesTool.execute(args);

        assertThat(result)
                .contains("✅ 找到 2 个引用位置")
                .contains("A.java")
                .contains("B.java")
                .contains("行: 20")
                .contains("行: 30");
    }

    @Test
    void findReferences_shouldReturnEmptyMessageWhenNoResult() throws Exception {
        when(mockLspClient.isInitialized()).thenReturn(true);

        when(mockLspClient.references(anyString(), anyInt(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(List.of()));

        ObjectNode args = nodeFactory.objectNode();
        args.put("file", "src/main/Source.java");
        args.put("line", 5);
        args.put("column", 3);

        String result = referencesTool.execute(args);

        assertThat(result)
                .contains("❌ 未找到任何引用")
                .contains("调整行号列号");
    }

    @Test
    void findReferences_shouldFallbackWhenLspNotInitialized() throws Exception {
        when(mockLspClient.isInitialized()).thenReturn(false);

        ObjectNode args = nodeFactory.objectNode();
        args.put("file", "src/main/Source.java");
        args.put("line", 5);
        args.put("column", 3);

        String result = referencesTool.execute(args);

        assertThat(result)
                .contains("LSP 服务正在启动中")
                .contains("grep")
                .contains("60-120 秒");
    }

    @Test
    void gotoDefinition_shouldHandleLspExceptionGracefully() throws Exception {
        when(mockLspClient.isInitialized()).thenReturn(true);

        when(mockLspClient.definition(anyString(), anyInt(), anyInt()))
                .thenReturn(failedFuture(new RuntimeException("Internal LSP error")));

        ObjectNode args = nodeFactory.objectNode();
        args.put("file", "src/main/Source.java");
        args.put("line", 5);
        args.put("column", 3);

        String result = gotoTool.execute(args);

        assertThat(result)
                .contains("⚠️ 跳转到定义失败")
                .contains("Internal LSP error");
    }

    @Test
    void findReferences_shouldHandleLspExceptionGracefully() throws Exception {
        when(mockLspClient.isInitialized()).thenReturn(true);

        when(mockLspClient.references(anyString(), anyInt(), anyInt()))
                .thenReturn(failedFuture(new RuntimeException("Internal LSP error")));

        ObjectNode args = nodeFactory.objectNode();
        args.put("file", "src/main/Source.java");
        args.put("line", 5);
        args.put("column", 3);

        String result = referencesTool.execute(args);

        assertThat(result)
                .contains("⚠️ 查找引用失败")
                .contains("Internal LSP error");
    }

    @Test
    void bothTools_shouldRunInBackground() {
        assertThat(gotoTool.shouldRunInBackground()).isTrue();
        assertThat(referencesTool.shouldRunInBackground()).isTrue();
    }

    @Test
    void bothTools_shouldHaveCorrectNames() {
        assertThat(gotoTool.getName()).isEqualTo("lsp_goto_definition");
        assertThat(referencesTool.getName()).isEqualTo("lsp_find_references");
    }

    @Test
    void bothTools_shouldHaveValidDescriptions() {
        assertThat(gotoTool.getDescription()).contains("跳转到符号定义");
        assertThat(referencesTool.getDescription()).contains("查找所有引用");
    }

    @Test
    void bothTools_shouldHaveValidJsonSchemas() {
        assertThat(gotoTool.getParametersSchema())
                .contains("file")
                .contains("line")
                .contains("column")
                .contains("required");

        assertThat(referencesTool.getParametersSchema())
                .contains("file")
                .contains("line")
                .contains("column")
                .contains("required");
    }

    @Test
    void exceptionUnwrapper_shouldUnwrapCompletionException() throws Exception {
        when(mockLspClient.isInitialized()).thenReturn(true);

        RuntimeException rootCause = new RuntimeException("真正的错误信息");
        CompletableFuture<List<Location>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new java.util.concurrent.CompletionException(rootCause));

        when(mockLspClient.definition(anyString(), anyInt(), anyInt())).thenReturn(failed);

        ObjectNode args = nodeFactory.objectNode();
        args.put("file", "src/main/Source.java");
        args.put("line", 5);
        args.put("column", 3);

        String result = gotoTool.execute(args);

        assertThat(result).contains("真正的错误信息");
    }

    @Test
    void exceptionUnwrapper_shouldUnwrapExecutionException() throws Exception {
        when(mockLspClient.isInitialized()).thenReturn(true);

        RuntimeException rootCause = new RuntimeException("深层错误");
        CompletableFuture<List<Location>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new java.util.concurrent.ExecutionException(rootCause));

        when(mockLspClient.definition(anyString(), anyInt(), anyInt())).thenReturn(failed);

        ObjectNode args = nodeFactory.objectNode();
        args.put("file", "src/main/Source.java");
        args.put("line", 5);
        args.put("column", 3);

        String result = gotoTool.execute(args);

        assertThat(result).contains("深层错误");
    }

    private Location createMockLocation(String path, int line, int column) {
        Location loc = new Location();
        loc.setUri(tempDir.resolve(path).toUri().toString());
        loc.setRange(new Range(new Position(line, column), new Position(line, column + 5)));
        return loc;
    }

    private <T> CompletableFuture<T> failedFuture(Exception e) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(e);
        return future;
    }
}
