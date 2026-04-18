package com.example.agent.lsp.model;

import java.util.List;
import java.util.Map;

public class InitializeParams {

    private Long processId;
    private String rootUri;
    private String rootPath;
    private ClientCapabilities capabilities;
    private List<WorkspaceFolder> workspaceFolders;

    public InitializeParams() {
    }

    public Long getProcessId() {
        return processId;
    }

    public void setProcessId(Long processId) {
        this.processId = processId;
    }

    public String getRootUri() {
        return rootUri;
    }

    public void setRootUri(String rootUri) {
        this.rootUri = rootUri;
    }

    public String getRootPath() {
        return rootPath;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }

    public ClientCapabilities getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(ClientCapabilities capabilities) {
        this.capabilities = capabilities;
    }

    public List<WorkspaceFolder> getWorkspaceFolders() {
        return workspaceFolders;
    }

    public void setWorkspaceFolders(List<WorkspaceFolder> workspaceFolders) {
        this.workspaceFolders = workspaceFolders;
    }

    public static class ClientCapabilities {
        private TextDocumentCapabilities textDocument;
        private WorkspaceCapabilities workspace;

        public ClientCapabilities() {
        }

        public TextDocumentCapabilities getTextDocument() {
            return textDocument;
        }

        public void setTextDocument(TextDocumentCapabilities textDocument) {
            this.textDocument = textDocument;
        }

        public WorkspaceCapabilities getWorkspace() {
            return workspace;
        }

        public void setWorkspace(WorkspaceCapabilities workspace) {
            this.workspace = workspace;
        }
    }

    public static class TextDocumentCapabilities {
        private Map<String, Object> definition;
        private Map<String, Object> references;
        private Map<String, Object> hover;
        private Map<String, Object> documentSymbol;

        public TextDocumentCapabilities() {
        }

        public Map<String, Object> getDefinition() {
            return definition;
        }

        public void setDefinition(Map<String, Object> definition) {
            this.definition = definition;
        }

        public Map<String, Object> getReferences() {
            return references;
        }

        public void setReferences(Map<String, Object> references) {
            this.references = references;
        }

        public Map<String, Object> getHover() {
            return hover;
        }

        public void setHover(Map<String, Object> hover) {
            this.hover = hover;
        }

        public Map<String, Object> getDocumentSymbol() {
            return documentSymbol;
        }

        public void setDocumentSymbol(Map<String, Object> documentSymbol) {
            this.documentSymbol = documentSymbol;
        }
    }

    public static class WorkspaceCapabilities {
        private Map<String, Object> symbol;

        public WorkspaceCapabilities() {
        }

        public Map<String, Object> getSymbol() {
            return symbol;
        }

        public void setSymbol(Map<String, Object> symbol) {
            this.symbol = symbol;
        }
    }
}
