package com.example.agent.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SessionConfig {

    private static final Logger logger = LoggerFactory.getLogger(SessionConfig.class);
    private static final int DEFAULT_MAX_HISTORY = 50;
    private static final String DEFAULT_HISTORY_FILE = ".agent_history";
    private static final int DEFAULT_MAX_SAVED_SESSIONS = 10;
    private static final String DEFAULT_SESSION_DIRECTORY = "logs/sessions";
    private static final int MIN_MAX_SAVED_SESSIONS = 0;
    private static final int MAX_MAX_SAVED_SESSIONS = 1000;

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
        if (maxHistory < 0) {
            logger.warn("maxHistory 不能为负数，使用默认值: {}", DEFAULT_MAX_HISTORY);
            this.maxHistory = DEFAULT_MAX_HISTORY;
        } else {
            this.maxHistory = maxHistory;
        }
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
        if (maxSavedSessions < MIN_MAX_SAVED_SESSIONS) {
            logger.warn("maxSavedSessions 不能小于 {}，使用默认值: {}", 
                MIN_MAX_SAVED_SESSIONS, DEFAULT_MAX_SAVED_SESSIONS);
            this.maxSavedSessions = DEFAULT_MAX_SAVED_SESSIONS;
        } else if (maxSavedSessions > MAX_MAX_SAVED_SESSIONS) {
            logger.warn("maxSavedSessions 不能大于 {}，使用最大值: {}", 
                MAX_MAX_SAVED_SESSIONS, MAX_MAX_SAVED_SESSIONS);
            this.maxSavedSessions = MAX_MAX_SAVED_SESSIONS;
        } else {
            this.maxSavedSessions = maxSavedSessions;
            if (maxSavedSessions == 0) {
                logger.info("maxSavedSessions 设置为 0，会话持久化将被禁用");
            }
        }
    }

    public String getSessionDirectory() {
        return sessionDirectory;
    }

    public void setSessionDirectory(String sessionDirectory) {
        if (sessionDirectory == null || sessionDirectory.trim().isEmpty()) {
            logger.warn("sessionDirectory 为空，使用默认值: {}", DEFAULT_SESSION_DIRECTORY);
            this.sessionDirectory = DEFAULT_SESSION_DIRECTORY;
        } else {
            this.sessionDirectory = sessionDirectory.trim();
        }
    }

    public boolean isAutoResume() {
        return autoResume;
    }

    public void setAutoResume(boolean autoResume) {
        this.autoResume = autoResume;
    }

    public void validate() {
        if (maxHistory < 0) {
            logger.warn("配置验证: maxHistory 为负数，已重置为默认值");
            maxHistory = DEFAULT_MAX_HISTORY;
        }
        
        if (maxSavedSessions < MIN_MAX_SAVED_SESSIONS || maxSavedSessions > MAX_MAX_SAVED_SESSIONS) {
            logger.warn("配置验证: maxSavedSessions 超出范围，已重置为默认值");
            maxSavedSessions = DEFAULT_MAX_SAVED_SESSIONS;
        }
        
        if (sessionDirectory == null || sessionDirectory.trim().isEmpty()) {
            logger.warn("配置验证: sessionDirectory 为空，已重置为默认值");
            sessionDirectory = DEFAULT_SESSION_DIRECTORY;
        }
        
        if (historyFile == null || historyFile.trim().isEmpty()) {
            logger.warn("配置验证: historyFile 为空，已重置为默认值");
            historyFile = DEFAULT_HISTORY_FILE;
        }
        
        if (maxSavedSessions == 0 && persistSessions) {
            logger.warn("配置验证: maxSavedSessions 为 0 但 persistSessions 为 true，会话持久化将被禁用");
        }
        
        logger.debug("配置验证完成: {}", this);
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
