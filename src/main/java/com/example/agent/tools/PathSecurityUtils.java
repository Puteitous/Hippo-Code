package com.example.agent.tools;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class PathSecurityUtils {

    private static final Path PROJECT_ROOT = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    
    private static final List<String> RESTRICTED_PATHS_UNIX = List.of(
            "/etc",
            "/root",
            "/home",
            "/Users",
            "/System",
            "/.ssh",
            "/.gnupg"
    );
    
    private static final List<String> RESTRICTED_PATHS_WINDOWS = List.of(
            "\\Windows",
            "\\Program Files",
            "\\Program Files (x86)",
            "\\AppData",
            "\\.ssh",
            "\\.gnupg"
    );

    public static Path validateAndResolve(String filePath) throws ToolExecutionException {
        if (filePath == null || filePath.trim().isEmpty()) {
            return PROJECT_ROOT;
        }
        
        filePath = filePath.trim();
        
        Path path = Paths.get(filePath);
        
        if (!path.isAbsolute()) {
            path = PROJECT_ROOT.resolve(path);
        }
        
        path = path.normalize();
        
        if (!isWithinProject(path)) {
            throw new ToolExecutionException(
                "安全限制: 只能访问项目目录内的文件。\n" +
                "项目目录: " + PROJECT_ROOT + "\n" +
                "请求路径: " + path
            );
        }
        
        String pathString = path.toString();
        String normalizedPath = pathString.replace("/", File.separator).replace("\\", File.separator);
        
        for (String restricted : getRestrictedPathsForOS()) {
            String normalizedRestricted = restricted.replace("/", File.separator).replace("\\", File.separator);
            if (normalizedPath.startsWith(normalizedRestricted)) {
                throw new ToolExecutionException("安全限制: 不允许访问系统敏感目录: " + restricted);
            }
        }
        
        return path;
    }
    
    private static List<String> getRestrictedPathsForOS() {
        return File.separator.equals("/") ? RESTRICTED_PATHS_UNIX : RESTRICTED_PATHS_WINDOWS;
    }

    public static boolean isWithinProject(Path path) {
        if (path == null) {
            return false;
        }
        Path normalizedPath = path.toAbsolutePath().normalize();
        return normalizedPath.startsWith(PROJECT_ROOT);
    }

    public static Path getProjectRoot() {
        return PROJECT_ROOT;
    }

    public static String getRelativePath(Path absolutePath) {
        if (absolutePath == null) {
            return "null";
        }
        if (isWithinProject(absolutePath)) {
            return PROJECT_ROOT.relativize(absolutePath).toString();
        }
        return absolutePath.toString();
    }
}
