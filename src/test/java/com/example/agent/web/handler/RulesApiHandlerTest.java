package com.example.agent.web.handler;

import com.example.agent.desktop.WorkspaceContext;
import com.example.agent.logging.WorkspaceManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RulesApiHandler 单元测试")
class RulesApiHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RulesApiHandler handler;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        handler = new RulesApiHandler();
        WorkspaceContext.setCurrentFolder(tempDir.toString());
    }

    @AfterEach
    void tearDown() {
        WorkspaceContext.clear();
    }

    @Nested
    @DisplayName("GET /api/rules/list")
    class ListRulesTests {

        @Test
        @DisplayName("无规则文件时返回空列表")
        void returnsEmptyWhenNoRules() throws IOException {
            WorkspaceContext.setCurrentFolder(tempDir.toString());

            FakeHttpExchange exchange = createExchange("GET", "/api/rules/list");
            handler.handle(exchange);

            assertEquals(200, exchange.getResponseCode());
            Map<String, Object> result = objectMapper.readValue(
                    exchange.getResponseBodyAsString(), new TypeReference<>() {});
            assertNotNull(result.get("projectRules"));
            assertNotNull(result.get("userRules"));
        }

        @Test
        @DisplayName("有项目规则时返回规则列表")
        void returnsProjectRules() throws IOException {
            Path rulesDir = tempDir.resolve(".hippo").resolve("rules");
            Files.createDirectories(rulesDir);
            Files.writeString(rulesDir.resolve("java-style.md"),
                    "---\nmode: always\ndescription: Java 编码规范\n---\n\nuse 4 spaces");
            Files.writeString(rulesDir.resolve("api-design.md"),
                    "---\nmode: manual\ndescription: API 设计\n---\n\nRESTful");

            WorkspaceContext.setCurrentFolder(tempDir.toString());

            FakeHttpExchange exchange = createExchange("GET", "/api/rules/list");
            handler.handle(exchange);

            assertEquals(200, exchange.getResponseCode());
            Map<String, Object> result = objectMapper.readValue(
                    exchange.getResponseBodyAsString(), new TypeReference<>() {});

            List<Map<String, Object>> projectRules = (List<Map<String, Object>>) result.get("projectRules");
            assertEquals(2, projectRules.size());
            assertTrue(projectRules.stream().anyMatch(r -> "java-style.md".equals(r.get("name"))));
            assertTrue(projectRules.stream().anyMatch(r -> "api-design.md".equals(r.get("name"))));
        }

        @Test
        @DisplayName("返回的规则包含完整元数据")
        void returnsMetadata() throws IOException {
            Path rulesDir = tempDir.resolve(".hippo").resolve("rules");
            Files.createDirectories(rulesDir);
            Files.writeString(rulesDir.resolve("test-rule.md"),
                    "---\nmode: manual\ndescription: 测试用\n---\n\ncontent");

            WorkspaceContext.setCurrentFolder(tempDir.toString());

            FakeHttpExchange exchange = createExchange("GET", "/api/rules/list");
            handler.handle(exchange);

            Map<String, Object> result = objectMapper.readValue(
                    exchange.getResponseBodyAsString(), new TypeReference<>() {});
            List<Map<String, Object>> projectRules = (List<Map<String, Object>>) result.get("projectRules");

            assertEquals(1, projectRules.size());
            Map<String, Object> rule = projectRules.get(0);
            assertEquals("test-rule", rule.get("id"));
            assertEquals("test-rule.md", rule.get("name"));
            assertEquals("manual", rule.get("mode"));
            assertEquals("测试用", rule.get("description"));
        }

        @Test
        @DisplayName("OPTIONS 请求返回 204")
        void optionsReturns204() throws IOException {
            FakeHttpExchange exchange = createExchange("OPTIONS", "/api/rules/list");
            handler.handle(exchange);
            assertEquals(204, exchange.getResponseCode());
        }
    }

    @Nested
    @DisplayName("POST /api/rules/create")
    class CreateRuleTests {

        @Test
        @DisplayName("project scope - 成功创建规则文件")
        void createProjectRule() throws IOException {
            Path rulesDir = tempDir.resolve(".hippo").resolve("rules");
            Files.createDirectories(rulesDir);
            WorkspaceContext.setCurrentFolder(tempDir.toString());

            String body = """
                    {"name":"my-rule","mode":"always","description":"我的规则","scope":"project"}
                    """;
            FakeHttpExchange exchange = createExchangeWithBody("POST", "/api/rules/create", body);
            handler.handle(exchange);

            assertEquals(201, exchange.getResponseCode());
            Map<String, Object> result = objectMapper.readValue(
                    exchange.getResponseBodyAsString(), new TypeReference<>() {});
            assertTrue((Boolean) result.get("success"));
            assertTrue(Files.exists(rulesDir.resolve("my-rule.md")));
        }

        @Test
        @DisplayName("user scope - 成功创建全局规则")
        void createUserRule() throws IOException {
            Path originalRoot = WorkspaceManager.getHippoRoot();
            try {
                WorkspaceManager.overrideBasePath(tempDir);

                String uniqueName = "global-rule-" + System.nanoTime();
                String body = """
                        {"name":"$UNIQUE","mode":"manual","description":"全局","scope":"user"}
                        """.replace("$UNIQUE", uniqueName);
                FakeHttpExchange exchange = createExchangeWithBody("POST", "/api/rules/create", body);
                handler.handle(exchange);

                assertEquals(201, exchange.getResponseCode());
                Map<String, Object> result = objectMapper.readValue(
                        exchange.getResponseBodyAsString(), new TypeReference<>() {});
                assertTrue((Boolean) result.get("success"));
            } finally {
                WorkspaceManager.overrideBasePath(originalRoot.getParent());
            }
        }

        @Test
        @DisplayName("project scope - 无工作区返回错误")
        void createProjectRuleNoWorkspace() throws IOException {
            WorkspaceContext.setCurrentFolder(null);

            String body = """
                    {"name":"my-rule","scope":"project"}
                    """;
            FakeHttpExchange exchange = createExchangeWithBody("POST", "/api/rules/create", body);
            handler.handle(exchange);

            assertEquals(400, exchange.getResponseCode());
            Map<String, Object> result = objectMapper.readValue(
                    exchange.getResponseBodyAsString(), new TypeReference<>() {});
            assertFalse((Boolean) result.get("success"));
            assertTrue(((String) result.get("message")).contains("工作区"));
        }

        @Test
        @DisplayName("名称为空时返回 400 错误")
        void createRuleEmptyName() throws IOException {
            String body = """
                    {"name":"","scope":"user"}
                    """;
            FakeHttpExchange exchange = createExchangeWithBody("POST", "/api/rules/create", body);
            handler.handle(exchange);

            assertEquals(400, exchange.getResponseCode());
            Map<String, Object> result = objectMapper.readValue(
                    exchange.getResponseBodyAsString(), new TypeReference<>() {});
            assertFalse((Boolean) result.get("success"));
        }

        @Test
        @DisplayName("文件已存在时返回 400 错误")
        void createRuleFileExists() throws IOException {
            Path rulesDir = tempDir.resolve(".hippo").resolve("rules");
            Files.createDirectories(rulesDir);
            Files.writeString(rulesDir.resolve("existing.md"), "existing");
            WorkspaceContext.setCurrentFolder(tempDir.toString());

            String body = """
                    {"name":"existing","scope":"project"}
                    """;
            FakeHttpExchange exchange = createExchangeWithBody("POST", "/api/rules/create", body);
            handler.handle(exchange);

            assertEquals(400, exchange.getResponseCode());
            Map<String, Object> result = objectMapper.readValue(
                    exchange.getResponseBodyAsString(), new TypeReference<>() {});
            assertFalse((Boolean) result.get("success"));
            assertTrue(((String) result.get("message")).contains("已存在"));
        }

        @Test
        @DisplayName("带自定义内容创建规则")
        void createRuleWithContent() throws IOException {
            Path rulesDir = tempDir.resolve(".hippo").resolve("rules");
            Files.createDirectories(rulesDir);
            WorkspaceContext.setCurrentFolder(tempDir.toString());

            String body = """
                    {"name":"content-rule","mode":"manual","description":"有内容","scope":"project","content":"# 自定义\\n\\n正文"}
                    """;
            FakeHttpExchange exchange = createExchangeWithBody("POST", "/api/rules/create", body);
            handler.handle(exchange);

            assertEquals(201, exchange.getResponseCode());
            String fileContent = Files.readString(rulesDir.resolve("content-rule.md"));
            assertTrue(fileContent.contains("# 自定义"));
        }

        @Test
        @DisplayName("OPTIONS 请求返回 204")
        void optionsReturns204() throws IOException {
            FakeHttpExchange exchange = createExchange("OPTIONS", "/api/rules/create");
            handler.handle(exchange);
            assertEquals(204, exchange.getResponseCode());
        }
    }

    @Nested
    @DisplayName("GET /api/rules/get")
    class GetRuleTests {

        @Test
        @DisplayName("成功读取规则文件内容")
        void readExistingFile() throws IOException {
            Path rulesDir = tempDir.resolve(".hippo").resolve("rules");
            Files.createDirectories(rulesDir);
            Files.writeString(rulesDir.resolve("test.md"), "# 测试规则\n\n内容");
            WorkspaceContext.setCurrentFolder(tempDir.toString());

            String filePath = rulesDir.resolve("test.md").toAbsolutePath().normalize().toString();
            FakeHttpExchange exchange = createExchange("GET",
                    "/api/rules/get?filePath=" + java.net.URLEncoder.encode(filePath, "UTF-8"));
            handler.handle(exchange);

            assertEquals(200, exchange.getResponseCode());
            Map<String, Object> result = objectMapper.readValue(
                    exchange.getResponseBodyAsString(), new TypeReference<>() {});
            assertTrue(((String) result.get("content")).contains("测试规则"));
        }

        @Test
        @DisplayName("缺少 filePath 参数返回 400")
        void missingFilePath() throws IOException {
            FakeHttpExchange exchange = createExchange("GET", "/api/rules/get");
            handler.handle(exchange);
            assertEquals(400, exchange.getResponseCode());
        }

        @Test
        @DisplayName("文件不存在返回 404")
        void fileNotFound() throws IOException {
            String path = tempDir.resolve("nonexistent.md").toAbsolutePath().normalize().toString();
            FakeHttpExchange exchange = createExchange("GET",
                    "/api/rules/get?filePath=" + java.net.URLEncoder.encode(path, "UTF-8"));
            handler.handle(exchange);
            assertEquals(404, exchange.getResponseCode());
        }
    }

    @Nested
    @DisplayName("POST /api/rules/update")
    class UpdateRuleTests {

        @Test
        @DisplayName("成功更新规则内容")
        void updateContent() throws IOException {
            Path rulesDir = tempDir.resolve(".hippo").resolve("rules");
            Files.createDirectories(rulesDir);
            Path file = rulesDir.resolve("test.md");
            Files.writeString(file, "---\nmode: always\n---\n\nold content");
            WorkspaceContext.setCurrentFolder(tempDir.toString());

            String body = """
                    {"filePath":"%s","name":"test","mode":"manual","description":"updated","scope":"project","content":"---\\nmode: manual\\n---\\n\\nnew content"}
                    """.formatted(file.toAbsolutePath().normalize().toString().replace("\\", "\\\\"));
            FakeHttpExchange exchange = createExchangeWithBody("POST", "/api/rules/update", body);
            handler.handle(exchange);

            assertEquals(200, exchange.getResponseCode());
            Map<String, Object> result = objectMapper.readValue(
                    exchange.getResponseBodyAsString(), new TypeReference<>() {});
            assertTrue((Boolean) result.get("success"));

            String fileContent = Files.readString(file);
            assertTrue(fileContent.contains("new content"));
        }

        @Test
        @DisplayName("filePath 为空返回 400")
        void emptyFilePath() throws IOException {
            String body = """
                    {"filePath":"","name":"test","scope":"project","content":""}
                    """;
            FakeHttpExchange exchange = createExchangeWithBody("POST", "/api/rules/update", body);
            handler.handle(exchange);
            assertEquals(400, exchange.getResponseCode());
        }

        @Test
        @DisplayName("name 为空返回 400")
        void emptyName() throws IOException {
            Path rulesDir = tempDir.resolve(".hippo").resolve("rules");
            Files.createDirectories(rulesDir);
            Path file = rulesDir.resolve("test.md");
            Files.writeString(file, "content");
            WorkspaceContext.setCurrentFolder(tempDir.toString());

            String body = """
                    {"filePath":"%s","name":"","scope":"project","content":""}
                    """.formatted(file.toAbsolutePath().normalize().toString().replace("\\", "\\\\"));
            FakeHttpExchange exchange = createExchangeWithBody("POST", "/api/rules/update", body);
            handler.handle(exchange);
            assertEquals(400, exchange.getResponseCode());
        }
    }

    @Nested
    @DisplayName("POST /api/rules/delete")
    class DeleteRuleTests {

        @Test
        @DisplayName("成功删除规则文件")
        void deleteExisting() throws IOException {
            Path rulesDir = tempDir.resolve(".hippo").resolve("rules");
            Files.createDirectories(rulesDir);
            Path file = rulesDir.resolve("to-delete.md");
            Files.writeString(file, "content");
            WorkspaceContext.setCurrentFolder(tempDir.toString());

            String body = """
                    {"filePath":"%s"}
                    """.formatted(file.toAbsolutePath().normalize().toString().replace("\\", "\\\\"));
            FakeHttpExchange exchange = createExchangeWithBody("POST", "/api/rules/delete", body);
            handler.handle(exchange);

            assertEquals(200, exchange.getResponseCode());
            Map<String, Object> result = objectMapper.readValue(
                    exchange.getResponseBodyAsString(), new TypeReference<>() {});
            assertTrue((Boolean) result.get("success"));
            assertFalse(Files.exists(file));
        }

        @Test
        @DisplayName("filePath 为空返回 400")
        void emptyFilePath() throws IOException {
            String body = """
                    {"filePath":""}
                    """;
            FakeHttpExchange exchange = createExchangeWithBody("POST", "/api/rules/delete", body);
            handler.handle(exchange);
            assertEquals(400, exchange.getResponseCode());
        }

        @Test
        @DisplayName("文件不存在返回 404")
        void fileNotFound() throws IOException {
            String path = tempDir.resolve("nonexistent.md").toAbsolutePath().normalize().toString();
            String body = """
                    {"filePath":"%s"}
                    """.formatted(path.replace("\\", "\\\\"));
            FakeHttpExchange exchange = createExchangeWithBody("POST", "/api/rules/delete", body);
            handler.handle(exchange);
            assertEquals(404, exchange.getResponseCode());
        }
    }

    @Nested
    @DisplayName("路由 - 不匹配的路径/方法")
    class RouteTests {

        @Test
        @DisplayName("不存在的路径返回 404")
        void unknownPath() throws IOException {
            FakeHttpExchange exchange = createExchange("GET", "/api/rules/unknown");
            handler.handle(exchange);
            assertEquals(404, exchange.getResponseCode());
        }

        @Test
        @DisplayName("GET /api/rules/create 返回 404")
        void getCreateReturns404() throws IOException {
            FakeHttpExchange exchange = createExchange("GET", "/api/rules/create");
            handler.handle(exchange);
            assertEquals(404, exchange.getResponseCode());
        }

        @Test
        @DisplayName("POST /api/rules/list 返回 404")
        void postListReturns404() throws IOException {
            FakeHttpExchange exchange = createExchangeWithBody("POST", "/api/rules/list", "{}");
            handler.handle(exchange);
            assertEquals(404, exchange.getResponseCode());
        }
    }

    // ==================== FakeHttpExchange ====================

    static class FakeHttpExchange extends HttpExchange {

        private final String requestMethod;
        private final String requestUri;
        private final Headers responseHeaders = new Headers();
        private final Headers requestHeaders = new Headers();
        private final ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        private final ByteArrayInputStream requestBody;
        private int responseCode = -1;
        private long responseLength = -1;
        private boolean closed = false;

        FakeHttpExchange(String requestMethod, String requestUri) {
            this(requestMethod, requestUri, "");
        }

        FakeHttpExchange(String requestMethod, String requestUri, String requestBodyContent) {
            this.requestMethod = requestMethod;
            this.requestUri = requestUri;
            this.requestBody = new ByteArrayInputStream(
                requestBodyContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            requestHeaders.add("Host", "localhost");
        }

        @Override
        public Headers getRequestHeaders() { return requestHeaders; }
        @Override
        public Headers getResponseHeaders() { return responseHeaders; }
        @Override
        public String getRequestMethod() { return requestMethod; }

        @Override
        public URI getRequestURI() {
            return URI.create("http://localhost" + requestUri);
        }

        @Override
        public void sendResponseHeaders(int rCode, long rLen) {
            this.responseCode = rCode;
            this.responseLength = rLen;
        }

        @Override
        public OutputStream getResponseBody() { return responseBody; }
        @Override
        public InputStream getRequestBody() { return requestBody; }
        @Override
        public void close() { this.closed = true; }

        public int getResponseCode() { return responseCode; }

        String getResponseBodyAsString() {
            return responseBody.toString(java.nio.charset.StandardCharsets.UTF_8);
        }

        boolean isClosed() { return closed; }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return new InetSocketAddress("127.0.0.1", 12345);
        }

        @Override
        public InetSocketAddress getLocalAddress() {
            return new InetSocketAddress("127.0.0.1", 8080);
        }

        @Override
        public String getProtocol() { return "HTTP/1.1"; }
        @Override
        public Object getAttribute(String name) { return null; }
        @Override
        public void setAttribute(String name, Object value) {}
        @Override
        public void setStreams(InputStream i, OutputStream o) {}
        @Override
        public HttpPrincipal getPrincipal() { return null; }
        @Override
        public HttpContext getHttpContext() { return null; }
    }

    private static FakeHttpExchange createExchange(String method, String uri) {
        return new FakeHttpExchange(method, uri);
    }

    private static FakeHttpExchange createExchangeWithBody(String method, String uri, String body) {
        return new FakeHttpExchange(method, uri, body);
    }
}
