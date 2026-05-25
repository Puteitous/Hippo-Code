package com.example.agent.tools.filter;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class FileFilter {

    private final IgnoredDirectories ignoredDirectories;
    private final IgnoredExtensions ignoredExtensions;
    private final GitignoreMatcher gitignoreMatcher;

    public FileFilter(Path searchRoot) {
        this.ignoredDirectories = new IgnoredDirectories();
        this.ignoredExtensions = new IgnoredExtensions();
        this.gitignoreMatcher = new GitignoreMatcher(searchRoot);
    }

    public FileFilter(IgnoredDirectories ignoredDirectories,
                      IgnoredExtensions ignoredExtensions,
                      GitignoreMatcher gitignoreMatcher) {
        this.ignoredDirectories = ignoredDirectories;
        this.ignoredExtensions = ignoredExtensions;
        this.gitignoreMatcher = gitignoreMatcher;
    }

    public Stream<Path> walkFiles(Path start, int maxDepth) throws IOException {
        List<Path> results = new ArrayList<>();
        Files.walkFileTree(start, Collections.emptySet(), maxDepth, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(start)) {
                    if (ignoredDirectories.isIgnored(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (gitignoreMatcher.isIgnored(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (shouldSearch(file)) {
                    results.add(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.SKIP_SUBTREE;
            }
        });
        return results.stream();
    }

    public boolean shouldSearch(Path path) {
        if (!Files.isRegularFile(path)) {
            return false;
        }
        if (ignoredDirectories.isIgnored(path)) {
            return false;
        }
        if (ignoredExtensions.isIgnored(path)) {
            return false;
        }
        return !gitignoreMatcher.isIgnored(path);
    }
}
