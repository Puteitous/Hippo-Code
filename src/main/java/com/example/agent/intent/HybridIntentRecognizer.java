package com.example.agent.intent;

import com.example.agent.config.IntentConfig;
import com.example.agent.llm.client.LlmClient;
import com.example.agent.llm.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class HybridIntentRecognizer implements IntentRecognizer {

    private static final Logger logger = LoggerFactory.getLogger(HybridIntentRecognizer.class);

    private final RuleBasedIntentRecognizer ruleRecognizer;
    private final LlmIntentRecognizer llmRecognizer;
    private final IntentConfig.RecognitionStrategy config;

    public HybridIntentRecognizer(LlmClient llmClient) {
        this(llmClient, new IntentConfig().getRecognition());
    }

    public HybridIntentRecognizer(LlmClient llmClient, IntentConfig.RecognitionStrategy config) {
        this.ruleRecognizer = new RuleBasedIntentRecognizer();
        this.llmRecognizer = new LlmIntentRecognizer(llmClient);
        this.config = config;
        
        applyConfig();
    }

    private void applyConfig() {
        this.llmRecognizer.setEnabled(config.isLlmEnabled());
    }

    @Override
    public IntentResult recognize(String userInput) {
        return recognize(userInput, null);
    }

    @Override
    public IntentResult recognize(String userInput, List<Message> context) {
        if (userInput == null || userInput.trim().isEmpty()) {
            return IntentResult.unknown();
        }

        IntentResult ruleResult = ruleRecognizer.recognize(userInput, context);

        if (!config.isPreferLlm() && ruleResult.getConfidence() >= config.getHighConfidenceThreshold()) {
            logger.debug("规则识别器高置信度结果: {}", ruleResult);
            return ruleResult;
        }

        if (config.isPreferLlm() || ruleResult.getConfidence() < config.getLowConfidenceThreshold()) {
            IntentResult llmResult = llmRecognizer.recognize(userInput, context);
            if (llmResult.getConfidence() > ruleResult.getConfidence()) {
                logger.debug("使用 LLM 识别结果: {}", llmResult);
                return llmResult;
            }
        }

        logger.debug("使用规则识别结果: {}", ruleResult);
        return ruleResult;
    }

    public boolean isPreferLlm() {
        return config.isPreferLlm();
    }

    public void setPreferLlm(boolean preferLlm) {
        this.config.setPreferLlm(preferLlm);
    }

    public void setLlmEnabled(boolean enabled) {
        this.llmRecognizer.setEnabled(enabled);
        this.config.setLlmEnabled(enabled);
    }

    @Override
    public boolean isEnabled() {
        return ruleRecognizer.isEnabled() || llmRecognizer.isEnabled();
    }
}
