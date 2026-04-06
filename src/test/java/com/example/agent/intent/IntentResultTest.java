package com.example.agent.intent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class IntentResultTest {

    @Nested
    @DisplayName("Builder测试")
    class BuilderTests {

        @Test
        @DisplayName("构建完整的IntentResult")
        void testFullBuild() {
            Map<String, Object> entities = new HashMap<>();
            entities.put("file", "Test.java");
            entities.put("line", 10);

            IntentResult result = IntentResult.builder()
                    .type(IntentType.CODE_MODIFICATION)
                    .confidence(0.95)
                    .entities(entities)
                    .reasoning("用户请求修改代码")
                    .clarifiedIntent("修改Test.java第10行")
                    .build();

            assertEquals(IntentType.CODE_MODIFICATION, result.getType());
            assertEquals(0.95, result.getConfidence());
            assertEquals("Test.java", result.getEntityAsString("file"));
            assertEquals(10, result.getEntity("line", Integer.class));
            assertEquals("用户请求修改代码", result.getReasoning());
            assertEquals("修改Test.java第10行", result.getClarifiedIntent());
        }

        @Test
        @DisplayName("构建最小IntentResult")
        void testMinimalBuild() {
            IntentResult result = IntentResult.builder()
                    .type(IntentType.QUESTION)
                    .build();

            assertEquals(IntentType.QUESTION, result.getType());
            assertEquals(0.0, result.getConfidence());
            assertTrue(result.getEntities().isEmpty());
            assertNull(result.getReasoning());
            assertNull(result.getClarifiedIntent());
        }

        @Test
        @DisplayName("使用entity方法添加单个实体")
        void testEntityMethod() {
            IntentResult result = IntentResult.builder()
                    .type(IntentType.CODE_GENERATION)
                    .entity("language", "Java")
                    .entity("framework", "Spring")
                    .build();

            assertEquals("Java", result.getEntityAsString("language"));
            assertEquals("Spring", result.getEntityAsString("framework"));
        }

        @Test
        @DisplayName("置信度自动限制在0-1范围")
        void testConfidenceClamping() {
            IntentResult result1 = IntentResult.builder()
                    .confidence(1.5)
                    .build();
            assertEquals(1.0, result1.getConfidence());

            IntentResult result2 = IntentResult.builder()
                    .confidence(-0.5)
                    .build();
            assertEquals(0.0, result2.getConfidence());

            IntentResult result3 = IntentResult.builder()
                    .confidence(0.75)
                    .build();
            assertEquals(0.75, result3.getConfidence());
        }
    }

    @Nested
    @DisplayName("静态工厂方法测试")
    class StaticFactoryTests {

        @Test
        @DisplayName("unknown()返回UNKNOWN类型")
        void testUnknown() {
            IntentResult result = IntentResult.unknown();

            assertEquals(IntentType.UNKNOWN, result.getType());
            assertEquals(0.0, result.getConfidence());
            assertEquals("无法识别用户意图", result.getReasoning());
        }

        @Test
        @DisplayName("of()创建简单结果")
        void testOf() {
            IntentResult result = IntentResult.of(IntentType.DEBUGGING, 0.8);

            assertEquals(IntentType.DEBUGGING, result.getType());
            assertEquals(0.8, result.getConfidence());
        }
    }

    @Nested
    @DisplayName("实体获取测试")
    class EntityAccessTests {

        @Test
        @DisplayName("getEntity返回正确类型")
        void testGetEntityWithType() {
            IntentResult result = IntentResult.builder()
                    .entity("count", 42)
                    .entity("name", "test")
                    .build();

            assertEquals(42, result.getEntity("count", Integer.class));
            assertEquals("test", result.getEntity("name", String.class));
        }

        @Test
        @DisplayName("getEntity类型不匹配返回null")
        void testGetEntityWrongType() {
            IntentResult result = IntentResult.builder()
                    .entity("count", 42)
                    .build();

            String value = result.getEntity("count", String.class);
            assertNull(value);
        }

        @Test
        @DisplayName("getEntity不存在的键返回null")
        void testGetEntityNonExistent() {
            IntentResult result = IntentResult.builder().build();

            assertNull(result.getEntity("nonexistent", String.class));
        }

        @Test
        @DisplayName("getEntityAsString返回字符串")
        void testGetEntityAsString() {
            IntentResult result = IntentResult.builder()
                    .entity("value", 123)
                    .build();

            assertEquals("123", result.getEntityAsString("value"));
        }

        @Test
        @DisplayName("getEntityAsString不存在的键返回null")
        void testGetEntityAsStringNonExistent() {
            IntentResult result = IntentResult.builder().build();

            assertNull(result.getEntityAsString("nonexistent"));
        }

        @Test
        @DisplayName("getEntities返回不可修改的Map")
        void testGetEntitiesImmutable() {
            IntentResult result = IntentResult.builder()
                    .entity("key", "value")
                    .build();

            assertThrows(UnsupportedOperationException.class, () -> {
                result.getEntities().put("new", "value");
            });
        }
    }

    @Nested
    @DisplayName("置信度判断测试")
    class ConfidenceJudgmentTests {

        @Test
        @DisplayName("isHighConfidence判断")
        void testIsHighConfidence() {
            IntentResult result1 = IntentResult.builder()
                    .confidence(0.85)
                    .build();
            assertTrue(result1.isHighConfidence());

            IntentResult result2 = IntentResult.builder()
                    .confidence(0.79)
                    .build();
            assertFalse(result2.isHighConfidence());

            IntentResult result3 = IntentResult.builder()
                    .confidence(0.80)
                    .build();
            assertTrue(result3.isHighConfidence());
        }

        @Test
        @DisplayName("isLowConfidence判断")
        void testIsLowConfidence() {
            IntentResult result1 = IntentResult.builder()
                    .confidence(0.49)
                    .build();
            assertTrue(result1.isLowConfidence());

            IntentResult result2 = IntentResult.builder()
                    .confidence(0.50)
                    .build();
            assertFalse(result2.isLowConfidence());

            IntentResult result3 = IntentResult.builder()
                    .confidence(0.30)
                    .build();
            assertTrue(result3.isLowConfidence());
        }

        @Test
        @DisplayName("needsClarification判断")
        void testNeedsClarification() {
            IntentResult result1 = IntentResult.builder()
                    .type(IntentType.UNKNOWN)
                    .confidence(0.9)
                    .build();
            assertTrue(result1.needsClarification());

            IntentResult result2 = IntentResult.builder()
                    .type(IntentType.QUESTION)
                    .confidence(0.3)
                    .build();
            assertTrue(result2.needsClarification());

            IntentResult result3 = IntentResult.builder()
                    .type(IntentType.QUESTION)
                    .confidence(0.8)
                    .build();
            assertFalse(result3.needsClarification());
        }
    }

    @Nested
    @DisplayName("toString测试")
    class ToStringTests {

        @Test
        @DisplayName("toString包含关键信息")
        void testToString() {
            IntentResult result = IntentResult.builder()
                    .type(IntentType.CODE_GENERATION)
                    .confidence(0.95)
                    .reasoning("测试原因")
                    .build();

            String str = result.toString();

            assertTrue(str.contains("CODE_GENERATION"));
            assertTrue(str.contains("0.95"));
            assertTrue(str.contains("测试原因"));
        }

        @Test
        @DisplayName("toString格式化置信度")
        void testToStringFormatsConfidence() {
            IntentResult result = IntentResult.builder()
                    .confidence(0.123456)
                    .build();

            String str = result.toString();

            assertTrue(str.contains("0.12"));
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    class BoundaryTests {

        @Test
        @DisplayName("空实体Map")
        void testEmptyEntities() {
            IntentResult result = IntentResult.builder()
                    .entities(new HashMap<>())
                    .build();

            assertTrue(result.getEntities().isEmpty());
        }

        @Test
        @DisplayName("null实体值")
        void testNullEntityValue() {
            IntentResult result = IntentResult.builder()
                    .entity("key", null)
                    .build();

            assertNull(result.getEntityAsString("key"));
        }

        @Test
        @DisplayName("大量实体")
        void testManyEntities() {
            IntentResult.Builder builder = IntentResult.builder();
            for (int i = 0; i < 100; i++) {
                builder.entity("key" + i, "value" + i);
            }
            IntentResult result = builder.build();

            assertEquals(100, result.getEntities().size());
            assertEquals("value50", result.getEntityAsString("key50"));
        }

        @Test
        @DisplayName("特殊字符在实体值中")
        void testSpecialCharactersInEntity() {
            IntentResult result = IntentResult.builder()
                    .entity("special", "值\n\t\r特殊字符!@#$%")
                    .build();

            assertEquals("值\n\t\r特殊字符!@#$%", result.getEntityAsString("special"));
        }
    }
}
