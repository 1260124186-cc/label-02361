package com.factory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CapacityStrategy 枚举测试")
class CapacityStrategyTest {

    @Test
    @DisplayName("fromCode：有效代码应返回对应枚举值")
    void testFromCodeValid() {
        assertEquals(CapacityStrategy.WAIT, CapacityStrategy.fromCode("wait"));
        assertEquals(CapacityStrategy.WAIT, CapacityStrategy.fromCode("WAIT"));
        assertEquals(CapacityStrategy.WAIT, CapacityStrategy.fromCode("  Wait  "));
        assertEquals(CapacityStrategy.ALERT, CapacityStrategy.fromCode("alert"));
        assertEquals(CapacityStrategy.ALERT, CapacityStrategy.fromCode("ALERT"));
        assertEquals(CapacityStrategy.FAIL, CapacityStrategy.fromCode("fail"));
        assertEquals(CapacityStrategy.FAIL, CapacityStrategy.fromCode("FAIL"));
    }

    @Test
    @DisplayName("fromCode：null 或空字符串应返回默认值 WAIT")
    void testFromCodeNullEmpty() {
        assertEquals(CapacityStrategy.WAIT, CapacityStrategy.fromCode(null));
        assertEquals(CapacityStrategy.WAIT, CapacityStrategy.fromCode(""));
        assertEquals(CapacityStrategy.WAIT, CapacityStrategy.fromCode("   "));
    }

    @Test
    @DisplayName("fromCode：无效代码应抛出 IllegalArgumentException")
    void testFromCodeInvalid() {
        assertThrows(IllegalArgumentException.class, () -> CapacityStrategy.fromCode("invalid"));
        assertThrows(IllegalArgumentException.class, () -> CapacityStrategy.fromCode("unknown"));
    }

    @Test
    @DisplayName("getCode：应返回正确的代码字符串")
    void testGetCode() {
        assertEquals("wait", CapacityStrategy.WAIT.getCode());
        assertEquals("alert", CapacityStrategy.ALERT.getCode());
        assertEquals("fail", CapacityStrategy.FAIL.getCode());
    }

    @Test
    @DisplayName("getDescription：应返回正确的描述")
    void testGetDescription() {
        assertEquals("等待可用成员，直到满足需求", CapacityStrategy.WAIT.getDescription());
        assertEquals("记录产能不足告警，继续执行（可能使用部分成员）", CapacityStrategy.ALERT.getDescription());
        assertEquals("产能不足时任务失败", CapacityStrategy.FAIL.getDescription());
    }

    @Test
    @DisplayName("枚举值：应包含三个策略")
    void testEnumValues() {
        CapacityStrategy[] values = CapacityStrategy.values();
        assertEquals(3, values.length);
        assertEquals(CapacityStrategy.WAIT, values[0]);
        assertEquals(CapacityStrategy.ALERT, values[1]);
        assertEquals(CapacityStrategy.FAIL, values[2]);
    }
}
