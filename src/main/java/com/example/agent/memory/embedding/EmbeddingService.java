package com.example.agent.memory.embedding;

/**
 * 向量化服务接口
 * 
 * 负责将文本转换为向量表示，用于语义相似度检索
 */
public interface EmbeddingService {
    
    /**
     * 将文本转换为向量
     * 
     * @param text 输入文本
     * @return 浮点数组表示的向量
     */
    float[] embed(String text);
    
    /**
     * 批量向量化
     * 
     * @param texts 输入文本列表
     * @return 向量列表
     */
    float[][] embedBatch(String[] texts);
    
    /**
     * 检查服务是否可用
     * 
     * @return true 如果服务可用
     */
    boolean isAvailable();
    
    /**
     * 获取向量维度
     * 
     * @return 向量维度数
     */
    int getDimension();
    
    /**
     * 关闭服务，释放资源
     */
    void close();
}
