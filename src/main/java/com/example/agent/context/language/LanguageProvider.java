package com.example.agent.context.language;

/**
 * LanguageProvider 接口
 * 定义语言感知的智能截断策略
 */
public interface LanguageProvider {

    /**
     * 智能截断文件内容
     * 保留关键结构，截断次要内容
     *
     * @param content 原始文件内容
     * @param maxTokens 最大 token 限制
     * @return 截断后的内容
     */
    String truncate(String content, int maxTokens);

    /**
     * 获取语言名称
     */
    String getLanguageName();

    /**
     * 获取支持的文件扩展名
     */
    String[] getSupportedExtensions();

    /**
     * 检查是否支持该文件扩展名
     *
     * @param extension 文件扩展名（如 "java", "py"）
     * @return 是否支持
     */
    default boolean supportsExtension(String extension) {
        if (extension == null || extension.isEmpty()) {
            return false;
        }
        String ext = extension.toLowerCase();
        for (String supported : getSupportedExtensions()) {
            if (supported.equalsIgnoreCase(ext)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 计算内容的 token 数
     * 默认实现按字符数估算
     */
    default int estimateTokens(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        // 简单估算：平均每个 token 约 4 个字符
        return content.length() / 4;
    }
}
