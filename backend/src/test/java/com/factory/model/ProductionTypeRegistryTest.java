package com.factory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProductionTypeRegistry 注册表测试")
class ProductionTypeRegistryTest {

    @Test
    @DisplayName("构造函数：null 列表应抛出 IllegalArgumentException")
    void testConstructorNullList() {
        assertThrows(IllegalArgumentException.class, () -> new ProductionTypeRegistry(null));
    }

    @Test
    @DisplayName("构造函数：空列表应抛出 IllegalArgumentException")
    void testConstructorEmptyList() {
        assertThrows(IllegalArgumentException.class, () -> new ProductionTypeRegistry(List.of()));
    }

    @Test
    @DisplayName("构造函数：重复 id 应抛出 IllegalArgumentException")
    void testConstructorDuplicateId() {
        List<ProductionType> types = List.of(
                new ProductionType(1, "A", "", 100L, 1, true),
                new ProductionType(1, "B", "", 200L, 2, true)
        );
        assertThrows(IllegalArgumentException.class, () -> new ProductionTypeRegistry(types));
    }

    @Test
    @DisplayName("createDefault(n) 应创建 n 个启用的生产类型")
    void testCreateDefault() {
        ProductionTypeRegistry registry = ProductionTypeRegistry.createDefault(5);
        assertEquals(5, registry.size());
        assertEquals(5, registry.getMaxId());
        assertEquals(5, registry.getEnabled().size());
        for (int i = 1; i <= 5; i++) {
            assertTrue(registry.isValidId(i));
            assertTrue(registry.isEnabled(i));
        }
    }

    @Test
    @DisplayName("createDefault(0) 应抛出 IllegalArgumentException")
    void testCreateDefaultZero() {
        assertThrows(IllegalArgumentException.class, () -> ProductionTypeRegistry.createDefault(0));
    }

    @Test
    @DisplayName("findById：存在应返回 Optional，不存在应返回 Optional.empty")
    void testFindById() {
        ProductionTypeRegistry registry = ProductionTypeRegistry.createDefault(3);
        assertTrue(registry.findById(1).isPresent());
        assertEquals(1, registry.findById(1).get().getId());
        assertTrue(registry.findById(99).isEmpty());
    }

    @Test
    @DisplayName("getById：存在应返回类型，不存在应抛出 NoSuchElementException")
    void testGetById() {
        ProductionTypeRegistry registry = ProductionTypeRegistry.createDefault(3);
        assertEquals(2, registry.getById(2).getId());
        assertThrows(NoSuchElementException.class, () -> registry.getById(99));
    }

    @Test
    @DisplayName("isValidId / isEnabled：正确识别启用和禁用状态")
    void testIsValidAndEnabled() {
        List<ProductionType> types = List.of(
                new ProductionType(1, "A", "", 100L, 1, true),
                new ProductionType(2, "B", "", 200L, 2, false),
                new ProductionType(3, "C", "", 300L, 3, true)
        );
        ProductionTypeRegistry registry = new ProductionTypeRegistry(types);

        assertTrue(registry.isValidId(1));
        assertTrue(registry.isValidId(2));
        assertTrue(registry.isValidId(3));
        assertFalse(registry.isValidId(0));
        assertFalse(registry.isValidId(4));

        assertTrue(registry.isEnabled(1));
        assertFalse(registry.isEnabled(2));
        assertTrue(registry.isEnabled(3));
        assertFalse(registry.isEnabled(99));
    }

    @Test
    @DisplayName("getEnabledSortedByPriorityDesc：应按优先级降序返回启用的类型")
    void testGetEnabledSortedByPriorityDesc() {
        List<ProductionType> types = List.of(
                new ProductionType(1, "A", "", 100L, 1, true),
                new ProductionType(2, "B", "", 200L, 5, true),
                new ProductionType(3, "C", "", 300L, 3, false),
                new ProductionType(4, "D", "", 400L, 10, true)
        );
        ProductionTypeRegistry registry = new ProductionTypeRegistry(types);

        List<ProductionType> sorted = registry.getEnabledSortedByPriorityDesc();
        assertEquals(3, sorted.size());
        assertEquals(4, sorted.get(0).getId());
        assertEquals(10, sorted.get(0).getPriority());
        assertEquals(2, sorted.get(1).getId());
        assertEquals(5, sorted.get(1).getPriority());
        assertEquals(1, sorted.get(2).getId());
        assertEquals(1, sorted.get(2).getPriority());
    }

    @Test
    @DisplayName("getAll 应返回所有类型，getEnabled 只返回启用的")
    void testGetAllVsGetEnabled() {
        List<ProductionType> types = List.of(
                new ProductionType(1, "A", "", 100L, 1, true),
                new ProductionType(2, "B", "", 200L, 2, false)
        );
        ProductionTypeRegistry registry = new ProductionTypeRegistry(types);
        assertEquals(2, registry.getAll().size());
        assertEquals(1, registry.getEnabled().size());
    }
}
