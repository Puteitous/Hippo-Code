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

@DisplayName("模式切换快捷键测试")
class ModeSwitchShortcutTest {

    @Nested
    @DisplayName("功能测试")
    class FunctionTests {

        private AgentContext context;

        @BeforeEach
        void setUp() throws IOException {
            context = new AgentContext();
        }

        @Test
        @DisplayName("Ctrl+B 快捷键已正确绑定")
        void testCtrlBBinding() {
            LineReader reader = context.getReader();
            KeyMap<?> keyMap = reader.getKeyMaps().get(LineReader.MAIN);
            Object binding = keyMap.getBound("\002");

            assertNotNull(binding, "Ctrl+B (\\002) 应该被绑定");
            assertEquals("toggle-mode", ((Reference) binding).name(), "应该绑定到 toggle-mode widget");
        }

        @Test
        @DisplayName("toggle-mode widget 能正常切换模式")
        void testToggleModeWidget() {
            Widget toggleWidget = context.getReader().getWidgets().get("toggle-mode");
            AgentMode initialMode = context.getCurrentMode();

            boolean result = toggleWidget.apply();
            AgentMode newMode = context.getCurrentMode();

            assertTrue(result, "Widget 执行应该返回 true");
            assertNotSame(initialMode, newMode, "执行后模式应该改变");

            AgentMode expectedMode = (initialMode == AgentMode.CHAT) 
                ? AgentMode.BUILDER 
                : AgentMode.CHAT;
            assertEquals(expectedMode, newMode, "模式切换方向正确");
        }

        @Test
        @DisplayName("连续切换两次应该回到原模式")
        void testToggleTwice() {
            Widget toggleWidget = context.getReader().getWidgets().get("toggle-mode");
            AgentMode initialMode = context.getCurrentMode();

            toggleWidget.apply();
            toggleWidget.apply();
            
            assertEquals(initialMode, context.getCurrentMode(), "切换两次应该回到原模式");
        }
    }

    @Nested
    @DisplayName("按键序列验证")
    class KeySequenceTests {

        @Test
        @DisplayName("验证 Ctrl+B 序列正确性")
        void testCtrlBSequence() {
            assertEquals("\002", KeyMap.ctrl('B'), "Ctrl+B 应该对应 ASCII 0x02");
        }

        @Test
        @DisplayName("验证按键序列生成工具")
        void testKeyMapHelpers() {
            assertEquals("\033", KeyMap.esc());
            assertEquals("\001", KeyMap.ctrl('A'));
        }
    }
}
