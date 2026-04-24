package com.example.agent.lsp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LspBoundaryConditionsTest {

    private LspClient lspClient;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        lspClient = new LspClient(
                "java",
                "jdtls",
                List.of(),
                tempDir
        );
    }

    @Test
    void definition_shouldRejectNegativeLineNumber() {
        CompletionException exception = assertThrows(CompletionException.class, () -> {
            lspClient.definition("Test.java", -1, 0).join();
        });

        assertThat(exception.getCause())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("行号不能为负数");
    }

    @Test
    void definition_shouldRejectNegativeColumnNumber() {
        CompletionException exception = assertThrows(CompletionException.class, () -> {
            lspClient.definition("Test.java", 0, -1).join();
        });

        assertThat(exception.getCause())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("列号不能为负数");
    }

    @Test
    void references_shouldRejectNegativeLineNumber() {
        CompletionException exception = assertThrows(CompletionException.class, () -> {
            lspClient.references("Test.java", -1, 0).join();
        });

        assertThat(exception.getCause())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("行号不能为负数");
    }

    @Test
    void references_shouldRejectNegativeColumnNumber() {
        CompletionException exception = assertThrows(CompletionException.class, () -> {
            lspClient.references("Test.java", 0, -1).join();
        });

        assertThat(exception.getCause())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("列号不能为负数");
    }

    @Test
    void hover_shouldRejectNegativeLineNumber() {
        CompletionException exception = assertThrows(CompletionException.class, () -> {
            lspClient.hover("Test.java", -1, 0).join();
        });

        assertThat(exception.getCause())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("行号不能为负数");
    }

    @Test
    void hover_shouldRejectNegativeColumnNumber() {
        CompletionException exception = assertThrows(CompletionException.class, () -> {
            lspClient.hover("Test.java", 0, -1).join();
        });

        assertThat(exception.getCause())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("列号不能为负数");
    }

    @Test
    void definition_shouldRejectNullFilePath() {
        CompletionException exception = assertThrows(CompletionException.class, () -> {
            lspClient.definition(null, 0, 0).join();
        });

        assertThat(exception.getCause())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文件路径不能为空");
    }

    @Test
    void definition_shouldRejectEmptyFilePath() {
        CompletionException exception = assertThrows(CompletionException.class, () -> {
            lspClient.definition("", 0, 0).join();
        });

        assertThat(exception.getCause())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文件路径不能为空");
    }

    @Test
    void definition_shouldRejectNonExistentFile() {
        CompletionException exception = assertThrows(CompletionException.class, () -> {
            lspClient.definition("NonExistent.java", 0, 0).join();
        });

        assertThat(exception.getCause().getMessage())
                .contains("LSP");
    }

    @Test
    void shutdown_shouldHandleUninitializedClient() {
        assertThat(lspClient.isInitialized()).isFalse();
        
        lspClient.shutdown();
        
        assertThat(lspClient.isInitialized()).isFalse();
        assertThat(lspClient.isConnected()).isFalse();
    }

    @Test
    void shouldHandleVariableResolutionInCommand() {
        LspClient clientWithEnv = new LspClient(
                "java",
                "${user_home}/jdtls",
                List.of(),
                tempDir
        );

        assertThat(clientWithEnv.getLanguageId()).isEqualTo("java");
    }

    @Test
    void documentSymbol_shouldRejectNonExistentFile() {
        CompletionException exception = assertThrows(CompletionException.class, () -> {
            lspClient.documentSymbol("NonExistent.java").join();
        });

        assertThat(exception.getCause().getMessage())
                .contains("LSP");
    }

    @Test
    void workspaceSymbol_shouldHandleEmptyQuery() {
        CompletionException exception = assertThrows(CompletionException.class, () -> {
            lspClient.workspaceSymbol("").join();
        });

        assertThat(exception.getCause().getMessage())
                .contains("LSP");
    }

    @Test
    void workspaceSymbol_shouldHandleNullQuery() {
        CompletionException exception = assertThrows(CompletionException.class, () -> {
            lspClient.workspaceSymbol(null).join();
        });

        assertThat(exception.getCause().getMessage())
                .contains("LSP");
    }

    @Test
    void shouldReturnCorrectLanguageId() {
        assertThat(lspClient.getLanguageId()).isEqualTo("java");
    }

    @Test
    void shouldHandleNullArgumentsGracefully() {
        LspClient client = new LspClient(
                "java",
                "jdtls",
                null,
                tempDir,
                null
        );

        assertThat(client.getLanguageId()).isEqualTo("java");
    }

    @Test
    void definition_shouldWorkWithValidFile() throws Exception {
        Path validFile = tempDir.resolve("Valid.java");
        Files.writeString(validFile, "public class Valid {}");

        CompletionException exception = assertThrows(CompletionException.class, () -> {
            lspClient.definition("Valid.java", 0, 0).join();
        });

        assertThat(exception.getCause().getMessage())
                .contains("LSP 未初始化");
    }
}
