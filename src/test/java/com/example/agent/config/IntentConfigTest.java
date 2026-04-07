package com.example.agent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

class IntentConfigTest {

    private ObjectMapper yamlMapper;
    private ObjectMapper jsonMapper;

    @BeforeEach
    void setUp() {
        yamlMapper = new ObjectMapper(new YAMLFactory());
        jsonMapper = new ObjectMapper();
    }

    @Test
    void testDefaultIntentConfig() {
        IntentConfig config = new IntentConfig();
        
        assertTrue(config.isEnabled());
        assertNotNull(config.getRecognition());
        assertNotNull(config.getPlanning());
        
        assertEquals("hybrid", config.getRecognition().getMode());
        assertTrue(config.getRecognition().isLlmEnabled());
        assertFalse(config.getRecognition().isPreferLlm());
        assertEquals(0.85, config.getRecognition().getHighConfidenceThreshold());
        assertEquals(0.50, config.getRecognition().getLowConfidenceThreshold());
        assertTrue(config.getRecognition().isFallbackToRules());
        
        assertEquals("composite", config.getPlanning().getMode());
        assertFalse(config.getPlanning().isPreferLlm());
        assertEquals(0.85, config.getPlanning().getHighConfidenceThreshold());
        assertEquals(0.50, config.getPlanning().getLowConfidenceThreshold());
        assertTrue(config.getPlanning().isEnableComplexIntentDetection());
    }

    @Test
    void testYamlSerialization() throws Exception {
        IntentConfig config = new IntentConfig();
        config.setEnabled(true);
        config.getRecognition().setPreferLlm(true);
        config.getRecognition().setHighConfidenceThreshold(0.90);
        config.getPlanning().setPreferLlm(true);
        
        StringWriter writer = new StringWriter();
        yamlMapper.writeValue(writer, config);
        
        String yaml = writer.toString();
        assertTrue(yaml.contains("enabled: true"));
        assertTrue(yaml.contains("prefer_llm: true"));
        assertTrue(yaml.contains("high_confidence_threshold: 0.9"));
    }

    @Test
    void testYamlDeserialization() throws Exception {
        String yaml = """
            enabled: false
            recognition:
              mode: llm
              llm_enabled: false
              prefer_llm: true
              high_confidence_threshold: 0.95
              low_confidence_threshold: 0.60
              fallback_to_rules: false
            planning:
              mode: simple
              prefer_llm: true
              high_confidence_threshold: 0.90
              low_confidence_threshold: 0.55
              enable_complex_intent_detection: false
            """;
        
        IntentConfig config = yamlMapper.readValue(yaml, IntentConfig.class);
        
        assertFalse(config.isEnabled());
        assertEquals("llm", config.getRecognition().getMode());
        assertFalse(config.getRecognition().isLlmEnabled());
        assertTrue(config.getRecognition().isPreferLlm());
        assertEquals(0.95, config.getRecognition().getHighConfidenceThreshold());
        assertEquals(0.60, config.getRecognition().getLowConfidenceThreshold());
        assertFalse(config.getRecognition().isFallbackToRules());
        
        assertEquals("simple", config.getPlanning().getMode());
        assertTrue(config.getPlanning().isPreferLlm());
        assertEquals(0.90, config.getPlanning().getHighConfidenceThreshold());
        assertEquals(0.55, config.getPlanning().getLowConfidenceThreshold());
        assertFalse(config.getPlanning().isEnableComplexIntentDetection());
    }

    @Test
    void testRecognitionStrategySetters() {
        IntentConfig.RecognitionStrategy strategy = new IntentConfig.RecognitionStrategy();
        
        strategy.setMode("rule");
        strategy.setLlmEnabled(false);
        strategy.setPreferLlm(true);
        strategy.setHighConfidenceThreshold(0.95);
        strategy.setLowConfidenceThreshold(0.40);
        strategy.setFallbackToRules(false);
        
        assertEquals("rule", strategy.getMode());
        assertFalse(strategy.isLlmEnabled());
        assertTrue(strategy.isPreferLlm());
        assertEquals(0.95, strategy.getHighConfidenceThreshold());
        assertEquals(0.40, strategy.getLowConfidenceThreshold());
        assertFalse(strategy.isFallbackToRules());
    }

    @Test
    void testPlanningStrategySetters() {
        IntentConfig.PlanningStrategy strategy = new IntentConfig.PlanningStrategy();
        
        strategy.setMode("llm");
        strategy.setPreferLlm(true);
        strategy.setHighConfidenceThreshold(0.90);
        strategy.setLowConfidenceThreshold(0.45);
        strategy.setEnableComplexIntentDetection(false);
        
        assertEquals("llm", strategy.getMode());
        assertTrue(strategy.isPreferLlm());
        assertEquals(0.90, strategy.getHighConfidenceThreshold());
        assertEquals(0.45, strategy.getLowConfidenceThreshold());
        assertFalse(strategy.isEnableComplexIntentDetection());
    }

    @Test
    void testConfigIntegration() {
        Config config = new Config();
        IntentConfig intentConfig = config.getIntent();
        
        assertNotNull(intentConfig);
        assertTrue(intentConfig.isEnabled());
        assertNotNull(intentConfig.getRecognition());
        assertNotNull(intentConfig.getPlanning());
    }

    @Test
    void testConfigSetIntent() {
        Config config = new Config();
        IntentConfig newIntentConfig = new IntentConfig();
        newIntentConfig.setEnabled(false);
        newIntentConfig.getRecognition().setPreferLlm(true);
        
        config.setIntent(newIntentConfig);
        
        assertFalse(config.getIntent().isEnabled());
        assertTrue(config.getIntent().getRecognition().isPreferLlm());
    }

    @Test
    void testToString() {
        IntentConfig config = new IntentConfig();
        String str = config.toString();
        
        assertTrue(str.contains("IntentConfig"));
        assertTrue(str.contains("enabled=true"));
        assertTrue(str.contains("recognition="));
        assertTrue(str.contains("planning="));
    }

    @Test
    void testRecognitionStrategyToString() {
        IntentConfig.RecognitionStrategy strategy = new IntentConfig.RecognitionStrategy();
        String str = strategy.toString();
        
        assertTrue(str.contains("RecognitionStrategy"));
        assertTrue(str.contains("mode="));
        assertTrue(str.contains("llmEnabled="));
        assertTrue(str.contains("preferLlm="));
    }

    @Test
    void testPlanningStrategyToString() {
        IntentConfig.PlanningStrategy strategy = new IntentConfig.PlanningStrategy();
        String str = strategy.toString();
        
        assertTrue(str.contains("PlanningStrategy"));
        assertTrue(str.contains("mode="));
        assertTrue(str.contains("preferLlm="));
    }

    @Test
    void testRecognitionStrategyHighConfidenceThresholdBoundaryMin() {
        IntentConfig.RecognitionStrategy strategy = new IntentConfig.RecognitionStrategy();
        strategy.setHighConfidenceThreshold(0.0);
        assertEquals(0.0, strategy.getHighConfidenceThreshold());
    }

    @Test
    void testRecognitionStrategyHighConfidenceThresholdBoundaryMax() {
        IntentConfig.RecognitionStrategy strategy = new IntentConfig.RecognitionStrategy();
        strategy.setHighConfidenceThreshold(1.0);
        assertEquals(1.0, strategy.getHighConfidenceThreshold());
    }

    @Test
    void testRecognitionStrategyHighConfidenceThresholdNegative() {
        IntentConfig.RecognitionStrategy strategy = new IntentConfig.RecognitionStrategy();
        assertThrows(IllegalArgumentException.class, () -> 
            strategy.setHighConfidenceThreshold(-0.01));
        assertThrows(IllegalArgumentException.class, () -> 
            strategy.setHighConfidenceThreshold(-1.0));
    }

    @Test
    void testRecognitionStrategyHighConfidenceThresholdExceedsMax() {
        IntentConfig.RecognitionStrategy strategy = new IntentConfig.RecognitionStrategy();
        assertThrows(IllegalArgumentException.class, () -> 
            strategy.setHighConfidenceThreshold(1.01));
        assertThrows(IllegalArgumentException.class, () -> 
            strategy.setHighConfidenceThreshold(2.0));
    }

    @Test
    void testRecognitionStrategyLowConfidenceThresholdBoundaryMin() {
        IntentConfig.RecognitionStrategy strategy = new IntentConfig.RecognitionStrategy();
        strategy.setLowConfidenceThreshold(0.0);
        assertEquals(0.0, strategy.getLowConfidenceThreshold());
    }

    @Test
    void testRecognitionStrategyLowConfidenceThresholdBoundaryMax() {
        IntentConfig.RecognitionStrategy strategy = new IntentConfig.RecognitionStrategy();
        strategy.setLowConfidenceThreshold(1.0);
        assertEquals(1.0, strategy.getLowConfidenceThreshold());
    }

    @Test
    void testRecognitionStrategyLowConfidenceThresholdNegative() {
        IntentConfig.RecognitionStrategy strategy = new IntentConfig.RecognitionStrategy();
        assertThrows(IllegalArgumentException.class, () -> 
            strategy.setLowConfidenceThreshold(-0.01));
        assertThrows(IllegalArgumentException.class, () -> 
            strategy.setLowConfidenceThreshold(-1.0));
    }

    @Test
    void testRecognitionStrategyLowConfidenceThresholdExceedsMax() {
        IntentConfig.RecognitionStrategy strategy = new IntentConfig.RecognitionStrategy();
        assertThrows(IllegalArgumentException.class, () -> 
            strategy.setLowConfidenceThreshold(1.01));
        assertThrows(IllegalArgumentException.class, () -> 
            strategy.setLowConfidenceThreshold(2.0));
    }

    @Test
    void testPlanningStrategyHighConfidenceThresholdBoundaryMin() {
        IntentConfig.PlanningStrategy strategy = new IntentConfig.PlanningStrategy();
        strategy.setHighConfidenceThreshold(0.0);
        assertEquals(0.0, strategy.getHighConfidenceThreshold());
    }

    @Test
    void testPlanningStrategyHighConfidenceThresholdBoundaryMax() {
        IntentConfig.PlanningStrategy strategy = new IntentConfig.PlanningStrategy();
        strategy.setHighConfidenceThreshold(1.0);
        assertEquals(1.0, strategy.getHighConfidenceThreshold());
    }

    @Test
    void testPlanningStrategyHighConfidenceThresholdNegative() {
        IntentConfig.PlanningStrategy strategy = new IntentConfig.PlanningStrategy();
        assertThrows(IllegalArgumentException.class, () -> 
            strategy.setHighConfidenceThreshold(-0.01));
        assertThrows(IllegalArgumentException.class, () -> 
            strategy.setHighConfidenceThreshold(-1.0));
    }

    @Test
    void testPlanningStrategyHighConfidenceThresholdExceedsMax() {
        IntentConfig.PlanningStrategy strategy = new IntentConfig.PlanningStrategy();
        assertThrows(IllegalArgumentException.class, () -> 
            strategy.setHighConfidenceThreshold(1.01));
        assertThrows(IllegalArgumentException.class, () -> 
            strategy.setHighConfidenceThreshold(2.0));
    }

    @Test
    void testPlanningStrategyLowConfidenceThresholdBoundaryMin() {
        IntentConfig.PlanningStrategy strategy = new IntentConfig.PlanningStrategy();
        strategy.setLowConfidenceThreshold(0.0);
        assertEquals(0.0, strategy.getLowConfidenceThreshold());
    }

    @Test
    void testPlanningStrategyLowConfidenceThresholdBoundaryMax() {
        IntentConfig.PlanningStrategy strategy = new IntentConfig.PlanningStrategy();
        strategy.setLowConfidenceThreshold(1.0);
        assertEquals(1.0, strategy.getLowConfidenceThreshold());
    }

    @Test
    void testPlanningStrategyLowConfidenceThresholdNegative() {
        IntentConfig.PlanningStrategy strategy = new IntentConfig.PlanningStrategy();
        assertThrows(IllegalArgumentException.class, () -> 
            strategy.setLowConfidenceThreshold(-0.01));
        assertThrows(IllegalArgumentException.class, () -> 
            strategy.setLowConfidenceThreshold(-1.0));
    }

    @Test
    void testPlanningStrategyLowConfidenceThresholdExceedsMax() {
        IntentConfig.PlanningStrategy strategy = new IntentConfig.PlanningStrategy();
        assertThrows(IllegalArgumentException.class, () -> 
            strategy.setLowConfidenceThreshold(1.01));
        assertThrows(IllegalArgumentException.class, () -> 
            strategy.setLowConfidenceThreshold(2.0));
    }

    @Test
    void testThresholdBoundaryPrecision() {
        IntentConfig.RecognitionStrategy strategy = new IntentConfig.RecognitionStrategy();
        
        strategy.setHighConfidenceThreshold(0.001);
        assertEquals(0.001, strategy.getHighConfidenceThreshold());
        
        strategy.setHighConfidenceThreshold(0.999);
        assertEquals(0.999, strategy.getHighConfidenceThreshold());
        
        strategy.setLowConfidenceThreshold(0.001);
        assertEquals(0.001, strategy.getLowConfidenceThreshold());
        
        strategy.setLowConfidenceThreshold(0.999);
        assertEquals(0.999, strategy.getLowConfidenceThreshold());
    }
}
