package com.example.agent.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class InitializeResult {

    private ServerInfo serverInfo;
    private String protocolVersion;
    private Capabilities capabilities;

    public InitializeResult() {
    }

    public ServerInfo getServerInfo() {
        return serverInfo;
    }

    public void setServerInfo(ServerInfo serverInfo) {
        this.serverInfo = serverInfo;
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(String protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public Capabilities getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(Capabilities capabilities) {
        this.capabilities = capabilities;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ServerInfo {
        private String name;
        private String version;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Capabilities {
        private ToolsCapability tools;

        public ToolsCapability getTools() {
            return tools;
        }

        public void setTools(ToolsCapability tools) {
            this.tools = tools;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ToolsCapability {
        private boolean listChanged;

        public boolean isListChanged() {
            return listChanged;
        }

        public void setListChanged(boolean listChanged) {
            this.listChanged = listChanged;
        }
    }
}
