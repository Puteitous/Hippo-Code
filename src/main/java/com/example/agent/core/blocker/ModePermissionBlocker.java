package com.example.agent.core.blocker;

import com.example.agent.core.AgentContext;
import com.example.agent.core.AgentMode;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModePermissionBlocker implements Blocker {

    private static final Logger logger = LoggerFactory.getLogger(ModePermissionBlocker.class);

    private final AgentContext context;

    public ModePermissionBlocker(AgentContext context) {
        this.context = context;
    }

    @Override
    public HookResult check(String toolName, JsonNode arguments) {
        if (toolName == null) {
            return HookResult.allow();
        }

        AgentMode mode = context.getCurrentMode();
        if (!mode.isToolAllowed(toolName)) {
            String message = String.format(
                "[%s] 模式下不允许使用工具 '%s'，请输入 /coding 切换到编程模式",
                mode.getDisplayName(), toolName
            );
            
            logger.warn("工具权限拦截: {} - {}", mode, toolName);
            return HookResult.block(message);
        }

        return HookResult.allow();
    }
}