package com.example.agent.lsp.tools;

import com.example.agent.lsp.LspClient;
import com.example.agent.tools.ToolExecutor;

public abstract class LspBaseTool implements ToolExecutor {

    protected final LspClient lspClient;
    protected final String languageId;

    protected LspBaseTool(LspClient lspClient, String languageId) {
        this.lspClient = lspClient;
        this.languageId = languageId;
    }

    @Override
    public boolean shouldRunInBackground() {
        return true;
    }
}
