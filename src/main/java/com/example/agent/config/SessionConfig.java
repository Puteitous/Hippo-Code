package com.example.agent.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SessionConfig {

    private static final int DEFAULT_MAX_HISTORY = 50;
    private static final String DEFAULT_HISTORY_FILE = ".agent_history";

    @JsonProperty("auto_save")
    private boolean autoSave = true;
    
    @JsonProperty("max_history")
    private int maxHistory = DEFAULT_MAX_HISTORY;
    
    @JsonProperty("history_file")
    private String historyFile = DEFAULT_HISTORY_FILE;
    
    @JsonProperty("save_directory")
    private String saveDirectory;

    public SessionConfig() {
    }

    public boolean isAutoSave() {
        return autoSave;
    }

    public void setAutoSave(boolean autoSave) {
        this.autoSave = autoSave;
    }

    public int getMaxHistory() {
        return maxHistory;
    }

    public void setMaxHistory(int maxHistory) {
        this.maxHistory = maxHistory;
    }

    public String getHistoryFile() {
        return historyFile;
    }

    public void setHistoryFile(String historyFile) {
        this.historyFile = historyFile;
    }

    public String getSaveDirectory() {
        return saveDirectory;
    }

    public void setSaveDirectory(String saveDirectory) {
        this.saveDirectory = saveDirectory;
    }

    @Override
    public String toString() {
        return "SessionConfig{" +
                "autoSave=" + autoSave +
                ", maxHistory=" + maxHistory +
                ", historyFile='" + historyFile + '\'' +
                ", saveDirectory='" + saveDirectory + '\'' +
                '}';
    }
}
