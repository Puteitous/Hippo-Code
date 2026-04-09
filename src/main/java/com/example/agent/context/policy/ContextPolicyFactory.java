package com.example.agent.context.policy;

import com.example.agent.context.TrimPolicy;
import com.example.agent.context.config.ContextConfig;
import com.example.agent.service.TokenEstimator;

/**
 * ContextPolicy 工厂类
 * 根据配置创建对应的上下文策略
 */
public class ContextPolicyFactory {

    public static final String POLICY_SIMPLE = "simple";
    public static final String POLICY_THREE_TIER = "three-tier";

    private ContextPolicyFactory() {
        // 工具类，禁止实例化
    }

    /**
     * 根据配置创建策略
     *
     * @param config 上下文配置
     * @param trimPolicy 裁剪策略
     * @return 对应的 ContextPolicy 实例
     */
    public static ContextPolicy create(ContextConfig config, TrimPolicy trimPolicy) {
        if (config == null) {
            throw new IllegalArgumentException("config不能为null");
        }

        String policyName = config.getPolicy();

        return switch (policyName.toLowerCase()) {
            case POLICY_SIMPLE -> new SimplePolicy(trimPolicy);
            case POLICY_THREE_TIER -> new ThreeTierPolicy(trimPolicy);
            default -> {
                // 默认使用 SimplePolicy
                System.err.println("未知的策略: " + policyName + "，使用默认的 SimplePolicy");
                yield new SimplePolicy(trimPolicy);
            }
        };
    }

    /**
     * 创建默认策略（SimplePolicy）
     *
     * @param trimPolicy 裁剪策略
     * @return SimplePolicy 实例
     */
    public static ContextPolicy createDefault(TrimPolicy trimPolicy) {
        return new SimplePolicy(trimPolicy);
    }

    /**
     * 检查策略是否支持
     *
     * @param policyName 策略名称
     * @return 是否支持
     */
    public static boolean isSupported(String policyName) {
        if (policyName == null) {
            return false;
        }
        return switch (policyName.toLowerCase()) {
            case POLICY_SIMPLE, POLICY_THREE_TIER -> true;
            default -> false;
        };
    }

    /**
     * 获取支持的策略列表
     *
     * @return 支持的策略名称数组
     */
    public static String[] getSupportedPolicies() {
        return new String[]{POLICY_SIMPLE, POLICY_THREE_TIER};
    }
}
