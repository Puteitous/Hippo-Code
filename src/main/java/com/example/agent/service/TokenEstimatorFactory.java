package com.example.agent.service;

import com.example.agent.config.Config;
import com.example.agent.config.TokenEstimatorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TokenEstimatorFactory {

    private static final Logger logger = LoggerFactory.getLogger(TokenEstimatorFactory.class);

    public static TokenEstimator getDefault() {
        return create(null);
    }

    public static TokenEstimator create(Config config) {
        TokenEstimatorConfig tokenizerConfig = config != null ? config.getTokenizer() : null;
        
        String tokenizerType = tokenizerConfig != null ? tokenizerConfig.getType() : TokenEstimatorConfig.DEFAULT_TYPE;
        String modelName = tokenizerConfig != null ? tokenizerConfig.getModel() : null;
        boolean cacheEnabled = tokenizerConfig != null ? tokenizerConfig.isCacheEnabled() : true;
        int cacheMaxSize = tokenizerConfig != null ? tokenizerConfig.getCacheMaxSize() : TokenEstimatorConfig.DEFAULT_CACHE_MAX_SIZE;
        
        String envType = System.getenv("TOKENIZER_TYPE");
        if (envType != null && !envType.isEmpty()) {
            tokenizerType = envType;
        }
        
        if (modelName == null && config != null) {
            modelName = config.getModel();
        }
        
        return create(tokenizerType, modelName, cacheEnabled, cacheMaxSize);
    }

    public static TokenEstimator create(String tokenizerType, String modelName) {
        return create(tokenizerType, modelName, true, TokenEstimatorConfig.DEFAULT_CACHE_MAX_SIZE);
    }

    public static TokenEstimator create(String tokenizerType, String modelName, boolean cacheEnabled, int cacheMaxSize) {
        if (tokenizerType == null || tokenizerType.isEmpty()) {
            tokenizerType = TokenEstimatorConfig.DEFAULT_TYPE;
        }
        
        logger.info("创建TokenEstimator: type={}, model={}, cacheEnabled={}, cacheMaxSize={}", 
            tokenizerType, modelName, cacheEnabled, cacheMaxSize);
        
        if (TokenEstimatorConfig.TYPE_TIKTOKEN.equalsIgnoreCase(tokenizerType)) {
            try {
                return new TiktokenEstimator(modelName, cacheEnabled, cacheMaxSize);
            } catch (Exception e) {
                logger.warn("Tiktoken初始化失败，回退到SimpleTokenEstimator: {}", e.getMessage());
                return new SimpleTokenEstimator();
            }
        }
        
        return new SimpleTokenEstimator();
    }

    public static TokenEstimator createDefault() {
        return new SimpleTokenEstimator();
    }
}
