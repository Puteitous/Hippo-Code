package com.example.agent.lsp.tools;

import com.example.agent.lsp.LspClient;
import com.example.agent.tools.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class LspBaseTool implements ToolExecutor {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected final LspClient lspClient;
    protected final String languageId;

    protected LspBaseTool(LspClient lspClient, String languageId) {
        this.lspClient = lspClient;
        this.languageId = languageId;
    }

    protected Throwable unwrapException(Throwable e) {
        while ((e instanceof java.util.concurrent.ExecutionException
                || e instanceof java.util.concurrent.CompletionException
                || e.getCause() != null)
                && e.getCause() != null) {
            e = e.getCause();
        }
        return e;
    }

    @Override
    public boolean shouldRunInBackground() {
        return true;
    }
}
