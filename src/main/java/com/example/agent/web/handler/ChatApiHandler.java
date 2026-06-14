package com.example.agent.web.handler;

import com.example.agent.application.ConversationService;
import com.example.agent.core.di.ServiceLocator;
import com.example.agent.desktop.WorkspaceContext;
import com.example.agent.domain.conversation.Conversation;
import com.example.agent.llm.exception.LlmException;
import com.example.agent.llm.model.Message;
import com.example.agent.llm.model.Ref;
import com.example.agent.service.TokenEstimatorFactory;
import com.example.agent.web.logging.SessionLogger;
import com.example.agent.web.orchestrator.WebAgentOrchestrator;
import com.example.agent.web.session.SessionCancelManager;
import com.example.agent.web.server.WebInitializer;
import com.example.agent.web.session.PendingBashConfirmation;
import com.example.agent.web.session.PendingDeleteConfirmation;
import com.example.agent.web.session.PendingToolCall;
import com.example.agent.web.session.SessionManager;
import com.example.agent.web.session.SessionTokenStats;
import com.example.agent.web.session.WebSessionManager;
import com.example.agent.web.util.SseWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ChatApiHandler implements HttpHandler {

    private static final Logger logger = LoggerFactory.getLogger(ChatApiHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final SessionManager sessionManager;
    private final WebAgentOrchestrator orchestrator;

    public ChatApiHandler() {
        this.sessionManager = WebSessionManager.getInstance();
        this.orchestrator = WebAgentOrchestrator.getInstance();
    }

    ChatApiHandler(SessionManager sessionManager, WebAgentOrchestrator orchestrator) {
        this.sessionManager = sessionManager;
        this.orchestrator = orchestrator;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            String response = "{\"error\":\"Method not allowed\"}";
            exchange.sendResponseHeaders(405, response.getBytes(StandardCharsets.UTF_8).length);
            exchange.getResponseBody().write(response.getBytes(StandardCharsets.UTF_8));
            exchange.close();
            return;
        }

        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, 0);

        // 直接使用 OutputStreamWriter，不包装 BufferedWriter。
        // SSE 事件每次 write 后立即 flush，无需缓冲层。
        // BufferedWriter 的缓冲在 SSE 场景下从不被利用。
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(exchange.getResponseBody(), StandardCharsets.UTF_8);
        SseWriter sseWriter = new SseWriter(outputStreamWriter);

        String sessionId = null;
        boolean lockAcquired = false;

        try {
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            JsonNode json = objectMapper.readTree(requestBody);

            sessionId = json.has("sessionId") ? json.get("sessionId").asText() :
                       (json.has("session") ? json.get("session").asText() : "default");
            String userMessage = json.has("message") ? json.get("message").asText() : "";
            String systemPromptOverride = json.has("systemPrompt") ? json.get("systemPrompt").asText() : null;
            String editMessageId = json.has("editMessageId") ? json.get("editMessageId").asText() : null;

            // 解析 refs（结构化引用）并为 LLM 构建增强消息（注入文件/文本上下文）
            String workspacePath = WorkspaceContext.getCurrentFolder();
            List<Ref> refs = parseRefs(json);
            String enhancedMessage = buildEnhancedMessage(userMessage, refs, workspacePath);

            if (userMessage.isEmpty()) {
                sseWriter.sendSseEvent("error", "{\"message\":\"消息不能为空\"}");
                return;
            }

            try {
                sessionManager.tryAcquireSessionLock(sessionId, 30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                sseWriter.sendSseEvent("error", "{\"message\":\"请求被中断\"}");
                return;
            }
            lockAcquired = true;

            SseWriter.resetClientDisconnected();
            // 清理上一轮可能残留的取消标志，避免旧信号影响新请求
            SessionCancelManager.getInstance().reset(sessionId);

            logger.info("Web Chat 收到消息：session={}, message={}, edit={}, hasPendingTool={}",
                sessionId, userMessage, editMessageId != null, sessionManager.hasPendingToolCall(sessionId));

            int estimatedTokens = TokenEstimatorFactory.getDefault().estimateTextTokens(enhancedMessage);
            SessionLogger.logUserMessage(sessionId, Message.user(enhancedMessage), estimatedTokens);

            WebInitializer.ensureMemoryInitialized();

            Conversation conversation = sessionManager.getOrCreateConversation(sessionId, systemPromptOverride);
            ConversationService conversationService = ServiceLocator.get(ConversationService.class);

            PendingToolCall pendingTool = sessionManager.pollPendingToolCall(sessionId);
            if (pendingTool != null) {
                String toolResult = "用户回答：" + userMessage;
                conversationService.addToolResult(conversation, pendingTool.toolCallId, pendingTool.toolName, toolResult, true);
                SessionLogger.logToolCall(sessionId, pendingTool.toolName, pendingTool.question, toolResult, true);
                SessionTokenStats stats = sessionManager.getOrCreateSessionTokenStats(sessionId);
                stats.addToolCall();

                Message userMsg = createUserMessage(enhancedMessage, refs);
                conversationService.addMessage(conversation, userMsg);
                sseWriter.sendSseEvent("message_id", "{\"id\":\"" + userMsg.getId() + "\"}");
            } else if (editMessageId != null && !editMessageId.isEmpty()) {
                Message userMsg = conversationService.editUserMessage(conversation, editMessageId, enhancedMessage);
                if (userMsg != null) {
                    sseWriter.sendSseEvent("message_id", "{\"id\":\"" + userMsg.getId() + "\"}");
                }
            } else {
                // 新消息到达时，自动清理挂起的确认（用户忽略了确认框）
                PendingBashConfirmation stalePending = sessionManager.pollPendingBashConfirmation(sessionId);
                if (stalePending != null) {
                    logger.info("新消息到达，自动清理挂起的 bash 确认：confirmId={}, command={}",
                        stalePending.confirmId, stalePending.command);
                }
                PendingDeleteConfirmation staleDeletePending = sessionManager.pollPendingDeleteConfirmation(sessionId);
                if (staleDeletePending != null) {
                    logger.info("新消息到达，自动清理挂起的 delete_file 确认：confirmId={}",
                        staleDeletePending.confirmId);
                }

                Message userMsg = createUserMessage(enhancedMessage, refs);
                conversationService.addMessage(conversation, userMsg);
                sseWriter.sendSseEvent("message_id", "{\"id\":\"" + userMsg.getId() + "\"}");
            }

            orchestrator.execute(sessionId, conversation, sseWriter);

        } catch (LlmException e) {
            logger.error("LLM 调用失败", e);
            sseWriter.sendSseEvent("error", "{\"message\":\"" + SseWriter.escapeJson(e.getMessage()) + "\"}");
        } catch (Exception e) {
            logger.error("处理聊天请求失败", e);
            sseWriter.sendSseEvent("error", "{\"message\":\"" + SseWriter.escapeJson(e.getMessage()) + "\"}");
        } finally {
            if (lockAcquired && sessionId != null) {
                sessionManager.releaseSessionLock(sessionId);
            }
            SseWriter.removeClientDisconnected();
            sseWriter.sendSseEvent("complete", "[DONE]");
            outputStreamWriter.close();
            exchange.close();
        }
    }

    /**
     * 从请求 JSON 中解析 refs 数组
     */
    private List<Ref> parseRefs(JsonNode json) {
        if (!json.has("refs") || !json.get("refs").isArray()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(
                json.get("refs").traverse(),
                new TypeReference<List<Ref>>() {}
            );
        } catch (Exception e) {
            logger.warn("解析 refs 失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 根据 refs 构建上下文注入后的增强消息。
     * 文件引用：读取文件内容嵌入上下文。文本引用：直接嵌入。
     * path 校验：防止路径穿越。
     */
    private String buildEnhancedMessage(String userMessage, List<Ref> refs, String workspacePath) {
        if (refs == null || refs.isEmpty()) {
            return userMessage;
        }

        StringBuilder context = new StringBuilder();
        int refIndex = 0;

        for (Ref ref : refs) {
            if ("file".equals(ref.getType()) && ref.getPath() != null) {
                String content = readFileContentSafely(ref.getPath(), workspacePath, ref.getStartLine(), ref.getEndLine());
                if (content != null) {
                    if (refIndex > 0) context.append('\n');
                    String fileName = ref.getPath().contains("/")
                        ? ref.getPath().substring(ref.getPath().lastIndexOf('/') + 1)
                        : ref.getPath();
                    if (ref.getStartLine() != null && ref.getEndLine() != null) {
                        context.append("--- ").append(fileName).append(" (lines ").append(ref.getStartLine()).append('-').append(ref.getEndLine()).append(") ---\n");
                    } else {
                        context.append("--- ").append(fileName).append(" ---\n");
                    }
                    context.append(content);
                    refIndex++;
                }
            } else if ("text".equals(ref.getType()) && ref.getText() != null) {
                if (refIndex > 0) context.append('\n');
                context.append("--- 引用文本 ---\n");
                context.append(ref.getText());
                refIndex++;
            }
        }

        if (refIndex == 0) {
            return userMessage;
        }

        return context.toString() + "\n\n" + userMessage;
    }

    /**
     * 安全地读取文件内容，带路径穿越校验。
     */
    private String readFileContentSafely(String filePath, String workspacePath, Integer startLine, Integer endLine) {
        if (filePath == null || workspacePath == null || workspacePath.isBlank()) {
            return null;
        }

        try {
            // 规范化路径并校验是否在 workspace 内
            Path basePath = Paths.get(workspacePath).toRealPath();
            Path targetPath = basePath.resolve(filePath).normalize();

            if (!targetPath.startsWith(basePath)) {
                logger.warn("路径穿越拦截：filePath={}, resolved={}", filePath, targetPath);
                return "[路径访问被拒绝]";
            }

            if (!Files.exists(targetPath) || !Files.isRegularFile(targetPath)) {
                logger.warn("文件不存在：{}", targetPath);
                return null;
            }

            List<String> lines = Files.readAllLines(targetPath, StandardCharsets.UTF_8);

            if (startLine != null && endLine != null && startLine > 0 && endLine >= startLine) {
                int from = Math.min(startLine - 1, lines.size());
                int to = Math.min(endLine, lines.size());
                return lines.subList(from, to).stream().collect(Collectors.joining("\n"));
            }

            return String.join("\n", lines);
        } catch (Exception e) {
            logger.warn("读取 ref 文件失败：path={}", filePath, e);
            return null;
        }
    }

    /**
     * 创建用户消息，同时附加结构化 refs
     */
    private static Message createUserMessage(String content, List<Ref> refs) {
        Message msg = Message.user(content);
        if (refs != null && !refs.isEmpty()) {
            msg.setRefs(refs);
        }
        return msg;
    }
}
