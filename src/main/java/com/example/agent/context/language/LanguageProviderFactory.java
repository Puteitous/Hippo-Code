package com.example.agent.context.language;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * LanguageProvider 工厂类
 * 根据文件扩展名选择合适的语言处理器
 */
public class LanguageProviderFactory {

    private static final Logger logger = LoggerFactory.getLogger(LanguageProviderFactory.class);

    private final Map<String, LanguageProvider> providers;

    public LanguageProviderFactory() {
        this.providers = new HashMap<>();
        registerDefaultProviders();
    }

    /**
     * 注册默认的语言处理器
     */
    private void registerDefaultProviders() {
        // Java
        JavaLanguageProvider javaProvider = new JavaLanguageProvider();
        registerProvider(javaProvider);

        // Python
        PythonLanguageProvider pythonProvider = new PythonLanguageProvider();
        registerProvider(pythonProvider);

        // JavaScript/TypeScript
        JsLanguageProvider jsProvider = new JsLanguageProvider();
        registerProvider(jsProvider);
    }

    /**
     * 注册语言处理器
     */
    public void registerProvider(LanguageProvider provider) {
        if (provider == null) {
            return;
        }

        for (String extension : provider.getSupportedExtensions()) {
            providers.put(extension.toLowerCase(), provider);
            logger.debug("注册语言处理器: {} -> {}", extension, provider.getLanguageName());
        }
    }

    /**
     * 根据文件路径获取语言处理器
     *
     * @param filePath 文件路径
     * @return 对应的 LanguageProvider，如果没有找到则返回 null
     */
    public LanguageProvider getProvider(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return null;
        }

        String extension = extractExtension(filePath);
        if (extension == null) {
            return null;
        }

        LanguageProvider provider = providers.get(extension.toLowerCase());
        if (provider != null) {
            logger.debug("找到语言处理器: {} -> {}", extension, provider.getLanguageName());
        }

        return provider;
    }

    /**
     * 根据文件扩展名获取语言处理器
     *
     * @param extension 文件扩展名（如 "java", "py"）
     * @return 对应的 LanguageProvider，如果没有找到则返回 null
     */
    public LanguageProvider getProviderByExtension(String extension) {
        if (extension == null || extension.isEmpty()) {
            return null;
        }

        String ext = extension.toLowerCase();
        if (ext.startsWith(".")) {
            ext = ext.substring(1);
        }

        return providers.get(ext);
    }

    /**
     * 提取文件扩展名
     */
    private String extractExtension(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return null;
        }

        int lastDot = filePath.lastIndexOf('.');
        if (lastDot == -1 || lastDot == filePath.length() - 1) {
            return null;
        }

        return filePath.substring(lastDot + 1).toLowerCase();
    }

    /**
     * 检查是否支持该文件类型
     *
     * @param filePath 文件路径
     * @return 是否支持
     */
    public boolean isSupported(String filePath) {
        return getProvider(filePath) != null;
    }

    /**
     * 获取所有支持的扩展名
     *
     * @return 支持的扩展名数组
     */
    public String[] getSupportedExtensions() {
        return providers.keySet().toArray(new String[0]);
    }

    /**
     * 获取处理器数量
     */
    public int getProviderCount() {
        return providers.size();
    }
}
