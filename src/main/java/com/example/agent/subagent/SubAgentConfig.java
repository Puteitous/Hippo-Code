package com.example.agent.subagent;

public class SubAgentConfig {
    
    private final boolean shareTerminalOutput;
    private final boolean enableDepthTracking;
    private final int customMaxTurns;
    
    private SubAgentConfig(Builder builder) {
        this.shareTerminalOutput = builder.shareTerminalOutput;
        this.enableDepthTracking = builder.enableDepthTracking;
        this.customMaxTurns = builder.customMaxTurns;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static SubAgentConfig defaults() {
        return new Builder().build();
    }
    
    public boolean isShareTerminalOutput() {
        return shareTerminalOutput;
    }
    
    public boolean isEnableDepthTracking() {
        return enableDepthTracking;
    }
    
    public int getCustomMaxTurns() {
        return customMaxTurns;
    }
    
    public static class Builder {
        
        private boolean shareTerminalOutput = false;
        private boolean enableDepthTracking = false;
        private int customMaxTurns = -1;
        
        public Builder shareTerminalOutput() {
            this.shareTerminalOutput = true;
            return this;
        }
        
        public Builder enableDepthTracking() {
            this.enableDepthTracking = true;
            return this;
        }
        
        public Builder maxTurns(int maxTurns) {
            this.customMaxTurns = maxTurns;
            return this;
        }
        
        public SubAgentConfig build() {
            return new SubAgentConfig(this);
        }
    }
}
