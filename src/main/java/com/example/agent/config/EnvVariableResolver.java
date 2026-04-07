package com.example.agent.config;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EnvVariableResolver {

    private static final Pattern ENV_VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern ENV_VAR_WITH_DEFAULT = Pattern.compile("^([^:]+):-(.*)$");

    public static String resolve(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        StringBuilder result = new StringBuilder();
        Matcher matcher = ENV_VAR_PATTERN.matcher(value);

        while (matcher.find()) {
            String envExpression = matcher.group(1);
            String replacement = resolveEnvExpression(envExpression);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    private static String resolveEnvExpression(String expression) {
        Matcher defaultMatcher = ENV_VAR_WITH_DEFAULT.matcher(expression);
        
        if (defaultMatcher.matches()) {
            String envVarName = defaultMatcher.group(1);
            String defaultValue = defaultMatcher.group(2);
            String envValue = System.getenv(envVarName);
            return envValue != null && !envValue.isEmpty() ? envValue : defaultValue;
        } else {
            String envValue = System.getenv(expression);
            return envValue != null ? envValue : "";
        }
    }

    public static boolean containsEnvVariable(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        return ENV_VAR_PATTERN.matcher(value).find();
    }
}
