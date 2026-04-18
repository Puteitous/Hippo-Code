package com.example.agent.lsp.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LspConfig {

    private boolean enabled = true;
    private Map<String, LspServerConfig> servers = new HashMap<>();

    public LspConfig() {
        LspServerConfig javaConfig = new LspServerConfig();
        javaConfig.setEnabled(true);
        javaConfig.setCommand("jdtls");
        javaConfig.setArgs(List.of(
            "-configuration",
            System.getProperty("user.home") + "\\.jdtls\\config",
            "-data",
            System.getProperty("user.home") + "\\.jdtls\\workspace"
        ));
        servers.put("java", javaConfig);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, LspServerConfig> getServers() {
        return servers;
    }

    public void setServers(Map<String, LspServerConfig> servers) {
        this.servers = servers;
    }

    public LspServerConfig getServer(String languageId) {
        return servers.get(languageId);
    }

    public boolean isServerEnabled(String languageId) {
        LspServerConfig config = servers.get(languageId);
        return config != null && config.isEnabled();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LspServerConfig {
        private boolean enabled = true;
        private String command;
        private List<String> args = new ArrayList<>();
        private Map<String, String> env = new HashMap<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCommand() {
            return command;
        }

        public void setCommand(String command) {
            this.command = command;
        }

        public List<String> getArgs() {
            return args;
        }

        public void setArgs(List<String> args) {
            this.args = args;
        }

        public Map<String, String> getEnv() {
            return env;
        }

        public void setEnv(Map<String, String> env) {
            this.env = env;
        }
    }
}
