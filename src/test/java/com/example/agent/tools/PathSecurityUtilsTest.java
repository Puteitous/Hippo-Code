package com.example.agent.tools;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

public class PathSecurityUtilsTest {
    @Test
    void testGetProjectRoot() {
        Path root = PathSecurityUtils.getProjectRoot();
        assertNotNull(root);
        assertTrue(root.isAbsolute());
    }

    @Test
    void testValidateAndResolveWithRelativePath() throws ToolExecutionException {
        Path path = PathSecurityUtils.validateAndResolve("src");
        assertNotNull(path);
        assertTrue(path.isAbsolute());
        assertTrue(path.endsWith("src"));
    }

    @Test
    void testValidateAndResolveWithDotPath() throws ToolExecutionException {
        Path path = PathSecurityUtils.validateAndResolve(".");
        assertNotNull(path);
        assertTrue(path.isAbsolute());
        assertEquals(PathSecurityUtils.getProjectRoot(), path);
    }

    @Test
    void testValidateAndResolveWithEmptyPath() throws ToolExecutionException {
        Path path = PathSecurityUtils.validateAndResolve("");
        assertNotNull(path);
        assertTrue(path.isAbsolute());
        assertEquals(PathSecurityUtils.getProjectRoot(), path);
    }

    @Test
    void testValidateAndResolveWithDotSlashPath() throws ToolExecutionException {
        Path path = PathSecurityUtils.validateAndResolve("./src");
        assertNotNull(path);
        assertTrue(path.isAbsolute());
    }

    @Test
    void testValidateAndResolveWithAbsolutePath() throws ToolExecutionException {
        Path root = PathSecurityUtils.getProjectRoot();
        Path path = PathSecurityUtils.validateAndResolve(root.toString());
        assertNotNull(path);
        assertEquals(root, path);
    }

    @Test
    void testValidateAndResolveWithSubdirectory() throws ToolExecutionException {
        Path path = PathSecurityUtils.validateAndResolve("src/main/java");
        assertNotNull(path);
        assertTrue(path.isAbsolute());
        assertTrue(path.toString().contains("src"));
    }

    @Test
    void testValidateAndResolveWithParentDirectoryOutsideProject() {
        assertThrows(ToolExecutionException.class, () -> {
            PathSecurityUtils.validateAndResolve("..");
        });
    }

    @Test
    void testValidateAndResolveWithDeepParentTraversal() {
        assertThrows(ToolExecutionException.class, () -> {
            PathSecurityUtils.validateAndResolve("../../..");
        });
    }

    @Test
    void testValidateAndResolveWithAbsolutePathOutsideProject() {
        assertThrows(ToolExecutionException.class, () -> {
            PathSecurityUtils.validateAndResolve("/etc/passwd");
        });
    }

    @Test
    void testIsWithinProjectWithProjectRoot() {
        Path root = PathSecurityUtils.getProjectRoot();
        assertTrue(PathSecurityUtils.isWithinProject(root));
    }

    @Test
    void testIsWithinProjectWithSubdirectory() {
        Path root = PathSecurityUtils.getProjectRoot();
        Path subPath = root.resolve("src");
        assertTrue(PathSecurityUtils.isWithinProject(subPath));
    }

    @Test
    void testIsWithinProjectWithFile() {
        Path root = PathSecurityUtils.getProjectRoot();
        Path filePath = root.resolve("pom.xml");
        assertTrue(PathSecurityUtils.isWithinProject(filePath));
    }

    @Test
    void testIsWithinProjectWithParentDirectory() {
        Path root = PathSecurityUtils.getProjectRoot();
        Path parent = root.getParent();
        if (parent != null) {
            assertFalse(PathSecurityUtils.isWithinProject(parent));
        }
    }

    @Test
    void testIsWithinProjectWithSiblingDirectory() {
        Path root = PathSecurityUtils.getProjectRoot();
        Path parent = root.getParent();
        if (parent != null) {
            Path sibling = parent.resolve("other-project");
            assertFalse(PathSecurityUtils.isWithinProject(sibling));
        }
    }

    @Test
    void testIsWithinProjectWithAbsolutePath() {
        Path root = PathSecurityUtils.getProjectRoot();
        Path absolutePath = root.resolve("src").toAbsolutePath();
        assertTrue(PathSecurityUtils.isWithinProject(absolutePath));
    }

    @Test
    void testIsWithinProjectWithRelativePath() {
        Path relativePath = Paths.get("src");
        assertTrue(PathSecurityUtils.isWithinProject(relativePath));
    }

    @Test
    void testGetRelativePathWithSubdirectory() {
        Path root = PathSecurityUtils.getProjectRoot();
        Path subPath = root.resolve("src/main/java");
        String relative = PathSecurityUtils.getRelativePath(subPath);
        assertTrue(relative.contains("src"));
        assertFalse(relative.contains(root.toString()));
    }

    @Test
    void testGetRelativePathWithRootFile() {
        Path root = PathSecurityUtils.getProjectRoot();
        Path filePath = root.resolve("pom.xml");
        String relative = PathSecurityUtils.getRelativePath(filePath);
        assertEquals("pom.xml", relative);
    }

    @Test
    void testGetRelativePathWithOutsidePath() {
        Path outsidePath = Paths.get("/etc/passwd");
        String relative = PathSecurityUtils.getRelativePath(outsidePath);
        assertEquals(outsidePath.toString(), relative);
    }

    @Test
    void testValidateAndResolveWithMixedSlashes() throws ToolExecutionException {
        Path path = PathSecurityUtils.validateAndResolve("src\\main/java");
        assertNotNull(path);
        assertTrue(path.isAbsolute());
    }

    @Test
    void testValidateAndResolveWithTrailingSlash() throws ToolExecutionException {
        Path path = PathSecurityUtils.validateAndResolve("src/");
        assertNotNull(path);
        assertTrue(path.isAbsolute());
    }

    @Test
    void testValidateAndResolveWithTrailingBackslash() throws ToolExecutionException {
        Path path = PathSecurityUtils.validateAndResolve("src\\");
        assertNotNull(path);
        assertTrue(path.isAbsolute());
    }

    @Test
    void testValidateAndResolveWithDoubleSlash() throws ToolExecutionException {
        Path path = PathSecurityUtils.validateAndResolve("src//main");
        assertNotNull(path);
        assertTrue(path.isAbsolute());
    }

    @Test
    void testValidateAndResolveWithDotDotInMiddle() {
        assertThrows(ToolExecutionException.class, () -> {
            PathSecurityUtils.validateAndResolve("src/../../../etc");
        });
    }

    @Test
    void testValidateAndResolveWithValidDotDotInMiddle() throws ToolExecutionException {
        Path path = PathSecurityUtils.validateAndResolve("src/main/../test");
        assertNotNull(path);
        assertTrue(path.isAbsolute());
        assertTrue(path.toString().contains("src"));
    }

    @Test
    void testValidateAndResolveWithHiddenFile() throws ToolExecutionException {
        Path path = PathSecurityUtils.validateAndResolve(".gitignore");
        assertNotNull(path);
        assertTrue(path.isAbsolute());
    }

    @Test
    void testValidateAndResolveWithHiddenDirectory() throws ToolExecutionException {
        Path path = PathSecurityUtils.validateAndResolve(".git");
        assertNotNull(path);
        assertTrue(path.isAbsolute());
    }

    @Test
    void testValidateAndResolveWithNullPath() throws ToolExecutionException {
        Path path = PathSecurityUtils.validateAndResolve(null);
        assertNotNull(path);
        assertEquals(PathSecurityUtils.getProjectRoot(), path);
    }

    @Test
    void testValidateAndResolveWithWhitespacePath() throws ToolExecutionException {
        Path path = PathSecurityUtils.validateAndResolve("   ");
        assertNotNull(path);
        assertTrue(path.isAbsolute());
    }

    @Test
    void testIsWithinProjectWithNullPath() {
        assertFalse(PathSecurityUtils.isWithinProject(null));
    }

    @Test
    void testGetRelativePathWithNullPath() {
        String relative = PathSecurityUtils.getRelativePath(null);
        assertEquals("null", relative);
    }

    @Test
    void testValidateAndResolveWithVeryLongPath() throws ToolExecutionException {
        StringBuilder longPath = new StringBuilder("src/");
        for (int i = 0; i < 10; i++) {
            longPath.append("subdir").append(i).append("/");
        }
        longPath.append("file.txt");
        
        Path path = PathSecurityUtils.validateAndResolve(longPath.toString());
        assertNotNull(path);
        assertTrue(path.isAbsolute());
    }

    @Test
    void testValidateAndResolveWithSpecialCharacters() throws ToolExecutionException {
        Path path = PathSecurityUtils.validateAndResolve("src/test file.txt");
        assertNotNull(path);
        assertTrue(path.isAbsolute());
    }

    @Test
    void testValidateAndResolveWithUnicodePath() throws ToolExecutionException {
        Path path = PathSecurityUtils.validateAndResolve("src/测试文件.txt");
        assertNotNull(path);
        assertTrue(path.isAbsolute());
    }

}
