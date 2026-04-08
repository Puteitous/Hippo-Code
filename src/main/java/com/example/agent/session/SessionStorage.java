package com.example.agent.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SessionStorage {

    private static final Logger logger = LoggerFactory.getLogger(SessionStorage.class);
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final String SESSION_FILE_PREFIX = "session_";
    private static final String SESSION_FILE_SUFFIX = ".json";

    private final Path storageDirectory;
    private final ObjectMapper objectMapper;
    private final int maxSavedSessions;

    public SessionStorage() {
        this(Paths.get("logs", "sessions"), 10);
    }

    public SessionStorage(Path storageDirectory, int maxSavedSessions) {
        this.storageDirectory = storageDirectory;
        this.maxSavedSessions = maxSavedSessions;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        
        ensureDirectoryExists();
    }

    private void ensureDirectoryExists() {
        try {
            if (!Files.exists(storageDirectory)) {
                Files.createDirectories(storageDirectory);
                logger.info("创建会话存储目录: {}", storageDirectory);
            }
        } catch (IOException e) {
            logger.error("创建会话存储目录失败: {}", storageDirectory, e);
            throw new RuntimeException("无法创建会话存储目录: " + storageDirectory, e);
        }
    }

    public SessionData saveSession(SessionData session) {
        if (session == null) {
            logger.warn("尝试保存空会话");
            return null;
        }

        try {
            ensureDirectoryExists();
            
            String sessionId = session.getSessionId();
            if (sessionId == null || sessionId.isEmpty()) {
                sessionId = generateSessionId();
                session.setSessionId(sessionId);
            }

            session.touch();
            
            Path sessionFile = getSessionFilePath(sessionId);
            objectMapper.writeValue(sessionFile.toFile(), session);
            
            logger.info("会话已保存: {} (状态: {}, 消息数: {})", 
                sessionId, session.getStatus(), session.getMessageCount());
            
            cleanupOldSessions();
            
            return session;
        } catch (IOException e) {
            logger.error("保存会话失败: {}", session.getSessionId(), e);
            return null;
        }
    }

    public Optional<SessionData> loadSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return Optional.empty();
        }

        Path sessionFile = getSessionFilePath(sessionId);
        
        if (!Files.exists(sessionFile)) {
            logger.warn("会话文件不存在: {}", sessionId);
            return Optional.empty();
        }

        try {
            SessionData session = objectMapper.readValue(sessionFile.toFile(), SessionData.class);
            logger.info("会话已加载: {} (状态: {}, 消息数: {})", 
                sessionId, session.getStatus(), session.getMessageCount());
            return Optional.of(session);
        } catch (IOException e) {
            logger.error("加载会话失败: {}", sessionId, e);
            return Optional.empty();
        }
    }

    public List<SessionData> listSessions() {
        List<SessionData> sessions = new ArrayList<>();
        
        if (!Files.exists(storageDirectory)) {
            return sessions;
        }

        try (Stream<Path> files = Files.list(storageDirectory)) {
            sessions = files
                .filter(this::isSessionFile)
                .map(this::loadSessionFromFile)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .sorted(Comparator.comparing(SessionData::getLastActiveAt).reversed())
                .collect(Collectors.toList());
        } catch (IOException e) {
            logger.error("列出会话失败", e);
        }

        return sessions;
    }

    public List<SessionData> listResumableSessions() {
        return listSessions().stream()
            .filter(SessionData::canResume)
            .collect(Collectors.toList());
    }

    public Optional<SessionData> findLatestResumableSession() {
        return listResumableSessions().stream()
            .findFirst();
    }

    public boolean deleteSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return false;
        }

        Path sessionFile = getSessionFilePath(sessionId);
        
        try {
            boolean deleted = Files.deleteIfExists(sessionFile);
            if (deleted) {
                logger.info("会话已删除: {}", sessionId);
            }
            return deleted;
        } catch (IOException e) {
            logger.error("删除会话失败: {}", sessionId, e);
            return false;
        }
    }

    public void cleanupOldSessions() {
        List<SessionData> sessions = listSessions();
        
        if (sessions.size() <= maxSavedSessions) {
            return;
        }

        int toDelete = sessions.size() - maxSavedSessions;
        sessions.stream()
            .skip(maxSavedSessions)
            .forEach(session -> {
                deleteSession(session.getSessionId());
            });
        
        logger.info("清理了 {} 个旧会话", toDelete);
    }

    public void cleanupCompletedSessions() {
        List<SessionData> sessions = listSessions();
        
        sessions.stream()
            .filter(s -> s.getStatus() == SessionData.Status.COMPLETED)
            .forEach(session -> deleteSession(session.getSessionId()));
        
        logger.info("清理了已完成的会话");
    }

    private String generateSessionId() {
        return LocalDateTime.now().format(FILE_DATE_FORMAT) + "_" + 
               Long.toHexString(System.currentTimeMillis() % 0xFFFF);
    }

    private Path getSessionFilePath(String sessionId) {
        return storageDirectory.resolve(SESSION_FILE_PREFIX + sessionId + SESSION_FILE_SUFFIX);
    }

    private boolean isSessionFile(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.startsWith(SESSION_FILE_PREFIX) && fileName.endsWith(SESSION_FILE_SUFFIX);
    }

    private Optional<SessionData> loadSessionFromFile(Path file) {
        try {
            return Optional.of(objectMapper.readValue(file.toFile(), SessionData.class));
        } catch (IOException e) {
            logger.warn("加载会话文件失败: {}", file, e);
            return Optional.empty();
        }
    }

    public Path getStorageDirectory() {
        return storageDirectory;
    }
}
