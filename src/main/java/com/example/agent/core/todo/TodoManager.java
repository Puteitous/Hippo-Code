package com.example.agent.core.todo;

import com.example.agent.console.AgentUi;
import com.example.agent.console.ConsoleStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public class TodoManager {

    private static final Logger logger = LoggerFactory.getLogger(TodoManager.class);

    private final List<TodoItem> todos;

    public TodoManager() {
        this.todos = new CopyOnWriteArrayList<>();
    }

    public void replaceAll(List<Map<String, Object>> items) {
        todos.clear();
        for (Map<String, Object> item : items) {
            String id = (String) item.get("id");
            String content = (String) item.get("content");
            String statusKey = (String) item.get("status");
            TodoStatus status = TodoStatus.fromKey(statusKey);
            todos.add(new TodoItem(id, content, status));
        }
        logger.debug("Todo 列表已替换: {} 项", todos.size());
    }

    public void mergeUpdates(List<Map<String, Object>> items) {
        for (Map<String, Object> item : items) {
            String id = (String) item.get("id");
            String content = (String) item.get("content");
            String statusKey = (String) item.get("status");
            TodoStatus status = TodoStatus.fromKey(statusKey);

            Optional<TodoItem> existing = findById(id);
            if (existing.isPresent()) {
                TodoItem todo = existing.get();
                if (content != null && !content.isEmpty()) {
                    todo.setContent(content);
                }
                if (status != null) {
                    todo.setStatus(status);
                }
                logger.debug("Todo 已更新: id={}, status={}", id, status);
            } else {
                todos.add(new TodoItem(id, content, status));
                logger.debug("Todo 已添加: id={}", id);
            }
        }
    }

    public Optional<TodoItem> findById(String id) {
        return todos.stream()
                .filter(todo -> todo.getId().equals(id))
                .findFirst();
    }

    public void clear() {
        todos.clear();
        logger.debug("Todo 列表已清空");
    }

    public List<TodoItem> getAll() {
        return new ArrayList<>(todos);
    }

    public boolean isEmpty() {
        return todos.isEmpty();
    }

    public int size() {
        return todos.size();
    }

    public long countByStatus(TodoStatus status) {
        return todos.stream()
                .filter(todo -> todo.getStatus() == status)
                .count();
    }

    public void renderToUi(AgentUi ui) {
        if (todos.isEmpty() || ui == null) {
            return;
        }

        ui.println();
        ui.println(ConsoleStyle.gray("══════════════════ 任务清单 ═════════════════"));

        for (TodoItem todo : todos) {
            String icon = todo.getStatus().getIcon();
            String line = String.format("  %s  %s", icon, todo.getContent());

            if (todo.getStatus() == TodoStatus.COMPLETED) {
                ui.println(ConsoleStyle.gray(line));
            } else if (todo.getStatus() == TodoStatus.IN_PROGRESS) {
                ui.println(ConsoleStyle.yellow(line));
            } else {
                ui.println(line);
            }
        }

        long completed = countByStatus(TodoStatus.COMPLETED);
        long total = todos.size();
        String summary = String.format("  进度: %d/%d 已完成", completed, total);
        ui.println();
        ui.println(ConsoleStyle.cyan(summary));
        ui.println(ConsoleStyle.gray("═══════════════════════════════════════════════"));
        ui.println();
    }

    public String formatAsMarkdown() {
        if (todos.isEmpty()) {
            return "暂无任务";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## 任务清单\n\n");

        for (TodoItem todo : todos) {
            String checkbox = todo.getStatus() == TodoStatus.COMPLETED ? "- [x]" : "- [ ]";
            sb.append(String.format("%s %s\n", checkbox, todo.getContent()));
        }

        long completed = countByStatus(TodoStatus.COMPLETED);
        sb.append(String.format("\n**进度: %d/%d 已完成**\n", completed, todos.size()));
        return sb.toString();
    }
}
