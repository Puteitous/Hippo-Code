// src/test/java/com/example/agent/lsp/LspClientSmokeTest.java
package com.example.agent.lsp;

import com.example.agent.lsp.model.Location;
import com.example.agent.lsp.model.SymbolInformation;

import java.nio.file.Paths;
import java.util.List;

public class LspClientSmokeTest {

    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("   LSP 客户端冒烟测试");
        System.out.println("========================================");

        String workspaceRoot = Paths.get(".").toAbsolutePath().normalize().toString();
        System.out.println("工作目录: " + workspaceRoot);

        LspClient client = new LspClient(
                "java",
                "E:\\Tools\\jdt-language-server-1.54.0-202511200503\\jdtls.bat",
                List.of(),
                Paths.get(".").toAbsolutePath()
        );

        System.out.println("\n[1/5] 启动 LSP 服务器...");
        client.start().get(60, java.util.concurrent.TimeUnit.SECONDS);
        System.out.println("✅ LSP 进程启动成功");

        System.out.println("\n[2/5] 初始化 LSP...");
        client.initialize().get(60, java.util.concurrent.TimeUnit.SECONDS);
        System.out.println("✅ LSP 初始化成功");
        System.out.println("   (等待 10 秒让 jdtls 建立索引...)");
        Thread.sleep(10000);

        String testFile = "src/main/java/com/example/agent/tools/GrepTool.java";
        int testLine = 36;
        int testColumn = 18;

        System.out.println("\n[3/5] 测试: goToDefinition");
        System.out.println("   文件: " + testFile);
        System.out.println("   位置: 行 " + testLine + ", 列 " + testColumn);
        List<Location> defs = client.definition(testFile, testLine, testColumn)
                .get(30, java.util.concurrent.TimeUnit.SECONDS);
        System.out.println("✅ 找到 " + defs.size() + " 个定义:");
        for (Location loc : defs) {
            System.out.println("   -> " + loc.toFilePath() +
                    " (行" + loc.getRange().getStart().getLine() + ")");
        }

        System.out.println("\n[4/5] 测试: documentSymbol");
        List<SymbolInformation> symbols = client.documentSymbol(testFile)
                .get(30, java.util.concurrent.TimeUnit.SECONDS);
        System.out.println("✅ 找到 " + symbols.size() + " 个符号:");
        for (SymbolInformation sym : symbols) {
            System.out.println("   [" + sym.getKindName() + "] " + sym.getName());
        }

        System.out.println("\n[5/5] 测试: workspaceSymbol");
        List<SymbolInformation> wsSymbols = client.workspaceSymbol("GrepTool")
                .get(30, java.util.concurrent.TimeUnit.SECONDS);
        System.out.println("✅ 找到 " + wsSymbols.size() + " 个工作区符号");

        System.out.println("\n========================================");
        System.out.println("   ✅ 所有测试通过！");
        System.out.println("========================================");

        client.shutdown();
    }
}