package com.factory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProductionType 实体测试")
class ProductionTypeTest {

    @Test
    @DisplayName("构造函数：合法参数应正常创建")
    void testConstructorValidParams() {
        ProductionType pt = new ProductionType(
                1, "Type-A", "描述", 1500L, 5, true);
        assertEquals(1, pt.getId());
        assertEquals("Type-A", pt.getName());
        assertEquals("描述", pt.getDescription());
        assertEquals(1500L, pt.getManufacturingDurationMs());
        assertEquals(5, pt.getPriority());
        assertTrue(pt.isEnabled());
    }

    @Test
    @DisplayName("构造函数：id < 1 应抛出 IllegalArgumentException")
    void testConstructorInvalidId() {
        assertThrows(IllegalArgumentException.class, () ->
                new ProductionType(0, "Type-A", "", 1000L, 1, true));
        assertThrows(IllegalArgumentException.class, () ->
                new ProductionType(-1, "Type-A", "", 1000L, 1, true));
    }

    @Test
    @DisplayName("构造函数：name 为 null 应抛出 IllegalArgumentException")
    void testConstructorNullName() {
        assertThrows(IllegalArgumentException.class, () ->
                new ProductionType(1, null, "", 1000L, 1, true));
    }

    @Test
    @DisplayName("构造函数：name 为空白应抛出 IllegalArgumentException")
    void testConstructorBlankName() {
        assertThrows(IllegalArgumentException.class, () ->
                new ProductionType(1, "   ", "", 1000L, 1, true));
        assertThrows(IllegalArgumentException.class, () ->
                new ProductionType(1, "", "", 1000L, 1, true));
    }

    @Test
    @DisplayName("构造函数：manufacturingDurationMs < 0 应抛出 IllegalArgumentException")
    void testConstructorNegativeDuration() {
        assertThrows(IllegalArgumentException.class, () ->
                new ProductionType(1, "Type-A", "", -1L, 1, true));
    }

    @Test
    @DisplayName("构造函数：description 为 null 应默认空字符串")
    void testConstructorNullDescriptionDefaultsToEmpty() {
        ProductionType pt = new ProductionType(1, "Type-A", null, 1000L, 1, true);
        assertEquals("", pt.getDescription());
    }

    @Test
    @DisplayName("equals/hashCode：相同属性应相等")
    void testEqualsAndHashCode() {
        ProductionType pt1 = new ProductionType(1, "A", "d", 100L, 5, true);
        ProductionType pt2 = new ProductionType(1, "A", "d", 100L, 5, true);
        assertEquals(pt1, pt2);
        assertEquals(pt1.hashCode(), pt2.hashCode());
    }

    @Test
    @DisplayName("equals：不同 id 应不相等")
    void testEqualsDifferentId() {
        ProductionType pt1 = new ProductionType(1, "A", "", 100L, 1, true);
        ProductionType pt2 = new ProductionType(2, "A", "", 100L, 1, true);
        assertNotEquals(pt1, pt2);
    }

    @Test
    @DisplayName("toString 应包含关键字段")
    void testToString() {
        ProductionType pt = new ProductionType(1, "Type-A", "desc", 1000L, 3, true);
        String str = pt.toString();
        assertTrue(str.contains("id=1"));
        assertTrue(str.contains("name='Type-A'"));
        assertTrue(str.contains("enabled=true"));
    }
}
