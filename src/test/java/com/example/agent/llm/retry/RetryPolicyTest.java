package com.example.agent.llm.retry;

import com.example.agent.llm.exception.LlmApiException;
import com.example.agent.llm.exception.LlmConnectionException;
import com.example.agent.llm.exception.LlmException;
import com.example.agent.llm.exception.LlmTimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class RetryPolicyTest {

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTests {

        @Test
        @DisplayName("默认构造函数")
        void testDefaultConstructor() {
            RetryPolicy policy = new RetryPolicy();
            
            assertEquals(3, policy.getMaxRetries());
            assertEquals(1000, policy.getDelayMs(0));
            assertEquals(2.0, policy.getBackoffMultiplier());
            assertEquals(10000, policy.getMaxDelayMs());
        }

        @Test
        @DisplayName("自定义参数构造")
        void testCustomConstructor() {
            RetryPolicy policy = new RetryPolicy(5, 500, 1.5, 5000);
            
            assertEquals(5, policy.getMaxRetries());
            assertEquals(500, policy.getDelayMs(0));
            assertEquals(1.5, policy.getBackoffMultiplier());
            assertEquals(5000, policy.getMaxDelayMs());
        }

        @Test
        @DisplayName("负数maxRetries抛出异常")
        void testNegativeMaxRetries() {
            assertThrows(IllegalArgumentException.class, () -> {
                new RetryPolicy(-1, 1000, 2.0, 10000);
            });
        }

        @Test
        @DisplayName("负数initialDelayMs抛出异常")
        void testNegativeInitialDelayMs() {
            assertThrows(IllegalArgumentException.class, () -> {
                new RetryPolicy(3, -1, 2.0, 10000);
            });
        }

        @Test
        @DisplayName("零或负数backoffMultiplier抛出异常")
        void testInvalidBackoffMultiplier() {
            assertThrows(IllegalArgumentException.class, () -> {
                new RetryPolicy(3, 1000, 0, 10000);
            });
            
            assertThrows(IllegalArgumentException.class, () -> {
                new RetryPolicy(3, 1000, -1, 10000);
            });
        }

        @Test
        @DisplayName("负数maxDelayMs抛出异常")
        void testNegativeMaxDelayMs() {
            assertThrows(IllegalArgumentException.class, () -> {
                new RetryPolicy(3, 1000, 2.0, -1);
            });
        }

        @Test
        @DisplayName("initialDelayMs大于maxDelayMs抛出异常")
        void testInitialDelayGreaterThanMax() {
            assertThrows(IllegalArgumentException.class, () -> {
                new RetryPolicy(3, 15000, 2.0, 10000);
            });
        }

        @Test
        @DisplayName("零值参数")
        void testZeroValues() {
            RetryPolicy policy = new RetryPolicy(0, 0, 1.0, 0);
            
            assertEquals(0, policy.getMaxRetries());
            assertEquals(0, policy.getDelayMs(0));
        }
    }

    @Nested
    @DisplayName("getDelayMs测试")
    class GetDelayMsTests {

        @Test
        @DisplayName("延迟指数增长")
        void testExponentialBackoff() {
            RetryPolicy policy = new RetryPolicy(3, 1000, 2.0, 10000);
            
            assertEquals(1000, policy.getDelayMs(0));
            assertEquals(2000, policy.getDelayMs(1));
            assertEquals(4000, policy.getDelayMs(2));
            assertEquals(8000, policy.getDelayMs(3));
        }

        @Test
        @DisplayName("延迟不超过最大值")
        void testMaxDelayCap() {
            RetryPolicy policy = new RetryPolicy(10, 1000, 2.0, 5000);
            
            assertTrue(policy.getDelayMs(0) <= 5000);
            assertTrue(policy.getDelayMs(5) <= 5000);
            assertTrue(policy.getDelayMs(10) <= 5000);
        }

        @Test
        @DisplayName("负数attempt返回初始延迟")
        void testNegativeAttempt() {
            RetryPolicy policy = new RetryPolicy(3, 1000, 2.0, 10000);
            
            assertEquals(1000, policy.getDelayMs(-1));
            assertEquals(1000, policy.getDelayMs(-100));
        }

        @Test
        @DisplayName("大attempt返回最大延迟")
        void testLargeAttempt() {
            RetryPolicy policy = new RetryPolicy(3, 1000, 2.0, 10000);
            
            assertEquals(10000, policy.getDelayMs(100));
            assertEquals(10000, policy.getDelayMs(Integer.MAX_VALUE));
        }

        @Test
        @DisplayName("防止溢出")
        void testOverflowProtection() {
            RetryPolicy policy = new RetryPolicy(3, 1000, 10.0, 10000);
            
            assertTrue(policy.getDelayMs(50) <= 10000);
            assertTrue(policy.getDelayMs(100) <= 10000);
        }
    }

    @Nested
    @DisplayName("shouldRetry测试")
    class ShouldRetryTests {

        @Test
        @DisplayName("超过最大重试次数返回false")
        void testExceedMaxRetries() {
            RetryPolicy policy = new RetryPolicy(3, 1000, 2.0, 10000);
            LlmException exception = new LlmTimeoutException("超时", 30);
            
            assertFalse(policy.shouldRetry(exception, 3));
            assertFalse(policy.shouldRetry(exception, 4));
            assertFalse(policy.shouldRetry(exception, 100));
        }

        @Test
        @DisplayName("LlmTimeoutException可重试")
        void testTimeoutExceptionRetryable() {
            RetryPolicy policy = new RetryPolicy(3, 1000, 2.0, 10000);
            LlmTimeoutException exception = new LlmTimeoutException("超时", 30);
            
            assertTrue(policy.shouldRetry(exception, 0));
            assertTrue(policy.shouldRetry(exception, 1));
            assertTrue(policy.shouldRetry(exception, 2));
        }

        @Test
        @DisplayName("LlmConnectionException可重试")
        void testConnectionExceptionRetryable() {
            RetryPolicy policy = new RetryPolicy(3, 1000, 2.0, 10000);
            LlmConnectionException exception = new LlmConnectionException("连接失败", "http://test.com");
            
            assertTrue(policy.shouldRetry(exception, 0));
            assertTrue(policy.shouldRetry(exception, 1));
        }

        @Test
        @DisplayName("服务器错误(5xx)可重试")
        void testServerErrorRetryable() {
            RetryPolicy policy = new RetryPolicy(3, 1000, 2.0, 10000);
            
            for (int status = 500; status < 600; status++) {
                LlmApiException exception = new LlmApiException("服务器错误", status);
                assertTrue(policy.shouldRetry(exception, 0), "Status " + status + " should be retryable");
            }
        }

        @Test
        @DisplayName("限流(429)可重试")
        void testRateLimitedRetryable() {
            RetryPolicy policy = new RetryPolicy(3, 1000, 2.0, 10000);
            LlmApiException exception = new LlmApiException("限流", 429);
            
            assertTrue(policy.shouldRetry(exception, 0));
        }

        @Test
        @DisplayName("客户端错误(4xx, 非429)不可重试")
        void testClientErrorNotRetryable() {
            RetryPolicy policy = new RetryPolicy(3, 1000, 2.0, 10000);
            
            int[] clientErrors = {400, 401, 403, 404, 422};
            for (int status : clientErrors) {
                LlmApiException exception = new LlmApiException("客户端错误", status);
                assertFalse(policy.shouldRetry(exception, 0), "Status " + status + " should not be retryable");
            }
        }

        @Test
        @DisplayName("普通LlmException不可重试")
        void testGenericLlmExceptionNotRetryable() {
            RetryPolicy policy = new RetryPolicy(3, 1000, 2.0, 10000);
            LlmException exception = new LlmException("普通错误");
            
            assertFalse(policy.shouldRetry(exception, 0));
        }
    }

    @Nested
    @DisplayName("静态工厂方法测试")
    class StaticFactoryTests {

        @Test
        @DisplayName("noRetry()返回不重试策略")
        void testNoRetry() {
            RetryPolicy policy = RetryPolicy.noRetry();
            
            assertEquals(0, policy.getMaxRetries());
            assertEquals(0, policy.getDelayMs(0));
        }

        @Test
        @DisplayName("defaultPolicy()返回默认策略")
        void testDefaultPolicy() {
            RetryPolicy policy = RetryPolicy.defaultPolicy();
            
            assertEquals(3, policy.getMaxRetries());
            assertEquals(1000, policy.getDelayMs(0));
            assertEquals(2.0, policy.getBackoffMultiplier());
            assertEquals(10000, policy.getMaxDelayMs());
        }
    }

    @Nested
    @DisplayName("Builder测试")
    class BuilderTests {

        @Test
        @DisplayName("Builder构建完整策略")
        void testBuilderFullBuild() {
            RetryPolicy policy = RetryPolicy.builder()
                    .maxRetries(5)
                    .initialDelayMs(500)
                    .backoffMultiplier(1.5)
                    .maxDelayMs(3000)
                    .build();
            
            assertEquals(5, policy.getMaxRetries());
            assertEquals(500, policy.getDelayMs(0));
            assertEquals(1.5, policy.getBackoffMultiplier());
            assertEquals(3000, policy.getMaxDelayMs());
        }

        @Test
        @DisplayName("Builder默认值")
        void testBuilderDefaults() {
            RetryPolicy policy = RetryPolicy.builder().build();
            
            assertEquals(3, policy.getMaxRetries());
            assertEquals(1000, policy.getDelayMs(0));
            assertEquals(2.0, policy.getBackoffMultiplier());
            assertEquals(10000, policy.getMaxDelayMs());
        }

        @Test
        @DisplayName("Builder负数maxRetries抛出异常")
        void testBuilderNegativeMaxRetries() {
            assertThrows(IllegalArgumentException.class, () -> {
                RetryPolicy.builder().maxRetries(-1).build();
            });
        }

        @Test
        @DisplayName("Builder负数initialDelayMs抛出异常")
        void testBuilderNegativeInitialDelayMs() {
            assertThrows(IllegalArgumentException.class, () -> {
                RetryPolicy.builder().initialDelayMs(-1).build();
            });
        }

        @Test
        @DisplayName("Builder无效backoffMultiplier抛出异常")
        void testBuilderInvalidBackoffMultiplier() {
            assertThrows(IllegalArgumentException.class, () -> {
                RetryPolicy.builder().backoffMultiplier(0).build();
            });
        }

        @Test
        @DisplayName("Builder负数maxDelayMs抛出异常")
        void testBuilderNegativeMaxDelayMs() {
            assertThrows(IllegalArgumentException.class, () -> {
                RetryPolicy.builder().maxDelayMs(-1).build();
            });
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    class BoundaryTests {

        @Test
        @DisplayName("零重试次数")
        void testZeroRetries() {
            RetryPolicy policy = new RetryPolicy(0, 1000, 2.0, 10000);
            LlmTimeoutException exception = new LlmTimeoutException("超时", 30);
            
            assertFalse(policy.shouldRetry(exception, 0));
        }

        @Test
        @DisplayName("非常大的重试次数")
        void testLargeMaxRetries() {
            RetryPolicy policy = new RetryPolicy(1000, 1, 1.0, 100);
            
            assertEquals(1000, policy.getMaxRetries());
        }

        @Test
        @DisplayName("极小延迟")
        void testMinimalDelay() {
            RetryPolicy policy = new RetryPolicy(3, 1, 1.0, 1);
            
            assertEquals(1, policy.getDelayMs(0));
            assertEquals(1, policy.getDelayMs(100));
        }

        @Test
        @DisplayName("极小backoffMultiplier")
        void testMinimalBackoffMultiplier() {
            RetryPolicy policy = new RetryPolicy(3, 1000, 0.001, 10000);
            
            assertTrue(policy.getDelayMs(0) > 0);
            assertTrue(policy.getDelayMs(10) < policy.getDelayMs(0));
        }
    }
}
