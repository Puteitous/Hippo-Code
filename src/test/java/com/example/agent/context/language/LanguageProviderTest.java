package com.example.agent.context.language;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LanguageProvider 测试
 */
class LanguageProviderTest {

    private LanguageProviderFactory factory;

    @BeforeEach
    void setUp() {
        factory = new LanguageProviderFactory();
    }

    @Test
    void testFactoryCreation() {
        assertNotNull(factory);
        assertTrue(factory.getProviderCount() > 0);
    }

    @Test
    void testJavaProvider() {
        LanguageProvider provider = factory.getProviderByExtension("java");
        assertNotNull(provider);
        assertEquals("Java", provider.getLanguageName());
        assertTrue(provider.supportsExtension("java"));
        assertFalse(provider.supportsExtension("py"));
    }

    @Test
    void testPythonProvider() {
        LanguageProvider provider = factory.getProviderByExtension("py");
        assertNotNull(provider);
        assertEquals("Python", provider.getLanguageName());
        assertTrue(provider.supportsExtension("py"));
        assertFalse(provider.supportsExtension("java"));
    }

    @Test
    void testJsProvider() {
        LanguageProvider provider = factory.getProviderByExtension("js");
        assertNotNull(provider);
        assertEquals("JavaScript/TypeScript", provider.getLanguageName());
        assertTrue(provider.supportsExtension("js"));
        assertTrue(provider.supportsExtension("ts"));
        assertTrue(provider.supportsExtension("jsx"));
        assertTrue(provider.supportsExtension("tsx"));
    }

    @Test
    void testGetProviderByPath() {
        LanguageProvider javaProvider = factory.getProvider("src/main/java/Test.java");
        assertNotNull(javaProvider);
        assertEquals("Java", javaProvider.getLanguageName());

        LanguageProvider pythonProvider = factory.getProvider("src/test.py");
        assertNotNull(pythonProvider);
        assertEquals("Python", pythonProvider.getLanguageName());

        LanguageProvider jsProvider = factory.getProvider("src/app.js");
        assertNotNull(jsProvider);
        assertEquals("JavaScript/TypeScript", jsProvider.getLanguageName());
    }

    @Test
    void testIsSupported() {
        assertTrue(factory.isSupported("test.java"));
        assertTrue(factory.isSupported("test.py"));
        assertTrue(factory.isSupported("test.js"));
        assertTrue(factory.isSupported("test.ts"));
        assertFalse(factory.isSupported("test.txt"));
        assertFalse(factory.isSupported("test.md"));
    }

    @Test
    void testJavaTruncate() {
        JavaLanguageProvider provider = new JavaLanguageProvider();

        String javaCode = "package com.example;\n\n" +
                "import java.util.List;\n\n" +
                "public class TestClass {\n" +
                "    private String name;\n" +
                "    public void testMethod() {\n" +
                "        System.out.println(\"Hello\");\n" +
                "    }\n" +
                "}";

        // 测试不需要截断的情况
        String result = provider.truncate(javaCode, 1000);
        assertEquals(javaCode, result);

        // 测试需要截断的情况
        String truncated = provider.truncate(javaCode, 10);
        assertNotNull(truncated);
        assertTrue(truncated.contains("package"));
        assertTrue(truncated.contains("import"));
        assertTrue(truncated.contains("class TestClass"));
        assertTrue(truncated.contains("[文件已截断"));
    }

    @Test
    void testPythonTruncate() {
        PythonLanguageProvider provider = new PythonLanguageProvider();

        String pythonCode = "#!/usr/bin/env python3\n\n" +
                "\"\"\"Module docstring\"\"\"\n\n" +
                "import os\n\n" +
                "CONSTANT = 42\n\n" +
                "def test_function():\n" +
                "    \"\"\"Function docstring\"\"\"\n" +
                "    return True\n";

        // 测试不需要截断的情况
        String result = provider.truncate(pythonCode, 1000);
        assertEquals(pythonCode, result);

        // 测试需要截断的情况
        String truncated = provider.truncate(pythonCode, 10);
        assertNotNull(truncated);
        assertTrue(truncated.contains("#!/usr/bin/env python3"));
        assertTrue(truncated.contains("import"));
        assertTrue(truncated.contains("[文件已截断"));
    }

    @Test
    void testJsTruncate() {
        JsLanguageProvider provider = new JsLanguageProvider();

        String jsCode = "#!/usr/bin/env node\n\n" +
                "'use strict';\n\n" +
                "import React from 'react';\n\n" +
                "const CONSTANT = 42;\n\n" +
                "function testFunction() {\n" +
                "    return true;\n" +
                "}\n";

        // 测试不需要截断的情况
        String result = provider.truncate(jsCode, 1000);
        assertEquals(jsCode, result);

        // 测试需要截断的情况
        String truncated = provider.truncate(jsCode, 10);
        assertNotNull(truncated);
        assertTrue(truncated.contains("#!/usr/bin/env node"));
        assertTrue(truncated.contains("import"));
        assertTrue(truncated.contains("[文件已截断"));
    }

    @Test
    void testEmptyContent() {
        JavaLanguageProvider provider = new JavaLanguageProvider();

        assertEquals("", provider.truncate(null, 100));
        assertEquals("", provider.truncate("", 100));
    }

    @Test
    void testEstimateTokens() {
        JavaLanguageProvider provider = new JavaLanguageProvider();

        assertEquals(0, provider.estimateTokens(null));
        assertEquals(0, provider.estimateTokens(""));

        // 简单估算：平均每个 token 约 4 个字符
        String content = "public class Test { }";
        int tokens = provider.estimateTokens(content);
        assertTrue(tokens > 0);
    }

    @Test
    void testGetSupportedExtensions() {
        String[] extensions = factory.getSupportedExtensions();
        assertNotNull(extensions);
        assertTrue(extensions.length >= 6); // java, py, js, ts, jsx, tsx
    }

    @Test
    void testRegisterProvider() {
        LanguageProvider customProvider = new LanguageProvider() {
            @Override
            public String truncate(String content, int maxTokens) {
                return content;
            }

            @Override
            public String getLanguageName() {
                return "Custom";
            }

            @Override
            public String[] getSupportedExtensions() {
                return new String[]{"custom"};
            }
        };

        factory.registerProvider(customProvider);

        LanguageProvider provider = factory.getProviderByExtension("custom");
        assertNotNull(provider);
        assertEquals("Custom", provider.getLanguageName());
    }
}
