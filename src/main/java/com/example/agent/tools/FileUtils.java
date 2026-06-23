package com.example.agent.tools;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/**
 * 文件操作工具类。
 * <p>
 * 提供原子写入等通用文件操作方法，供 WriteFileTool、EditFileTool 等共享使用。
 */
public final class FileUtils {

    private FileUtils() {
    }

    /**
     * 可抛出 IOException 的消费者接口，用于 {@link #atomicWriteStream}。
     */
    @FunctionalInterface
    public interface IOConsumer<T> {
        void accept(T t) throws IOException;
    }

    /**
     * 原子写入字符串到文件。
     * <p>
     * 先写入临时文件（.tmp.{UUID}），再通过 atomic move 覆盖目标文件。
     * 如果写入或移动过程中断，临时文件会被清理，目标文件不受影响。
     *
     * @param path    目标文件路径
     * @param content 要写入的内容
     * @throws IOException 写入或移动过程中发生 I/O 错误
     */
    public static void atomicWriteString(Path path, String content) throws IOException {
        Path tmpPath = path.resolveSibling(path.getFileName() + ".tmp." + UUID.randomUUID());
        try {
            Files.writeString(tmpPath, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(tmpPath, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // 写入或移动过程中断，清理临时文件，原文件不受影响
            try {
                Files.deleteIfExists(tmpPath);
            } catch (IOException ignored) {
            }
            throw e;
        }
    }

    /**
     * 原子写入 OutputStream 数据到文件。
     * <p>
     * 先写入临时文件（.tmp.{UUID}），再通过 atomic move 覆盖目标文件。
     * 适合 {@code workbook.write(os)}、{@code doc.write(os)} 等 OutputStream 写入场景。
     * 如果写入或移动过程中断，临时文件会被清理，目标文件不受影响。
     *
     * @param path   目标文件路径
     * @param writer 接收临时文件的 OutputStream 并写入数据的回调
     * @throws IOException 写入或移动过程中发生 I/O 错误
     */
    public static void atomicWriteStream(Path path, IOConsumer<OutputStream> writer) throws IOException {
        Path tmpPath = path.resolveSibling(path.getFileName() + ".tmp." + UUID.randomUUID());
        try {
            try (OutputStream os = Files.newOutputStream(tmpPath)) {
                writer.accept(os);
            }
            Files.move(tmpPath, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tmpPath);
            } catch (IOException ignored) {
            }
            throw e;
        }
    }
}
