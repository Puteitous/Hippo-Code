package com.example.agent.progress;

import com.example.agent.console.AgentUi;
import com.example.agent.console.ConsoleStyle;
import org.jline.reader.LineReader;

public class EditConfirmationHandler {

    private final AgentUi ui;
    private final LineReader reader;
    private final DiffPreviewer diffPreviewer;
    private final SpinnerManager spinnerManager = SpinnerManager.getInstance();
    private boolean autoConfirmEnabled = false;

    public EditConfirmationHandler(AgentUi ui, LineReader reader) {
        this.ui = ui;
        this.reader = reader;
        this.diffPreviewer = new DiffPreviewer();
    }

    public void setAutoConfirmEnabled(boolean enabled) {
        this.autoConfirmEnabled = enabled;
    }

    public boolean confirmEdit(String filePath, String oldText, String newText) {
        if (autoConfirmEnabled) {
            return true;
        }

        spinnerManager.pauseAll();

        String diffPreview = diffPreviewer.generateUnifiedDiff(filePath, oldText, newText);
        ui.println(diffPreview);

        try {
            while (true) {
                String response = promptUser();

                if (response == null) {
                    return false;
                }

                response = response.trim().toLowerCase();

                switch (response) {
                    case "y":
                    case "yes":
                    case "":
                        ui.println(ConsoleStyle.green("\n✅ 已确认修改，正在执行..."));
                        ui.println();
                        return true;

                    case "n":
                    case "no":
                        ui.println(ConsoleStyle.yellow("\n❌ 已取消修改"));
                        ui.println();
                        return false;

                    case "e":
                    case "detail":
                    case "details":
                        showFullDetails(filePath, oldText, newText);
                        break;

                    case "a":
                    case "always":
                    case "auto":
                        autoConfirmEnabled = true;
                        ui.println(ConsoleStyle.cyan("\n🔓 本次会话自动确认已开启"));
                        ui.println(ConsoleStyle.gray("提示: 使用 /reset 命令可重置自动确认状态"));
                        ui.println();
                        return true;

                    default:
                        ui.println(ConsoleStyle.yellow("\n无效输入，请重新选择"));
                        break;
                }
            }
        } finally {
            spinnerManager.resumeAll();
        }
    }

    private String promptUser() {
        try {
            String prompt = ConsoleStyle.bold("确认执行此修改吗？") + " " +
                    ConsoleStyle.green("[Y] 接受 ") +
                    ConsoleStyle.red("[N] 取消 ") +
                    ConsoleStyle.cyan("[E] 详情 ") +
                    ConsoleStyle.dim("[A] 自动确认: ") + ConsoleStyle.cursor();
            return reader.readLine(prompt);
        } catch (Exception e) {
            return "n";
        }
    }

    private void showFullDetails(String filePath, String oldText, String newText) {
        ui.println("\n" + ConsoleStyle.boldCyan("📋 修改详情"));
        ui.println(ConsoleStyle.gray("─────────────────────────────────────────────────────────────"));
        ui.println("文件: " + ConsoleStyle.cyan(filePath));
        ui.println();
        ui.println(ConsoleStyle.boldRed("--- 修改前 ---"));
        ui.println(ConsoleStyle.gray("共 " + oldText.split("\n", -1).length + " 行, " + oldText.length() + " 字符"));
        ui.println(ConsoleStyle.gray("─────────────────────────────────────────────────────────────"));
        ui.println(oldText);
        ui.println();
        ui.println(ConsoleStyle.boldGreen("+++ 修改后 +++"));
        ui.println(ConsoleStyle.gray("共 " + newText.split("\n", -1).length + " 行, " + newText.length() + " 字符"));
        ui.println(ConsoleStyle.gray("─────────────────────────────────────────────────────────────"));
        ui.println(newText);
        ui.println();
        ui.println(ConsoleStyle.gray("─────────────────────────────────────────────────────────────"));
        ui.println();
    }
}
