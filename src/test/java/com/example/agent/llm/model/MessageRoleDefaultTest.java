package com.example.agent.llm.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Message role默认值测试")
class MessageRoleDefaultTest {

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTests {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("null/空/空白role使用user作为默认值")
        void testNullRoleDefaultsToUser(String role) {
            Message message = new Message(role, "content");
            
            assertEquals("user", message.getRole(), 
                "role为[" + role + "]时应该使用'user'作为默认值");
        }

        @Test
        @DisplayName("有效role保持不变")
        void testValidRolePreserved() {
            Message message = new Message("system", "content");
            assertEquals("system", message.getRole());

            message = new Message("assistant", "content");
            assertEquals("assistant", message.getRole());

            message = new Message("tool", "content");
            assertEquals("tool", message.getRole());
        }

        @Test
        @DisplayName("role前后空白被trim")
        void testRoleTrimmed() {
            Message message = new Message("  user  ", "content");
            assertEquals("user", message.getRole());
        }

        @Test
        @DisplayName("factory方法使用正确role")
        void testFactoryMethods() {
            assertEquals("system", Message.system("test").getRole());
            assertEquals("user", Message.user("test").getRole());
            assertEquals("assistant", Message.assistant("test").getRole());
            assertEquals("assistant", Message.assistantWithToolCalls(null).getRole());
            assertEquals("tool", Message.toolResult("call-123", "func", "result").getRole());
        }
    }

    @Nested
    @DisplayName("setter方法测试")
    class SetterTests {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("setRole null/空值使用user作为默认值")
        void testSetRoleNullDefaultsToUser(String role) {
            Message message = new Message();
            message.setRole(role);
            
            assertEquals("user", message.getRole(), 
                "setRole(" + role + ")应该回退到'user'");
        }

        @Test
        @DisplayName("setRole有效值正确设置")
        void testSetRoleValidValue() {
            Message message = new Message();
            message.setRole("system");
            assertEquals("system", message.getRole());

            message.setRole("  assistant  ");
            assertEquals("assistant", message.getRole());
        }
    }

    @Nested
    @DisplayName("🔴 API兼容性验证")
    class ApiCompatibilityTests {

        @Test
        @DisplayName("默认值不是unknown而是标准API兼容值")
        void testDefaultIsNotUnknown() {
            Message message = new Message(null, "test");
            
            assertNotEquals("unknown", message.getRole(), 
                "默认值不应该是'unknown'，LLM API不识别");
            assertTrue(
                List.of("system", "user", "assistant", "tool").contains(message.getRole()),
                "默认值必须是LLM API识别的标准role之一"
            );
        }

        @Test
        @DisplayName("user是最安全的fallback值")
        void testUserIsSafeFallback() {
            Message message = new Message("", "test");
            
            assertEquals("user", message.getRole(),
                "'user'是LLM API最通用的role");
        }
    }

    @Nested
    @DisplayName("内容处理测试")
    class ContentTests {

        @Test
        @DisplayName("null content转为空字符串")
        void testNullContentBecomesEmpty() {
            Message message = new Message("user", null);
            assertEquals("", message.getContent());
        }

        @Test
        @DisplayName("setContent null转为空字符串")
        void testSetNullContentBecomesEmpty() {
            Message message = new Message();
            message.setContent(null);
            assertEquals("", message.getContent());
        }
    }
}
