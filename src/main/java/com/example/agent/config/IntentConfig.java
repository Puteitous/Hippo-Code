package com.example.agent.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class IntentConfig {

    private boolean enabled = true;
    private RecognitionStrategy recognition = new RecognitionStrategy();
    private PlanningStrategy planning = new PlanningStrategy();

    public IntentConfig() {
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public RecognitionStrategy getRecognition() {
        return recognition;
    }

    public void setRecognition(RecognitionStrategy recognition) {
        this.recognition = recognition;
    }

    public PlanningStrategy getPlanning() {
        return planning;
    }

    public void setPlanning(PlanningStrategy planning) {
        this.planning = planning;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RecognitionStrategy {
        private String mode = "hybrid";
        
        @JsonProperty("llm_enabled")
        private boolean llmEnabled = true;
        
        @JsonProperty("prefer_llm")
        private boolean preferLlm = false;
        
        @JsonProperty("high_confidence_threshold")
        private double highConfidenceThreshold = 0.85;
        
        @JsonProperty("low_confidence_threshold")
        private double lowConfidenceThreshold = 0.50;
        
        @JsonProperty("fallback_to_rules")
        private boolean fallbackToRules = true;

        public RecognitionStrategy() {
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public boolean isLlmEnabled() {
            return llmEnabled;
        }

        public void setLlmEnabled(boolean llmEnabled) {
            this.llmEnabled = llmEnabled;
        }

        public boolean isPreferLlm() {
            return preferLlm;
        }

        public void setPreferLlm(boolean preferLlm) {
            this.preferLlm = preferLlm;
        }

        public double getHighConfidenceThreshold() {
            return highConfidenceThreshold;
        }

        public void setHighConfidenceThreshold(double highConfidenceThreshold) {
            this.highConfidenceThreshold = highConfidenceThreshold;
        }

        public double getLowConfidenceThreshold() {
            return lowConfidenceThreshold;
        }

        public void setLowConfidenceThreshold(double lowConfidenceThreshold) {
            this.lowConfidenceThreshold = lowConfidenceThreshold;
        }

        public boolean isFallbackToRules() {
            return fallbackToRules;
        }

        public void setFallbackToRules(boolean fallbackToRules) {
            this.fallbackToRules = fallbackToRules;
        }

        @Override
        public String toString() {
            return "RecognitionStrategy{" +
                    "mode='" + mode + '\'' +
                    ", llmEnabled=" + llmEnabled +
                    ", preferLlm=" + preferLlm +
                    ", highConfidenceThreshold=" + highConfidenceThreshold +
                    ", lowConfidenceThreshold=" + lowConfidenceThreshold +
                    ", fallbackToRules=" + fallbackToRules +
                    '}';
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlanningStrategy {
        private String mode = "composite";
        
        @JsonProperty("prefer_llm")
        private boolean preferLlm = false;
        
        @JsonProperty("high_confidence_threshold")
        private double highConfidenceThreshold = 0.85;
        
        @JsonProperty("low_confidence_threshold")
        private double lowConfidenceThreshold = 0.50;
        
        @JsonProperty("enable_complex_intent_detection")
        private boolean enableComplexIntentDetection = true;

        public PlanningStrategy() {
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public boolean isPreferLlm() {
            return preferLlm;
        }

        public void setPreferLlm(boolean preferLlm) {
            this.preferLlm = preferLlm;
        }

        public double getHighConfidenceThreshold() {
            return highConfidenceThreshold;
        }

        public void setHighConfidenceThreshold(double highConfidenceThreshold) {
            this.highConfidenceThreshold = highConfidenceThreshold;
        }

        public double getLowConfidenceThreshold() {
            return lowConfidenceThreshold;
        }

        public void setLowConfidenceThreshold(double lowConfidenceThreshold) {
            this.lowConfidenceThreshold = lowConfidenceThreshold;
        }

        public boolean isEnableComplexIntentDetection() {
            return enableComplexIntentDetection;
        }

        public void setEnableComplexIntentDetection(boolean enableComplexIntentDetection) {
            this.enableComplexIntentDetection = enableComplexIntentDetection;
        }

        @Override
        public String toString() {
            return "PlanningStrategy{" +
                    "mode='" + mode + '\'' +
                    ", preferLlm=" + preferLlm +
                    ", highConfidenceThreshold=" + highConfidenceThreshold +
                    ", lowConfidenceThreshold=" + lowConfidenceThreshold +
                    ", enableComplexIntentDetection=" + enableComplexIntentDetection +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "IntentConfig{" +
                "enabled=" + enabled +
                ", recognition=" + recognition +
                ", planning=" + planning +
                '}';
    }
}
