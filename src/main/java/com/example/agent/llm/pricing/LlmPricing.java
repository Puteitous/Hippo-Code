package com.example.agent.llm.pricing;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class LlmPricing {

    private LlmPricing() {}

    private static final Map<String, ModelPrice> PRICING = new HashMap<>();

    static {
        PRICING.put("gpt-4o", new ModelPrice(
                "openai", "gpt-4o",
                usdPerMillion(2.50),
                usdPerMillion(10.00)
        ));
        PRICING.put("gpt-4o-mini", new ModelPrice(
                "openai", "gpt-4o-mini",
                usdPerMillion(0.15),
                usdPerMillion(0.60)
        ));
        PRICING.put("gpt-4-turbo", new ModelPrice(
                "openai", "gpt-4-turbo",
                usdPerMillion(10.00),
                usdPerMillion(30.00)
        ));
        PRICING.put("gpt-3.5-turbo", new ModelPrice(
                "openai", "gpt-3.5-turbo",
                usdPerMillion(0.50),
                usdPerMillion(1.50)
        ));

        PRICING.put("qwen3.5-plus", new ModelPrice(
                "dashscope", "qwen3.5-plus",
                cnyPerMillion(3.5),
                cnyPerMillion(7.0)
        ));
        PRICING.put("qwen-turbo", new ModelPrice(
                "dashscope", "qwen-turbo",
                cnyPerMillion(0.3),
                cnyPerMillion(0.6)
        ));
        PRICING.put("qwen-max", new ModelPrice(
                "dashscope", "qwen-max",
                cnyPerMillion(1.6),
                cnyPerMillion(4.8)
        ));

        PRICING.put("deepseek-v3", new ModelPrice(
                "openai", "deepseek-v3",
                usdPerMillion(0.14),
                usdPerMillion(0.28)
        ));

        PRICING.put("claude-3-5-sonnet", new ModelPrice(
                "anthropic", "claude-3-5-sonnet",
                usdPerMillion(3.00),
                usdPerMillion(15.00)
        ));
    }

    private static BigDecimal usdPerMillion(double dollars) {
        return BigDecimal.valueOf(dollars).divide(BigDecimal.valueOf(1_000_000), 10, BigDecimal.ROUND_HALF_UP);
    }

    private static BigDecimal cnyPerMillion(double yuan) {
        return BigDecimal.valueOf(yuan).divide(BigDecimal.valueOf(1_000_000), 10, BigDecimal.ROUND_HALF_UP);
    }

    public static Optional<ModelPrice> getPrice(String model) {
        if (model == null) {
            return Optional.empty();
        }

        String normalizedModel = model.toLowerCase().trim();

        ModelPrice exactMatch = PRICING.get(normalizedModel);
        if (exactMatch != null) {
            return Optional.of(exactMatch);
        }

        for (Map.Entry<String, ModelPrice> entry : PRICING.entrySet()) {
            if (normalizedModel.contains(entry.getKey())) {
                return Optional.of(entry.getValue());
            }
        }

        return Optional.empty();
    }

    public static Cost calculateCost(String model, int promptTokens, int completionTokens) {
        return getPrice(model)
                .map(price -> price.calculate(promptTokens, completionTokens))
                .orElseGet(() -> Cost.unknown(model, promptTokens, completionTokens));
    }

    public record ModelPrice(
            String provider,
            String model,
            BigDecimal pricePerPromptToken,
            BigDecimal pricePerCompletionToken
    ) {
        public Cost calculate(int promptTokens, int completionTokens) {
            BigDecimal promptCost = pricePerPromptToken.multiply(BigDecimal.valueOf(promptTokens));
            BigDecimal completionCost = pricePerCompletionToken.multiply(BigDecimal.valueOf(completionTokens));
            return new Cost(model, provider, promptTokens, completionTokens, promptCost, completionCost);
        }
    }

    public record Cost(
            String model,
            String provider,
            int promptTokens,
            int completionTokens,
            BigDecimal promptCost,
            BigDecimal completionCost
    ) {
        public static Cost unknown(String model, int promptTokens, int completionTokens) {
            return new Cost(model, "unknown", promptTokens, completionTokens, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        public BigDecimal totalCost() {
            return promptCost.add(completionCost);
        }

        public int totalTokens() {
            return promptTokens + completionTokens;
        }

        public boolean isUnknown() {
            return "unknown".equals(provider);
        }

        public String getCurrency() {
            return "dashscope".equals(provider) ? "¥" : "$";
        }

        public String format() {
            if (isUnknown()) {
                return String.format("未知模型 %s (%d tokens)", model, totalTokens());
            }
            return String.format("%s %.6f", getCurrency(), totalCost());
        }

        public String formatDetail() {
            if (isUnknown()) {
                return String.format("%s: %d tokens (无定价)", model, totalTokens());
            }
            return String.format("%s: In %d, Out %d = %s %.6f",
                    model, promptTokens, completionTokens, getCurrency(), totalCost());
        }
    }
}
