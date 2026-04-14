package com.example.agent.config;

import com.example.agent.context.config.ContextConfig;
import com.example.agent.mcp.config.McpConfig;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Config {

    private static Config instance;

    private LlmConfig llm = new LlmConfig();
    private ToolsConfig tools = new ToolsConfig();
    private SessionConfig session = new SessionConfig();
    private UiConfig ui = new UiConfig();
    private ContextConfig context = new ContextConfig();
    private IntentConfig intent = new IntentConfig();
    private TokenEstimatorConfig tokenizer = new TokenEstimatorConfig();
    private RuleConfig rule = new RuleConfig();
    private CacheConfig cache = new CacheConfig();
    private IndexConfig index = new IndexConfig();
    private McpConfig mcp = new McpConfig();

    private transient ConfigLoader configLoader;

    Config() {
    }

    public static synchronized Config getInstance() {
        if (instance == null) {
            ConfigLoader loader = new ConfigLoader();
            instance = loader.load();
            instance.configLoader = loader;
            instance.loadFromEnvironment();
        }
        return instance;
    }

    private void loadFromEnvironment() {
        String envApiKey = System.getenv("DASHSCOPE_API_KEY");
        if (envApiKey != null && !envApiKey.isEmpty()) {
            llm.setApiKey(envApiKey);
            System.out.println("API Key loaded from environment variable: DASHSCOPE_API_KEY");
        }
        
        String envApiKeyAlt = System.getenv("OPENAI_API_KEY");
        if (envApiKeyAlt != null && !envApiKeyAlt.isEmpty() && (llm.getApiKey() == null || llm.getApiKey().isEmpty())) {
            llm.setApiKey(envApiKeyAlt);
            System.out.println("API Key loaded from environment variable: OPENAI_API_KEY");
        }
        
        String envBaseUrl = System.getenv("DASHSCOPE_BASE_URL");
        if (envBaseUrl != null && !envBaseUrl.isEmpty()) {
            llm.setBaseUrl(envBaseUrl);
        }
        
        String envModel = System.getenv("DASHSCOPE_MODEL");
        if (envModel != null && !envModel.isEmpty()) {
            llm.setModel(envModel);
        }
    }

    public void save() {
        if (configLoader == null) {
            configLoader = new ConfigLoader();
        }
        
        File configFile = configLoader.getConfigFile();
        try {
            ObjectMapper mapper;
            String fileName = configFile.getName().toLowerCase();
            
            if (fileName.endsWith(".yaml") || fileName.endsWith(".yml")) {
                mapper = new ObjectMapper(new YAMLFactory());
            } else {
                mapper = new ObjectMapper();
            }
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            mapper.writeValue(configFile, this);
            System.out.println("Configuration saved to: " + configFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Error saving config file: " + e.getMessage());
        }
    }

    public void reload() {
        if (configLoader == null) {
            configLoader = new ConfigLoader();
        }
        
        File configFile = configLoader.getConfigFile();
        if (configFile.exists()) {
            try {
                ObjectMapper mapper;
                String fileName = configFile.getName().toLowerCase();
                
                if (fileName.endsWith(".yaml") || fileName.endsWith(".yml")) {
                    mapper = new ObjectMapper(new YAMLFactory());
                } else {
                    mapper = new ObjectMapper();
                }
                
                Config reloaded = mapper.readValue(configFile, Config.class);
                this.llm = reloaded.llm;
                this.tools = reloaded.tools;
                this.session = reloaded.session;
                this.ui = reloaded.ui;
                this.context = reloaded.context;
                this.intent = reloaded.intent;
                this.tokenizer = reloaded.tokenizer;
                this.rule = reloaded.rule;
                this.cache = reloaded.cache;
                this.index = reloaded.index;
                this.loadFromEnvironment();
                System.out.println("Configuration reloaded from: " + configFile.getAbsolutePath());
            } catch (IOException e) {
                System.err.println("Error reloading config file: " + e.getMessage());
            }
        }
    }

    public boolean isValid() {
        return llm != null && llm.isValid();
    }

    public ValidationResult validate() {
        ValidationResult result = new ValidationResult();
        
        if (llm == null) {
            result.addError("llm", "LLM 配置不能为空");
            return result;
        }
        
        if (!llm.isLocalProvider()) {
            if (llm.getApiKey() == null || llm.getApiKey().isEmpty()) {
                result.addError("apiKey", "API Key 不能为空");
            } else if (llm.getApiKey().equals("your-api-key-here")) {
                result.addError("apiKey", "请设置有效的 API Key");
            }
        }
        
        if (llm.getBaseUrl() == null || llm.getBaseUrl().isEmpty()) {
            result.addError("baseUrl", "Base URL 不能为空");
        } else if (!llm.getBaseUrl().startsWith("http://") && !llm.getBaseUrl().startsWith("https://")) {
            result.addError("baseUrl", "Base URL 必须以 http:// 或 https:// 开头");
        }
        
        if (llm.getModel() == null || llm.getModel().isEmpty()) {
            result.addError("model", "模型名称不能为空");
        }
        
        if (llm.getMaxTokens() <= 0) {
            result.addError("maxTokens", "maxTokens 必须大于 0");
        }
        
        return result;
    }

    public static class ValidationResult {
        private final Map<String, String> errors = new HashMap<>();
        
        public void addError(String field, String message) {
            errors.put(field, message);
        }
        
        public boolean isValid() {
            return errors.isEmpty();
        }
        
        public Map<String, String> getErrors() {
            return errors;
        }
        
        public String getError(String field) {
            return errors.get(field);
        }
        
        public String getFirstError() {
            return errors.values().stream().findFirst().orElse(null);
        }
    }

    public LlmConfig getLlm() {
        return llm;
    }

    public void setLlm(LlmConfig llm) {
        this.llm = llm;
    }

    public ToolsConfig getTools() {
        return tools;
    }

    public void setTools(ToolsConfig tools) {
        this.tools = tools;
    }

    public SessionConfig getSession() {
        return session;
    }

    public void setSession(SessionConfig session) {
        this.session = session;
    }

    public UiConfig getUi() {
        return ui;
    }

    public void setUi(UiConfig ui) {
        this.ui = ui;
    }

    public ContextConfig getContext() {
        return context;
    }

    public void setContext(ContextConfig context) {
        this.context = context;
    }

    public IntentConfig getIntent() {
        return intent;
    }

    public void setIntent(IntentConfig intent) {
        this.intent = intent;
    }

    public TokenEstimatorConfig getTokenizer() {
        return tokenizer;
    }

    public void setTokenizer(TokenEstimatorConfig tokenizer) {
        this.tokenizer = tokenizer;
    }

    public RuleConfig getRule() {
        return rule;
    }

    public void setRule(RuleConfig rule) {
        this.rule = rule;
    }

    public CacheConfig getCache() {
        return cache;
    }

    public void setCache(CacheConfig cache) {
        this.cache = cache;
    }

    public IndexConfig getIndex() {
        return index;
    }

    public void setIndex(IndexConfig index) {
        this.index = index;
    }

    public McpConfig getMcp() {
        if (mcp == null) {
            mcp = new McpConfig();
        }
        return mcp;
    }

    public void setMcp(McpConfig mcp) {
        this.mcp = mcp;
    }

    @Deprecated
    public String getApiKey() {
        return llm != null ? llm.getApiKey() : null;
    }

    @Deprecated
    public void setApiKey(String apiKey) {
        if (llm != null) {
            llm.setApiKey(apiKey);
        }
    }

    @Deprecated
    public String getModel() {
        return llm != null ? llm.getModel() : null;
    }

    @Deprecated
    public void setModel(String model) {
        if (llm != null) {
            llm.setModel(model);
        }
    }

    @Deprecated
    public String getBaseUrl() {
        return llm != null ? llm.getBaseUrl() : null;
    }

    @Deprecated
    public void setBaseUrl(String baseUrl) {
        if (llm != null) {
            llm.setBaseUrl(baseUrl);
        }
    }

    @Deprecated
    public int getMaxTokens() {
        return llm != null ? llm.getMaxTokens() : 0;
    }

    @Deprecated
    public void setMaxTokens(int maxTokens) {
        if (llm != null) {
            llm.setMaxTokens(maxTokens);
        }
    }

    public String getConfigFilePath() {
        if (configLoader == null) {
            configLoader = new ConfigLoader();
        }
        return configLoader.getConfigFilePath();
    }

    @Override
    public String toString() {
        return "Config{" +
                "llm=" + llm +
                ", tools=" + tools +
                ", session=" + session +
                ", ui=" + ui +
                ", context=" + context +
                ", intent=" + intent +
                ", tokenizer=" + tokenizer +
                ", rule=" + rule +
                ", cache=" + cache +
                ", index=" + index +
                '}';
    }
}
