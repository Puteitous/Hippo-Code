package com.example.agent.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class ToolsConfigTest {

    private ToolsConfig toolsConfig;

    @BeforeEach
    void setUp() {
        toolsConfig = new ToolsConfig();
    }

    @Test
    void testDefaultBashConfig() {
        ToolsConfig.BashToolConfig bash = toolsConfig.getBash();
        
        assertTrue(bash.isEnabled());
        assertTrue(bash.isRequireConfirmation());
        assertNotNull(bash.getWhitelist());
        assertTrue(bash.getWhitelist().contains("git"));
        assertTrue(bash.getWhitelist().contains("mvn"));
        assertTrue(bash.getWhitelist().contains("npm"));
        assertTrue(bash.getWhitelist().contains("docker"));
        assertTrue(bash.getWhitelist().contains("ls"));
        assertTrue(bash.getWhitelist().contains("cat"));
        assertTrue(bash.getWhitelist().contains("grep"));
    }

    @Test
    void testDefaultFileConfig() {
        ToolsConfig.FileToolConfig file = toolsConfig.getFile();
        
        assertTrue(file.isEnabled());
        assertEquals("10MB", file.getMaxFileSize());
        assertEquals(10 * 1024 * 1024, file.getMaxFileSizeBytes());
        assertNotNull(file.getAllowedPaths());
        assertTrue(file.getAllowedPaths().contains("."));
        assertNotNull(file.getBlockedExtensions());
        assertTrue(file.getBlockedExtensions().contains(".env"));
        assertTrue(file.getBlockedExtensions().contains(".pem"));
        assertTrue(file.getBlockedExtensions().contains(".key"));
    }

    @Test
    void testIsCommandAllowedWhenDisabled() {
        ToolsConfig.BashToolConfig bash = toolsConfig.getBash();
        bash.setEnabled(false);
        
        assertFalse(bash.isCommandAllowed("git status"));
        assertFalse(bash.isCommandAllowed("ls"));
    }

    @Test
    void testIsCommandAllowedWithWhitelistedCommand() {
        ToolsConfig.BashToolConfig bash = toolsConfig.getBash();
        
        assertTrue(bash.isCommandAllowed("git status"));
        assertTrue(bash.isCommandAllowed("mvn clean install"));
        assertTrue(bash.isCommandAllowed("npm install"));
        assertTrue(bash.isCommandAllowed("docker ps"));
        assertTrue(bash.isCommandAllowed("ls -la"));
        assertTrue(bash.isCommandAllowed("cat file.txt"));
        assertTrue(bash.isCommandAllowed("grep pattern file.txt"));
    }

    @Test
    void testIsCommandAllowedWithNonWhitelistedCommand() {
        ToolsConfig.BashToolConfig bash = toolsConfig.getBash();
        
        assertFalse(bash.isCommandAllowed("rm -rf /"));
        assertFalse(bash.isCommandAllowed("sudo reboot"));
        assertFalse(bash.isCommandAllowed("format c:"));
    }

    @Test
    void testIsCommandAllowedWithEmptyWhitelist() {
        ToolsConfig.BashToolConfig bash = toolsConfig.getBash();
        bash.setWhitelist(Collections.emptyList());
        
        assertTrue(bash.isCommandAllowed("git status"));
        assertTrue(bash.isCommandAllowed("rm -rf /"));
    }

    @Test
    void testIsCommandAllowedWithNullWhitelist() {
        ToolsConfig.BashToolConfig bash = toolsConfig.getBash();
        bash.setWhitelist(null);
        
        assertTrue(bash.isCommandAllowed("git status"));
    }

    @Test
    void testIsCommandAllowedWithEmptyCommand() {
        ToolsConfig.BashToolConfig bash = toolsConfig.getBash();
        
        assertFalse(bash.isCommandAllowed(""));
        assertFalse(bash.isCommandAllowed("   "));
    }

    @Test
    void testIsCommandAllowedWithSemicolonInjection() {
        ToolsConfig.BashToolConfig bash = toolsConfig.getBash();
        
        assertFalse(bash.isCommandAllowed("git status; rm -rf /"));
        assertFalse(bash.isCommandAllowed("ls; cat /etc/passwd"));
        assertFalse(bash.isCommandAllowed("git status ; ls"));
    }

    @Test
    void testIsCommandAllowedWithAndInjection() {
        ToolsConfig.BashToolConfig bash = toolsConfig.getBash();
        
        assertFalse(bash.isCommandAllowed("git status && rm -rf /"));
        assertFalse(bash.isCommandAllowed("ls && cat /etc/passwd"));
    }

    @Test
    void testIsCommandAllowedWithOrInjection() {
        ToolsConfig.BashToolConfig bash = toolsConfig.getBash();
        
        assertFalse(bash.isCommandAllowed("git status || rm -rf /"));
        assertFalse(bash.isCommandAllowed("ls || cat /etc/passwd"));
    }

    @Test
    void testIsCommandAllowedWithPipeInjection() {
        ToolsConfig.BashToolConfig bash = toolsConfig.getBash();
        
        assertFalse(bash.isCommandAllowed("git status | rm -rf /"));
        assertFalse(bash.isCommandAllowed("cat file | grep secret"));
    }

    @Test
    void testIsCommandAllowedWithBacktickInjection() {
        ToolsConfig.BashToolConfig bash = toolsConfig.getBash();
        
        assertFalse(bash.isCommandAllowed("git `rm -rf /` status"));
        assertFalse(bash.isCommandAllowed("echo `cat /etc/passwd`"));
    }

    @Test
    void testIsCommandAllowedWithCommandSubstitutionInjection() {
        ToolsConfig.BashToolConfig bash = toolsConfig.getBash();
        
        assertFalse(bash.isCommandAllowed("git $(rm -rf /) status"));
        assertFalse(bash.isCommandAllowed("echo $(cat /etc/passwd)"));
    }

    @Test
    void testIsCommandAllowedWithMixedInjection() {
        ToolsConfig.BashToolConfig bash = toolsConfig.getBash();
        
        assertFalse(bash.isCommandAllowed("git status; ls && cat file || echo test"));
    }

    @Test
    void testIsCommandAllowedWithLeadingWhitespace() {
        ToolsConfig.BashToolConfig bash = toolsConfig.getBash();
        
        assertTrue(bash.isCommandAllowed("   git status"));
        assertTrue(bash.isCommandAllowed("\tgit status"));
    }

    @Test
    void testParseFileSizeBytes() {
        ToolsConfig.FileToolConfig file = toolsConfig.getFile();
        file.setMaxFileSize("1024B");
        assertEquals(1024, file.getMaxFileSizeBytes());
    }

    @Test
    void testParseFileSizeKilobytes() {
        ToolsConfig.FileToolConfig file = toolsConfig.getFile();
        file.setMaxFileSize("10KB");
        assertEquals(10 * 1024, file.getMaxFileSizeBytes());
    }

    @Test
    void testParseFileSizeMegabytes() {
        ToolsConfig.FileToolConfig file = toolsConfig.getFile();
        file.setMaxFileSize("10MB");
        assertEquals(10 * 1024 * 1024, file.getMaxFileSizeBytes());
    }

    @Test
    void testParseFileSizeGigabytes() {
        ToolsConfig.FileToolConfig file = toolsConfig.getFile();
        file.setMaxFileSize("1GB");
        assertEquals(1024L * 1024 * 1024, file.getMaxFileSizeBytes());
    }

    @Test
    void testParseFileSizeCaseInsensitive() {
        ToolsConfig.FileToolConfig file = toolsConfig.getFile();
        file.setMaxFileSize("10mb");
        assertEquals(10 * 1024 * 1024, file.getMaxFileSizeBytes());
        
        file.setMaxFileSize("10Mb");
        assertEquals(10 * 1024 * 1024, file.getMaxFileSizeBytes());
    }

    @Test
    void testParseFileSizeWithSpaces() {
        ToolsConfig.FileToolConfig file = toolsConfig.getFile();
        file.setMaxFileSize("  10 MB  ");
        assertEquals(10 * 1024 * 1024, file.getMaxFileSizeBytes());
    }

    @Test
    void testParseFileSizeNull() {
        ToolsConfig.FileToolConfig file = toolsConfig.getFile();
        file.setMaxFileSize(null);
        assertEquals(10 * 1024 * 1024, file.getMaxFileSizeBytes());
    }

    @Test
    void testParseFileSizeEmpty() {
        ToolsConfig.FileToolConfig file = toolsConfig.getFile();
        file.setMaxFileSize("");
        assertEquals(10 * 1024 * 1024, file.getMaxFileSizeBytes());
    }

    @Test
    void testParseFileSizeInvalidFormat() {
        ToolsConfig.FileToolConfig file = toolsConfig.getFile();
        file.setMaxFileSize("invalid");
        assertEquals(10 * 1024 * 1024, file.getMaxFileSizeBytes());
    }

    @Test
    void testParseFileSizeInvalidNumber() {
        ToolsConfig.FileToolConfig file = toolsConfig.getFile();
        file.setMaxFileSize("abcMB");
        assertEquals(10 * 1024 * 1024, file.getMaxFileSizeBytes());
    }

    @Test
    void testParseFileSizePlainNumber() {
        ToolsConfig.FileToolConfig file = toolsConfig.getFile();
        file.setMaxFileSize("1024");
        assertEquals(1024, file.getMaxFileSizeBytes());
    }

    @Test
    void testIsExtensionBlockedWithBlockedExtension() {
        ToolsConfig.FileToolConfig file = toolsConfig.getFile();
        
        assertTrue(file.isExtensionBlocked("config.env"));
        assertTrue(file.isExtensionBlocked("cert.pem"));
        assertTrue(file.isExtensionBlocked("private.key"));
        assertTrue(file.isExtensionBlocked("keystore.p12"));
        assertTrue(file.isExtensionBlocked("truststore.jks"));
    }

    @Test
    void testIsExtensionBlockedWithAllowedExtension() {
        ToolsConfig.FileToolConfig file = toolsConfig.getFile();
        
        assertFalse(file.isExtensionBlocked("main.java"));
        assertFalse(file.isExtensionBlocked("config.yaml"));
        assertFalse(file.isExtensionBlocked("data.json"));
        assertFalse(file.isExtensionBlocked("README.md"));
    }

    @Test
    void testIsExtensionBlockedWithNoExtension() {
        ToolsConfig.FileToolConfig file = toolsConfig.getFile();
        
        assertFalse(file.isExtensionBlocked("Makefile"));
        assertFalse(file.isExtensionBlocked("Dockerfile"));
    }

    @Test
    void testIsExtensionBlockedCaseInsensitive() {
        ToolsConfig.FileToolConfig file = toolsConfig.getFile();
        
        assertTrue(file.isExtensionBlocked("CONFIG.ENV"));
        assertTrue(file.isExtensionBlocked("Config.Pem"));
        assertTrue(file.isExtensionBlocked("PRIVATE.KEY"));
    }

    @Test
    void testIsExtensionBlockedWithEmptyBlockedExtensions() {
        ToolsConfig.FileToolConfig file = toolsConfig.getFile();
        file.setBlockedExtensions(Collections.emptyList());
        
        assertFalse(file.isExtensionBlocked("config.env"));
        assertFalse(file.isExtensionBlocked("cert.pem"));
    }

    @Test
    void testIsExtensionBlockedWithNullBlockedExtensions() {
        ToolsConfig.FileToolConfig file = toolsConfig.getFile();
        file.setBlockedExtensions(null);
        
        assertFalse(file.isExtensionBlocked("config.env"));
    }

    @Test
    void testBashConfigSetters() {
        ToolsConfig.BashToolConfig bash = new ToolsConfig.BashToolConfig();
        
        bash.setEnabled(false);
        bash.setRequireConfirmation(false);
        bash.setWhitelist(Arrays.asList("git", "npm"));
        
        assertFalse(bash.isEnabled());
        assertFalse(bash.isRequireConfirmation());
        assertEquals(2, bash.getWhitelist().size());
        assertTrue(bash.getWhitelist().contains("git"));
        assertTrue(bash.getWhitelist().contains("npm"));
    }

    @Test
    void testFileConfigSetters() {
        ToolsConfig.FileToolConfig file = new ToolsConfig.FileToolConfig();
        
        file.setEnabled(false);
        file.setMaxFileSize("20MB");
        file.setAllowedPaths(Arrays.asList("/home", "/tmp"));
        file.setBlockedExtensions(Arrays.asList(".secret"));
        
        assertFalse(file.isEnabled());
        assertEquals("20MB", file.getMaxFileSize());
        assertEquals(2, file.getAllowedPaths().size());
        assertEquals(1, file.getBlockedExtensions().size());
    }

    @Test
    void testToolsConfigSetters() {
        ToolsConfig config = new ToolsConfig();
        ToolsConfig.BashToolConfig newBash = new ToolsConfig.BashToolConfig();
        ToolsConfig.FileToolConfig newFile = new ToolsConfig.FileToolConfig();
        
        config.setBash(newBash);
        config.setFile(newFile);
        
        assertSame(newBash, config.getBash());
        assertSame(newFile, config.getFile());
    }
}
