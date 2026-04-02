package com.example.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {

    @TempDir
    Path tempDir;

    private File testConfigFile;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        resetConfigInstance();
        testConfigFile = tempDir.resolve("config.json").toFile();
        mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @AfterEach
    void tearDown() throws Exception {
        resetConfigInstance();
    }

    private void resetConfigInstance() throws Exception {
        Field instanceField = Config.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }

    private Config createConfigInstance() throws Exception {
        Constructor<Config> constructor = Config.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    @Test
    void testDefaultValues() throws Exception {
        Config config = createConfigInstance();
        
        assertEquals("qwen3.5-plus", config.getModel());
        assertEquals("https://dashscope.aliyuncs.com", config.getBaseUrl());
        assertEquals(2048, config.getMaxTokens());
        assertNull(config.getApiKey());
    }

    @Test
    void testSettersAndGetters() throws Exception {
        Config config = createConfigInstance();
        
        config.setApiKey("test-api-key");
        config.setModel("qwen-max");
        config.setBaseUrl("https://custom.api.com");
        config.setMaxTokens(4096);
        
        assertEquals("test-api-key", config.getApiKey());
        assertEquals("qwen-max", config.getModel());
        assertEquals("https://custom.api.com", config.getBaseUrl());
        assertEquals(4096, config.getMaxTokens());
    }

    @Test
    void testIsValidWithNullApiKey() throws Exception {
        Config config = createConfigInstance();
        assertFalse(config.isValid());
    }

    @Test
    void testIsValidWithEmptyApiKey() throws Exception {
        Config config = createConfigInstance();
        config.setApiKey("");
        assertFalse(config.isValid());
    }

    @Test
    void testIsValidWithDefaultPlaceholder() throws Exception {
        Config config = createConfigInstance();
        config.setApiKey("your-api-key-here");
        assertFalse(config.isValid());
    }

    @Test
    void testIsValidWithValidApiKey() throws Exception {
        Config config = createConfigInstance();
        config.setApiKey("sk-1234567890abcdef");
        assertTrue(config.isValid());
    }

    @Test
    void testMaskApiKeyWithNull() throws Exception {
        Config config = createConfigInstance();
        config.setApiKey(null);
        String masked = config.toString();
        assertTrue(masked.contains("apiKey='****'"));
    }

    @Test
    void testMaskApiKeyWithShortKey() throws Exception {
        Config config = createConfigInstance();
        config.setApiKey("abc");
        String masked = config.toString();
        assertTrue(masked.contains("apiKey='****'"));
    }

    @Test
    void testMaskApiKeyWithLongKey() throws Exception {
        Config config = createConfigInstance();
        config.setApiKey("sk-1234567890abcdefghijklmnop");
        String masked = config.toString();
        assertTrue(masked.contains("sk-1****mnop"));
        assertFalse(masked.contains("1234567890abcdefghijkl"));
    }

    @Test
    void testToString() throws Exception {
        Config config = createConfigInstance();
        config.setApiKey("sk-test12345678key");
        config.setModel("qwen-max");
        config.setBaseUrl("https://api.test.com");
        config.setMaxTokens(1024);
        
        String str = config.toString();
        assertTrue(str.contains("model='qwen-max'"));
        assertTrue(str.contains("baseUrl='https://api.test.com'"));
        assertTrue(str.contains("maxTokens=1024"));
    }

    @Test
    void testJsonSerialization() throws Exception {
        Config config = createConfigInstance();
        config.setApiKey("test-key-123");
        config.setModel("qwen-max");
        config.setBaseUrl("https://test.api.com");
        config.setMaxTokens(8192);
        
        String json = mapper.writeValueAsString(config);
        
        assertTrue(json.contains("\"apiKey\" : \"test-key-123\""));
        assertTrue(json.contains("\"model\" : \"qwen-max\""));
        assertTrue(json.contains("\"baseUrl\" : \"https://test.api.com\""));
        assertTrue(json.contains("\"maxTokens\" : 8192"));
    }

    @Test
    void testJsonDeserialization() throws IOException {
        String json = """
            {
                "apiKey" : "deserialized-key",
                "model" : "qwen-turbo",
                "baseUrl" : "https://deserialized.api.com",
                "maxTokens" : 512
            }
            """;
        
        Config config = mapper.readValue(json, Config.class);
        
        assertEquals("deserialized-key", config.getApiKey());
        assertEquals("qwen-turbo", config.getModel());
        assertEquals("https://deserialized.api.com", config.getBaseUrl());
        assertEquals(512, config.getMaxTokens());
    }

    @Test
    void testJsonDeserializationWithMissingFields() throws IOException {
        String json = """
            {
                "apiKey" : "partial-key"
            }
            """;
        
        Config config = mapper.readValue(json, Config.class);
        
        assertEquals("partial-key", config.getApiKey());
        assertEquals("qwen3.5-plus", config.getModel());
        assertEquals("https://dashscope.aliyuncs.com", config.getBaseUrl());
        assertEquals(2048, config.getMaxTokens());
    }

    @Test
    void testJsonDeserializationIgnoresUnknownFields() throws IOException {
        String json = """
            {
                "apiKey" : "test-key",
                "unknownField" : "should be ignored",
                "anotherUnknown" : 12345
            }
            """;
        
        assertDoesNotThrow(() -> mapper.readValue(json, Config.class));
    }
}
