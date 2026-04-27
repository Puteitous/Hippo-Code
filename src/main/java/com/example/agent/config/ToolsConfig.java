package com.example.agent.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ToolsConfig {

    private BashToolConfig bash = new BashToolConfig();
    private FileToolConfig file = new FileToolConfig();

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BashToolConfig {
        private boolean enabled = true;
        private List<String> whitelist = new ArrayList<>();
        
        @JsonProperty("require_confirmation")
        private boolean requireConfirmation = true;

        public BashToolConfig() {
            whitelist.add("git");
            whitelist.add("mvn");
            whitelist.add("npm");
            whitelist.add("docker");
            whitelist.add("ls");
            whitelist.add("cat");
            whitelist.add("grep");
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getWhitelist() {
            return whitelist;
        }

        public void setWhitelist(List<String> whitelist) {
            this.whitelist = whitelist;
        }

        public boolean isRequireConfirmation() {
            return requireConfirmation;
        }

        public void setRequireConfirmation(boolean requireConfirmation) {
            this.requireConfirmation = requireConfirmation;
        }

        public boolean isCommandAllowed(String command) {
            if (!enabled) {
                return false;
            }
            if (whitelist == null || whitelist.isEmpty()) {
                return true;
            }
            String trimmedCommand = command.trim();
            if (trimmedCommand.isEmpty()) {
                return false;
            }
            if (trimmedCommand.contains(";") || trimmedCommand.contains("&&") || 
                trimmedCommand.contains("||") || trimmedCommand.contains("|") ||
                trimmedCommand.contains(">") || trimmedCommand.contains("`") || 
                trimmedCommand.contains("$(")) {
                return false;
            }
            String baseCommand = trimmedCommand.split("\\s+")[0];
            return whitelist.contains(baseCommand);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FileToolConfig {
        private boolean enabled = true;
        
        @JsonProperty("allowed_paths")
        private List<String> allowedPaths = new ArrayList<>();
        
        @JsonProperty("max_file_size")
        private String maxFileSize = "10MB";
        
        @JsonProperty("blocked_extensions")
        private List<String> blockedExtensions = new ArrayList<>();

        public FileToolConfig() {
            allowedPaths.add(".");
            blockedExtensions.add(".env");
            blockedExtensions.add(".pem");
            blockedExtensions.add(".key");
            blockedExtensions.add(".p12");
            blockedExtensions.add(".jks");
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getAllowedPaths() {
            return allowedPaths;
        }

        public void setAllowedPaths(List<String> allowedPaths) {
            this.allowedPaths = allowedPaths;
        }

        public String getMaxFileSize() {
            return maxFileSize;
        }

        public void setMaxFileSize(String maxFileSize) {
            this.maxFileSize = maxFileSize;
        }

        public List<String> getBlockedExtensions() {
            return blockedExtensions;
        }

        public void setBlockedExtensions(List<String> blockedExtensions) {
            this.blockedExtensions = blockedExtensions;
        }

        public long getMaxFileSizeBytes() {
            return parseFileSize(maxFileSize);
        }

        private long parseFileSize(String size) {
            if (size == null || size.isEmpty()) {
                return 10 * 1024 * 1024;
            }
            try {
                size = size.toUpperCase().trim();
                if (size.endsWith("GB")) {
                    return Long.parseLong(size.replace("GB", "").trim()) * 1024 * 1024 * 1024;
                } else if (size.endsWith("MB")) {
                    return Long.parseLong(size.replace("MB", "").trim()) * 1024 * 1024;
                } else if (size.endsWith("KB")) {
                    return Long.parseLong(size.replace("KB", "").trim()) * 1024;
                } else if (size.endsWith("B")) {
                    return Long.parseLong(size.replace("B", "").trim());
                }
                return Long.parseLong(size);
            } catch (NumberFormatException e) {
                System.err.println("Invalid file size format: " + size + ", using default 10MB");
                return 10 * 1024 * 1024;
            }
        }

        public boolean isExtensionBlocked(String fileName) {
            if (blockedExtensions == null || blockedExtensions.isEmpty()) {
                return false;
            }
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex < 0) {
                return false;
            }
            String ext = fileName.substring(dotIndex).toLowerCase();
            return blockedExtensions.contains(ext);
        }
    }

    public BashToolConfig getBash() {
        return bash;
    }

    public void setBash(BashToolConfig bash) {
        this.bash = bash;
    }

    public FileToolConfig getFile() {
        return file;
    }

    public void setFile(FileToolConfig file) {
        this.file = file;
    }
}
