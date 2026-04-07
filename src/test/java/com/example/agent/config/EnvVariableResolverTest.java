package com.example.agent.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnvVariableResolverTest {

    @Test
    void testResolveNullValue() {
        assertNull(EnvVariableResolver.resolve(null));
    }

    @Test
    void testResolveEmptyValue() {
        assertEquals("", EnvVariableResolver.resolve(""));
    }

    @Test
    void testResolvePlainValue() {
        String value = "plain-text-value";
        assertEquals(value, EnvVariableResolver.resolve(value));
    }

    @Test
    void testResolveNonExistentEnvVariable() {
        String value = "${NON_EXISTENT_VAR_12345}";
        String result = EnvVariableResolver.resolve(value);
        assertEquals("", result);
    }

    @Test
    void testResolveEnvVariableWithDefault() {
        String value = "${NON_EXISTENT_VAR_12345:-default-value}";
        String result = EnvVariableResolver.resolve(value);
        assertEquals("default-value", result);
    }

    @Test
    void testResolveEnvVariableWithEmptyDefault() {
        String value = "${NON_EXISTENT_VAR_12345:-}";
        String result = EnvVariableResolver.resolve(value);
        assertEquals("", result);
    }

    @Test
    void testResolveExistingEnvVariable() {
        String pathValue = System.getenv("PATH");
        if (pathValue != null && !pathValue.isEmpty()) {
            String value = "${PATH}";
            String result = EnvVariableResolver.resolve(value);
            assertEquals(pathValue, result);
        }
    }

    @Test
    void testResolveMixedContent() {
        String value = "prefix-${NON_EXISTENT_VAR_12345:-middle}-suffix";
        String result = EnvVariableResolver.resolve(value);
        assertEquals("prefix-middle-suffix", result);
    }

    @Test
    void testResolveMultipleEnvVariables() {
        String value = "${NON_EXISTENT_VAR_1:-first}_${NON_EXISTENT_VAR_2:-second}";
        String result = EnvVariableResolver.resolve(value);
        assertEquals("first_second", result);
    }

    @Test
    void testResolveNestedBracesNotSupported() {
        String value = "${VAR:-${NESTED:-default}}";
        String result = EnvVariableResolver.resolve(value);
        assertTrue(result.endsWith("}"));
    }

    @Test
    void testContainsEnvVariableWithNull() {
        assertFalse(EnvVariableResolver.containsEnvVariable(null));
    }

    @Test
    void testContainsEnvVariableWithEmpty() {
        assertFalse(EnvVariableResolver.containsEnvVariable(""));
    }

    @Test
    void testContainsEnvVariableWithPlainValue() {
        assertFalse(EnvVariableResolver.containsEnvVariable("plain-value"));
    }

    @Test
    void testContainsEnvVariableWithEnvVar() {
        assertTrue(EnvVariableResolver.containsEnvVariable("${SOME_VAR}"));
    }

    @Test
    void testContainsEnvVariableWithMixedContent() {
        assertTrue(EnvVariableResolver.containsEnvVariable("prefix-${VAR}-suffix"));
    }

    @Test
    void testResolveWithSpecialCharacters() {
        String value = "${NON_EXISTENT_VAR_12345:-hello@world#123}";
        String result = EnvVariableResolver.resolve(value);
        assertEquals("hello@world#123", result);
    }

    @Test
    void testResolveWithColonInDefault() {
        String value = "${NON_EXISTENT_VAR_12345:-http://localhost:8080}";
        String result = EnvVariableResolver.resolve(value);
        assertEquals("http://localhost:8080", result);
    }

    @Test
    void testResolveWithDollarSignInDefault() {
        String value = "${NON_EXISTENT_VAR_12345:-price-is-$100}";
        String result = EnvVariableResolver.resolve(value);
        assertEquals("price-is-$100", result);
    }

    @Test
    void testResolveMalformedEnvVarMissingClosingBrace() {
        String value = "${NON_EXISTENT_VAR";
        String result = EnvVariableResolver.resolve(value);
        assertEquals("${NON_EXISTENT_VAR", result);
    }

    @Test
    void testResolveEmptyEnvVarNameNotMatched() {
        String value = "${}";
        String result = EnvVariableResolver.resolve(value);
        assertEquals("${}", result);
    }

    @Test
    void testResolveEnvVarNameWithSpaces() {
        String value = "${ NON_EXISTENT_VAR_12345 }";
        String result = EnvVariableResolver.resolve(value);
        assertEquals("", result);
    }

    @Test
    void testResolveConsecutiveEnvVars() {
        String value = "${NON_EXISTENT_VAR_1:-a}${NON_EXISTENT_VAR_2:-b}${NON_EXISTENT_VAR_3:-c}";
        String result = EnvVariableResolver.resolve(value);
        assertEquals("abc", result);
    }
}
