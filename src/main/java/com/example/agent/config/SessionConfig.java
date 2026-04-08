package com.example.agent.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SessionConfig {

    private static final int DEFAULT_MAX_HISTORY = 50;
    private static final String DEFAULT_HISTORY_FILE = ".agent_history";
    private static final int DEFAULT_MAX_SAVED_SESSIONS = 10;
    private static final String DEFAULT_SESSION_DIRECTORY = "logs/sessions";

    @JsonProperty("auto_save")
    private boolean autoSave = true;
    
    @JsonProperty("max_history")
    private int maxHistory = DEFAULT_MAX_HISTORY;
    
    @JsonProperty("history_file")
    private String historyFile = DEFAULT_HISTORY_FILE;
    
    @JsonProperty("save_directory")
    private String saveDirectory;
    
    @JsonProperty("persist_sessions")
    private boolean persistSessions = true;
    
    @JsonProperty("max_saved_sessions")
    private int maxSavedSessions = DEFAULT_MAX_SAVED_SESSIONS;
    
    @JsonProperty("session_directory")
    private String sessionDirectory = DEFAULT_SESSION_DIRECTORY;
    
    @JsonProperty("auto_resume")
    private boolean autoResume = true;

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

    public boolean isPersistSessions() {
        return persistSessions;
    }

    public void setPersistSessions(boolean persistSessions) {
        this.persistSessions = persistSessions;
    }

    public int getMaxSavedSessions() {
        return maxSavedSessions;
    }

    public void setMaxSavedSessions(int maxSavedSessions) {
        this.maxSavedSessions = maxSavedSessions;
    }

    public String getSessionDirectory() {
        return sessionDirectory;
    }

    public void setSessionDirectory(String sessionDirectory) {
        this.sessionDirectory = sessionDirectory;
    }

    public boolean isAutoResume() {
        return autoResume;
    }

    public void setAutoResume(boolean autoResume) {
        this.autoResume = autoResume;
    }

    @Override
    public String toString() {
        return "SessionConfig{" +
                "autoSave=" + autoSave +
                ", maxHistory=" + maxHistory +
                ", historyFile='" + historyFile + '\'' +
                ", saveDirectory='" + saveDirectory + '\'' +
                ", persistSessions=" + persistSessions +
                ", maxSavedSessions=" + maxSavedSessions +
                ", sessionDirectory='" + sessionDirectory + '\'' +
                ", autoResume=" + autoResume +
                '}';
    }
}
