package com.example.agent.memory;

import java.nio.file.Path;

/**
 * @deprecated 已迁移到 {@link com.example.agent.memory.consolidation.ConsolidationGate}
 * 保留此类仅为向后兼容，新代码应使用 memory.consolidation 包中的版本
 */
@Deprecated
public class ConsolidationGate extends com.example.agent.memory.consolidation.ConsolidationGate {
    
    public ConsolidationGate(Path memoryDir) {
        super(memoryDir);
    }
}
