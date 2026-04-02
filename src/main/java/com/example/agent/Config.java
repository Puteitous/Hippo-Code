package com.example.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Config {

    private static final String CONFIG_FILE_NAME = "config.json";
    private static final String DEFAULT_MODEL = "qwen3.5-plus";
    private static final String DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com";
    private static final int DEFAULT_MAX_TOKENS = 2048;
    
    private static final String ENV_API_KEY = "DASHSCOPE_API_KEY";
    private static final String ENV_BASE_URL = "DASHSCOPE_BASE_URL";
    private static final String ENV_MODEL = "DASHSCOPE_MODEL";

    private static Config instance;

    private String apiKey;
    private String model = DEFAULT_MODEL;
    
    @JsonProperty("baseUrl")
    private String baseUrl = DEFAULT_BASE_URL;
    
    private int maxTokens = DEFAULT_MAX_TOKENS;

    private Config() {
    }

    public static synchronized Config getInstance() {
        if (instance == null) {
            instance = loadOrCreateConfig();
        }
        return instance;
    }

    private static Config loadOrCreateConfig() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        
        Config config = null;
        
        File configFile = getConfigFile();
        if (configFile.exists()) {
            try {
                config = mapper.readValue(configFile, Config.class);
                System.out.println("Configuration loaded from: " + configFile.getAbsolutePath());
            } catch (IOException e) {
                System.err.println("Error reading config file: " + e.getMessage());
            }
        }
        
        if (config == null) {
            config = createDefaultConfig();
            try {
                mapper.writeValue(configFile, config);
                System.out.println("Default configuration created at: " + configFile.getAbsolutePath());
                System.out.println("Please edit the config file or set environment variables.");
            } catch (IOException e) {
                System.err.println("Error creating default config file: " + e.getMessage());
            }
        }
        
        config.loadFromEnvironment();
        
        return config;
    }

    private void loadFromEnvironment() {
        String envApiKey = System.getenv(ENV_API_KEY);
        if (envApiKey != null && !envApiKey.isEmpty()) {
            this.apiKey = envApiKey;
            System.out.println("API Key loaded from environment variable: " + ENV_API_KEY);
        }
        
        String envBaseUrl = System.getenv(ENV_BASE_URL);
        if (envBaseUrl != null && !envBaseUrl.isEmpty()) {
            this.baseUrl = envBaseUrl;
        }
        
        String envModel = System.getenv(ENV_MODEL);
        if (envModel != null && !envModel.isEmpty()) {
            this.model = envModel;
        }
    }

    private static File getConfigFile() {
        String jarDir = getJarDirectory();
        return new File(jarDir, CONFIG_FILE_NAME);
    }

    private static String getJarDirectory() {
        // 优先使用当前工作目录
        String userDir = System.getProperty("user.dir");
        if (userDir != null && !userDir.isEmpty()) {
            File configFile = new File(userDir, CONFIG_FILE_NAME);
            if (configFile.exists()) {
                return userDir;
            }
        }
        
        // 尝试从 JAR 目录查找
        try {
            java.net.URI uri = Config.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            File file = new File(uri);
            String jarDir = file.isFile() ? file.getParent() : file.getAbsolutePath();
            
            // 如果 JAR 在 target 目录，则使用上级目录（项目根目录）
            if (jarDir != null && jarDir.endsWith("target")) {
                File parentDir = new File(jarDir).getParentFile();
                if (parentDir != null) {
                    return parentDir.getAbsolutePath();
                }
            }
            
            if (jarDir != null) {
                return jarDir;
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not determine JAR directory: " + e.getMessage());
        }
        
        return userDir;
    }

    private static Config createDefaultConfig() {
        Config config = new Config();
        config.apiKey = "your-api-key-here";
        config.model = DEFAULT_MODEL;
        config.baseUrl = DEFAULT_BASE_URL;
        config.maxTokens = DEFAULT_MAX_TOKENS;
        return config;
    }

    public void save() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        
        File configFile = getConfigFile();
        try {
            mapper.writeValue(configFile, this);
            System.out.println("Configuration saved to: " + configFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Error saving config file: " + e.getMessage());
        }
    }

    public void reload() {
        ObjectMapper mapper = new ObjectMapper();
        File configFile = getConfigFile();
        
        if (configFile.exists()) {
            try {
                Config reloaded = mapper.readValue(configFile, Config.class);
                this.apiKey = reloaded.apiKey;
                this.model = reloaded.model;
                this.baseUrl = reloaded.baseUrl;
                this.maxTokens = reloaded.maxTokens;
                System.out.println("Configuration reloaded from: " + configFile.getAbsolutePath());
            } catch (IOException e) {
                System.err.println("Error reloading config file: " + e.getMessage());
            }
        }
    }

    public boolean isValid() {
        return apiKey != null 
            && !apiKey.isEmpty() 
            && !apiKey.equals("your-api-key-here");
    }

    public ValidationResult validate() {
        ValidationResult result = new ValidationResult();
        
        if (apiKey == null || apiKey.isEmpty()) {
            result.addError("apiKey", "API Key 不能为空");
        } else if (apiKey.equals("your-api-key-here")) {
            result.addError("apiKey", "请设置有效的 API Key");
        }
        
        if (baseUrl == null || baseUrl.isEmpty()) {
            result.addError("baseUrl", "Base URL 不能为空");
        } else if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            result.addError("baseUrl", "Base URL 必须以 http:// 或 https:// 开头");
        }
        
        if (model == null || model.isEmpty()) {
            result.addError("model", "模型名称不能为空");
        }
        
        if (maxTokens <= 0) {
            result.addError("maxTokens", "maxTokens 必须大于 0");
        }
        
        return result;
    }

    public static class ValidationResult {
        private final java.util.Map<String, String> errors = new java.util.HashMap<>();
        
        public void addError(String field, String message) {
            errors.put(field, message);
        }
        
        public boolean isValid() {
            return errors.isEmpty();
        }
        
        public java.util.Map<String, String> getErrors() {
            return errors;
        }
        
        public String getError(String field) {
            return errors.get(field);
        }
        
        public String getFirstError() {
            return errors.values().stream().findFirst().orElse(null);
        }
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    @Override
    public String toString() {
        return "Config{" +
                "apiKey='" + maskApiKey() + '\'' +
                ", model='" + model + '\'' +
                ", baseUrl='" + baseUrl + '\'' +
                ", maxTokens=" + maxTokens +
                '}';
    }

    private String maskApiKey() {
        if (apiKey == null || apiKey.length() < 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }
}
