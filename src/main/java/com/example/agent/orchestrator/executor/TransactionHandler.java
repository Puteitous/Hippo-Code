package com.example.agent.orchestrator.executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

public class TransactionHandler {

    private static final Logger logger = LoggerFactory.getLogger(TransactionHandler.class);

    private final Map<String, Path> backups = new HashMap<>();
    private final Path backupDir;

    public TransactionHandler() {
        try {
            this.backupDir = Files.createTempDirectory("orchestrator_backup_");
            logger.debug("事务备份目录: {}", backupDir);
        } catch (IOException e) {
            throw new RuntimeException("无法创建备份目录", e);
        }
    }

    public void beforeEdit(String filePath) {
        Path source = Paths.get(filePath);
        if (!Files.exists(source)) {
            logger.debug("文件不存在，无需备份: {}", filePath);
            return;
        }

        try {
            String fileName = source.getFileName().toString();
            Path backup = backupDir.resolve(fileName + "_" + System.currentTimeMillis());
            Files.copy(source, backup, StandardCopyOption.REPLACE_EXISTING);
            backups.put(filePath, backup);
            logger.debug("已备份文件: {} -> {}", filePath, backup.getFileName());
        } catch (IOException e) {
            logger.warn("文件备份失败: {}", filePath, e);
        }
    }

    public void rollback() {
        if (backups.isEmpty()) {
            return;
        }

        logger.warn("⚠️ 正在执行事务回滚，恢复 {} 个文件", backups.size());

        backups.forEach((originalPath, backupPath) -> {
            try {
                if (Files.exists(backupPath)) {
                    Files.copy(backupPath, Paths.get(originalPath), 
                            StandardCopyOption.REPLACE_EXISTING);
                    logger.info("✅ 已恢复: {}", originalPath);
                }
            } catch (IOException e) {
                logger.error("❌ 回滚失败: {}", originalPath, e);
            }
        });

        cleanup();
    }

    public void commit() {
        logger.debug("事务提交，清理 {} 个备份文件", backups.size());
        cleanup();
    }

    private void cleanup() {
        backups.values().forEach(path -> {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                logger.debug("清理备份文件失败: {}", path, e);
            }
        });
        backups.clear();

        try {
            Files.deleteIfExists(backupDir);
        } catch (IOException e) {
            logger.debug("清理备份目录失败: {}", backupDir, e);
        }
    }
}
