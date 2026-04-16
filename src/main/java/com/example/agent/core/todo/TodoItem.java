package com.example.agent.core.todo;

public class TodoItem {

    private final String id;
    private String content;
    private TodoStatus status;

    public TodoItem(String id, String content, TodoStatus status) {
        this.id = id;
        this.content = content;
        this.status = status != null ? status : TodoStatus.PENDING;
    }

    public TodoItem(String id, String content) {
        this(id, content, TodoStatus.PENDING);
    }

    public String getId() {
        return id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public TodoStatus getStatus() {
        return status;
    }

    public void setStatus(TodoStatus status) {
        this.status = status;
    }

    public void merge(TodoItem other) {
        if (other.content != null && !other.content.isEmpty()) {
            this.content = other.content;
        }
        if (other.status != null) {
            this.status = other.status;
        }
    }
}
