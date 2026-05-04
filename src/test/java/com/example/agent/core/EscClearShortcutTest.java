package com.example.agent.core;

import org.jline.keymap.KeyMap;
import org.jline.reader.LineReader;
import org.jline.reader.Reference;
import org.jline.reader.Widget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ESC 清空输入快捷键测试")
class EscClearShortcutTest {

    @Nested
    @DisplayName("功能测试")
    class FunctionTests {

        private AgentContext context;

        @BeforeEach
        void setUp() throws IOException {
            context = new AgentContext();
        }

        @Test
        @DisplayName("ESC 快捷键已正确绑定")
        void testEscBinding() {
            LineReader reader = context.getReader();
            KeyMap<?> keyMap = reader.getKeyMaps().get(LineReader.MAIN);
            Object binding = keyMap.getBound("\033");

            assertNotNull(binding, "ESC (\\033) 应该被绑定");
            assertEquals("clear-input", ((Reference) binding).name(), "应该绑定到 clear-input widget");
        }

        @Test
        @DisplayName("clear-input widget 能正常清空缓冲区")
        void testClearInputWidget() {
            LineReader reader = context.getReader();
            
            reader.getBuffer().write("test input content");
            assertTrue(reader.getBuffer().length() > 0, "缓冲区应该有内容");

            reader.getBuffer().clear();
            
            assertEquals(0, reader.getBuffer().length(), "缓冲区应该被清空");
        }

        @Test
        @DisplayName("空缓冲区执行 clear-input 不会报错")
        void testClearEmptyBuffer() {
            LineReader reader = context.getReader();
            
            assertEquals(0, reader.getBuffer().length(), "缓冲区初始为空");

            assertDoesNotThrow(() -> reader.getBuffer().clear(), "清空空缓冲区不应抛出异常");
            assertEquals(0, reader.getBuffer().length(), "缓冲区仍然为空");
        }

        @Test
        @DisplayName("连续多次清空操作不会报错")
        void testMultipleClearOperations() {
            LineReader reader = context.getReader();
            
            reader.getBuffer().write("first content");
            reader.getBuffer().clear();
            assertEquals(0, reader.getBuffer().length());

            reader.getBuffer().write("second content");
            reader.getBuffer().clear();
            assertEquals(0, reader.getBuffer().length());

            reader.getBuffer().clear();
            assertEquals(0, reader.getBuffer().length());
        }
    }

    @Nested
    @DisplayName("按键序列验证")
    class KeySequenceTests {

        @Test
        @DisplayName("验证 ESC 序列正确性")
        void testEscSequence() {
            assertEquals("\033", KeyMap.esc(), "ESC 应该对应 ASCII 0x1B");
        }

        @Test
        @DisplayName("验证 ESC 与其他快捷键不冲突")
        void testNoKeyConflict() {
            assertNotEquals("\033", KeyMap.ctrl('B'), "ESC 不应与 Ctrl+B 冲突");
            assertNotEquals("\033", "\001", "ESC 不应与 Ctrl+A 冲突");
            assertNotEquals("\033", "\002", "ESC 不应与 Ctrl+B 冲突");
        }
    }
}
