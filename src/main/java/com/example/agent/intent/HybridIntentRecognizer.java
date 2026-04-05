package com.example.agent.intent;

import com.example.agent.llm.client.LlmClient;
import com.example.agent.llm.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class HybridIntentRecognizer implements IntentRecognizer {

    private static final Logger logger = LoggerFactory.getLogger(HybridIntentRecognizer.class);

    private static final double HIGH_CONFIDENCE_THRESHOLD = 0.85;
    private static final double LOW_CONFIDENCE_THRESHOLD = 0.50;

    private final RuleBasedIntentRecognizer ruleRecognizer;
    private final LlmIntentRecognizer llmRecognizer;
    private boolean preferLlm = false;

    public HybridIntentRecognizer(LlmClient llmClient) {
        this.ruleRecognizer = new RuleBasedIntentRecognizer();
        this.llmRecognizer = new LlmIntentRecognizer(llmClient);
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

        if (ruleResult.getConfidence() >= HIGH_CONFIDENCE_THRESHOLD) {
            logger.debug("规则识别器高置信度结果: {}", ruleResult);
            return ruleResult;
        }

        if (preferLlm || ruleResult.getConfidence() < LOW_CONFIDENCE_THRESHOLD) {
            IntentResult llmResult = llmRecognizer.recognize(userInput, context);
            if (llmResult.getConfidence() > ruleResult.getConfidence()) {
                logger.debug("使用 LLM 识别结果: {}", llmResult);
                return llmResult;
            }
        }

        logger.debug("使用规则识别结果: {}", ruleResult);
        return ruleResult;
    }

    public void setPreferLlm(boolean preferLlm) {
        this.preferLlm = preferLlm;
    }

    public void setLlmEnabled(boolean enabled) {
        this.llmRecognizer.setEnabled(enabled);
    }

    @Override
    public boolean isEnabled() {
        return ruleRecognizer.isEnabled() || llmRecognizer.isEnabled();
    }
}
