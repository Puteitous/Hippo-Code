package com.example.agent.web.handler;

import com.example.agent.desktop.WorkspaceContext;
import com.example.agent.domain.rule.RuleLoader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 规则管理 API。
 * <p>
 * GET  /api/rules/list   — 返回项目级 + 用户级规则列表（含元数据和文件路径）
 * GET  /api/rules/get    — 读取单个规则文件内容（?filePath=xxx）
 * POST /api/rules/create — 新建规则文件
 * POST /api/rules/update — 更新规则（改名、改作用域、改内容）
 * POST /api/rules/delete — 删除规则文件
 * </p>
 */
public class RulesApiHandler implements HttpHandler {

    private static final Logger logger = LoggerFactory.getLogger(RulesApiHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        try {
            if ("GET".equals(method) && path.endsWith("/list")) {
                handleList(exchange);
            } else if ("GET".equals(method) && path.endsWith("/get")) {
                handleGet(exchange);
            } else if ("POST".equals(method) && path.endsWith("/create")) {
                handleCreate(exchange);
            } else if ("POST".equals(method) && path.endsWith("/update")) {
                handleUpdate(exchange);
            } else if ("POST".equals(method) && path.endsWith("/delete")) {
                handleDelete(exchange);
            } else {
                sendJson(exchange, 404, "{\"error\":\"Not found: " + path + "\"}");
            }
        } catch (Exception e) {
            logger.error("Rules API error", e);
            sendJson(exchange, 500, "{\"error\":\"Internal server error\"}");
        }
    }

    private void handleList(HttpExchange exchange) throws IOException {
        String workspacePath = WorkspaceContext.getCurrentFolder();
        List<RuleLoader.RuleInfo> rules = RuleLoader.getRuleList(workspacePath);

        ObjectNode root = MAPPER.createObjectNode();
        ArrayNode projectArray = MAPPER.createArrayNode();
        ArrayNode userArray = MAPPER.createArrayNode();

        for (RuleLoader.RuleInfo rule : rules) {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("id", rule.getId());
            node.put("name", rule.getName());
            node.put("description", rule.getDescription());
            node.put("mode", rule.getMode());
            node.put("filePath", rule.getFilePath());

            if ("project".equals(rule.getSource())) {
                projectArray.add(node);
            } else {
                userArray.add(node);
            }
        }

        root.set("projectRules", projectArray);
        root.set("userRules", userArray);

        sendJson(exchange, 200, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root));
    }

    private void handleGet(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String filePath = null;
        if (query != null) {
            for (String param : query.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2 && "filePath".equals(kv[0])) {
                    filePath = java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                }
            }
        }

        if (filePath == null || filePath.isBlank()) {
            sendJson(exchange, 400, "{\"error\":\"Missing filePath parameter\"}");
            return;
        }

        String content = RuleLoader.readRuleContentByPath(filePath);
        if (content == null) {
            sendJson(exchange, 404, "{\"error\":\"File not found\"}");
            return;
        }

        ObjectNode resp = MAPPER.createObjectNode();
        resp.put("filePath", filePath);
        resp.put("content", content);
        sendJson(exchange, 200, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(resp));
    }

    private void handleCreate(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        JsonNode json = MAPPER.readTree(body);

        String name = json.has("name") ? json.get("name").asText().trim() : "";
        String mode = json.has("mode") ? json.get("mode").asText().trim() : "always";
        String description = json.has("description") ? json.get("description").asText().trim() : "";
        String scope = json.has("scope") ? json.get("scope").asText().trim() : "project";
        String content = json.has("content") ? json.get("content").asText() : null;

        String workspacePath = WorkspaceContext.getCurrentFolder();
        RuleLoader.CreateRuleResult result = RuleLoader.createRuleFile(
                name, mode, description, scope, content, workspacePath);

        ObjectNode resp = MAPPER.createObjectNode();
        resp.put("success", result.isSuccess());
        resp.put("message", result.getMessage());
        if (result.getFilePath() != null) {
            resp.put("filePath", result.getFilePath().toString());
        }

        int status = result.isSuccess() ? 201 : 400;
        sendJson(exchange, status, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(resp));
    }

    private void handleUpdate(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        JsonNode json = MAPPER.readTree(body);

        String oldFilePath = json.has("filePath") ? json.get("filePath").asText().trim() : "";
        String name = json.has("name") ? json.get("name").asText().trim() : "";
        String mode = json.has("mode") ? json.get("mode").asText().trim() : "always";
        String description = json.has("description") ? json.get("description").asText().trim() : "";
        String scope = json.has("scope") ? json.get("scope").asText().trim() : "project";
        String content = json.has("content") ? json.get("content").asText() : "";

        if (oldFilePath.isBlank()) {
            sendJson(exchange, 400, "{\"success\":false,\"message\":\"filePath 不能为空\"}");
            return;
        }
        if (name.isBlank()) {
            sendJson(exchange, 400, "{\"success\":false,\"message\":\"规则名称不能为空\"}");
            return;
        }

        String workspacePath = WorkspaceContext.getCurrentFolder();
        RuleLoader.UpdateRuleResult result = RuleLoader.updateRuleFile(
                oldFilePath, name, mode, description, scope, content, workspacePath);

        ObjectNode resp = MAPPER.createObjectNode();
        resp.put("success", result.isSuccess());
        resp.put("message", result.getMessage());
        if (result.getFilePath() != null) {
            resp.put("filePath", result.getFilePath());
        }

        int status = result.isSuccess() ? 200 : 400;
        sendJson(exchange, status, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(resp));
    }

    private void handleDelete(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        JsonNode json = MAPPER.readTree(body);

        String filePath = json.has("filePath") ? json.get("filePath").asText().trim() : "";
        if (filePath.isBlank()) {
            sendJson(exchange, 400, "{\"success\":false,\"message\":\"filePath 不能为空\"}");
            return;
        }

        boolean deleted = RuleLoader.deleteRuleFile(filePath);
        ObjectNode resp = MAPPER.createObjectNode();
        resp.put("success", deleted);
        resp.put("message", deleted ? "规则已删除" : "文件不存在");

        int status = deleted ? 200 : 404;
        sendJson(exchange, status, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(resp));
    }

    private void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }
}
