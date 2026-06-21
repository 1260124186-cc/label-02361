package com.factory.config;

import com.factory.model.ProductionType;
import com.factory.model.ProductionTypeRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProductionStrategy 生产策略测试")
class ProductionStrategyTest {

    private ProductionTypeRegistry createTestRegistry() {
        List<ProductionType> types = List.of(
                new ProductionType(1, "Low", "", 100L, 1, true),
                new ProductionType(2, "Mid", "", 100L, 5, true),
                new ProductionType(3, "High", "", 100L, 10, true),
                new ProductionType(4, "Disabled", "", 100L, 7, false)
        );
        return new ProductionTypeRegistry(types);
    }

    @Test
    @DisplayName("sequential 策略：按 pID 升序循环")
    void testSequentialStrategy() {
        ProductionTypeRegistry registry = createTestRegistry();
        Iterator<Integer> it = ProductionStrategy.sequential().createIterator(registry);

        List<Integer> sequence = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            assertTrue(it.hasNext());
            sequence.add(it.next());
        }
        assertEquals(List.of(1, 2, 3, 1, 2, 3), sequence);
        assertEquals("sequential", ProductionStrategy.sequential().getName());
    }

    @Test
    @DisplayName("priorityFirst 策略：按优先级降序循环")
    void testPriorityFirstStrategy() {
        ProductionTypeRegistry registry = createTestRegistry();
        Iterator<Integer> it = ProductionStrategy.priorityFirst().createIterator(registry);

        List<Integer> sequence = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            assertTrue(it.hasNext());
            sequence.add(it.next());
        }
        assertEquals(List.of(3, 2, 1, 3, 2, 1), sequence);
        assertEquals("priority", ProductionStrategy.priorityFirst().getName());
    }

    @Test
    @DisplayName("weekdaySequential 策略：名称正确，迭代器循环")
    void testWeekdaySequentialStrategy() {
        ProductionTypeRegistry registry = createTestRegistry();
        Iterator<Integer> it = ProductionStrategy.weekdaySequential().createIterator(registry);

        Integer first = it.next();
        assertNotNull(first);
        assertEquals("weekday", ProductionStrategy.weekdaySequential().getName());
    }

    @Test
    @DisplayName("priorityFirstWeekday 策略：名称正确")
    void testPriorityFirstWeekdayStrategy() {
        assertEquals("priority-weekday", ProductionStrategy.priorityFirstWeekday().getName());
    }

    @Test
    @DisplayName("所有已禁用类型时 sequential 策略应抛出 NoSuchElementException")
    void testAllDisabledThrows() {
        List<ProductionType> types = List.of(
                new ProductionType(1, "A", "", 100L, 1, false)
        );
        ProductionTypeRegistry registry = new ProductionTypeRegistry(types);
        assertThrows(NoSuchElementException.class, () ->
                ProductionStrategy.sequential().createIterator(registry));
    }

    @Test
    @DisplayName("priorityFirst 策略也应在所有禁用时抛出异常")
    void testPriorityAllDisabledThrows() {
        List<ProductionType> types = List.of(
                new ProductionType(1, "A", "", 100L, 1, false)
        );
        ProductionTypeRegistry registry = new ProductionTypeRegistry(types);
        assertThrows(NoSuchElementException.class, () ->
                ProductionStrategy.priorityFirst().createIterator(registry));
    }
}
