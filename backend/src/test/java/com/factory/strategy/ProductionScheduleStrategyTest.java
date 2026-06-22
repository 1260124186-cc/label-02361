package com.factory.strategy;

import com.factory.model.ProductionType;
import com.factory.model.ProductionTypeRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProductionScheduleStrategy 排产策略测试")
class ProductionScheduleStrategyTest {

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
    @DisplayName("RoundRobinStrategy: 按 pID 升序循环")
    void testRoundRobinStrategy() {
        ProductionTypeRegistry registry = createTestRegistry();
        RoundRobinStrategy strategy = new RoundRobinStrategy();

        List<Integer> sequence = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            sequence.add(strategy.nextPID(registry));
        }
        assertEquals(List.of(1, 2, 3, 1, 2, 3), sequence);
        assertEquals("round-robin", strategy.getName());
    }

    @Test
    @DisplayName("RoundRobinStrategy: 所有类型禁用时抛出 NoSuchElementException")
    void testRoundRobinAllDisabledThrows() {
        List<ProductionType> types = List.of(
                new ProductionType(1, "A", "", 100L, 1, false)
        );
        ProductionTypeRegistry registry = new ProductionTypeRegistry(types);
        RoundRobinStrategy strategy = new RoundRobinStrategy();
        assertThrows(NoSuchElementException.class, () -> strategy.nextPID(registry));
    }

    @Test
    @DisplayName("RandomStrategy: 随机返回启用的 pID")
    void testRandomStrategy() {
        ProductionTypeRegistry registry = createTestRegistry();
        RandomStrategy strategy = new RandomStrategy(new Random(42));

        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            int pid = strategy.nextPID(registry);
            assertTrue(pid >= 1 && pid <= 3, "pID 应在启用范围内");
            assertNotEquals(4, pid, "不应返回已禁用的 pID=4");
            seen.add(pid);
        }
        assertTrue(seen.size() >= 2, "多次调用应覆盖至少两种类型");
        assertEquals("random", strategy.getName());
    }

    @Test
    @DisplayName("RandomStrategy: 固定种子产生可复现结果")
    void testRandomStrategyFixedSeed() {
        ProductionTypeRegistry registry = createTestRegistry();
        RandomStrategy strategy1 = new RandomStrategy(new Random(123));
        RandomStrategy strategy2 = new RandomStrategy(new Random(123));

        List<Integer> seq1 = new ArrayList<>();
        List<Integer> seq2 = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            seq1.add(strategy1.nextPID(registry));
            seq2.add(strategy2.nextPID(registry));
        }
        assertEquals(seq1, seq2, "相同种子应产生相同序列");
    }

    @Test
    @DisplayName("RandomStrategy: null Random 应抛出 IllegalArgumentException")
    void testRandomStrategyNullRandom() {
        assertThrows(IllegalArgumentException.class, () -> new RandomStrategy(null));
    }

    @Test
    @DisplayName("RandomStrategy: 所有类型禁用时抛出 NoSuchElementException")
    void testRandomAllDisabledThrows() {
        List<ProductionType> types = List.of(
                new ProductionType(1, "A", "", 100L, 1, false)
        );
        ProductionTypeRegistry registry = new ProductionTypeRegistry(types);
        RandomStrategy strategy = new RandomStrategy();
        assertThrows(NoSuchElementException.class, () -> strategy.nextPID(registry));
    }

    @Test
    @DisplayName("PriorityStrategy: 高优先级类型被选中概率更高")
    void testPriorityStrategyWeighted() {
        ProductionTypeRegistry registry = createTestRegistry();
        PriorityStrategy strategy = new PriorityStrategy(new Random(42));

        int count1 = 0, count2 = 0, count3 = 0;
        int trials = 10000;
        for (int i = 0; i < trials; i++) {
            int pid = strategy.nextPID(registry);
            switch (pid) {
                case 1 -> count1++;
                case 2 -> count2++;
                case 3 -> count3++;
                default -> fail("无效 pID: " + pid);
            }
        }

        assertTrue(count3 > count2, "优先级10的类型应比优先级5的选中次数多");
        assertTrue(count2 > count1, "优先级5的类型应比优先级1的选中次数多");
        assertEquals("priority", strategy.getName());
    }

    @Test
    @DisplayName("PriorityStrategy: 零优先级时退化为均匀随机")
    void testPriorityStrategyZeroPriority() {
        List<ProductionType> types = List.of(
                new ProductionType(1, "A", "", 100L, 0, true),
                new ProductionType(2, "B", "", 100L, 0, true),
                new ProductionType(3, "C", "", 100L, 0, true)
        );
        ProductionTypeRegistry registry = new ProductionTypeRegistry(types);
        PriorityStrategy strategy = new PriorityStrategy(new Random(42));

        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            seen.add(strategy.nextPID(registry));
        }
        assertTrue(seen.size() >= 2, "零优先级时应能随机选中不同类型");
    }

    @Test
    @DisplayName("PriorityStrategy: null Random 应抛出 IllegalArgumentException")
    void testPriorityStrategyNullRandom() {
        assertThrows(IllegalArgumentException.class, () -> new PriorityStrategy(null));
    }

    @Test
    @DisplayName("PriorityStrategy: 所有类型禁用时抛出 NoSuchElementException")
    void testPriorityAllDisabledThrows() {
        List<ProductionType> types = List.of(
                new ProductionType(1, "A", "", 100L, 1, false)
        );
        ProductionTypeRegistry registry = new ProductionTypeRegistry(types);
        PriorityStrategy strategy = new PriorityStrategy();
        assertThrows(NoSuchElementException.class, () -> strategy.nextPID(registry));
    }

    @Test
    @DisplayName("DemandDrivenStrategy: 均衡分配 - 各类计数差不超过1")
    void testDemandDrivenStrategyBalanced() {
        ProductionTypeRegistry registry = createTestRegistry();
        DemandDrivenStrategy strategy = new DemandDrivenStrategy();

        int rounds = 9;
        for (int i = 0; i < rounds; i++) {
            strategy.nextPID(registry);
        }

        assertEquals(3, strategy.getCompletionCount(1));
        assertEquals(3, strategy.getCompletionCount(2));
        assertEquals(3, strategy.getCompletionCount(3));
        assertEquals(0, strategy.getCompletionCount(4));
        assertEquals("demand-driven", strategy.getName());
    }

    @Test
    @DisplayName("DemandDrivenStrategy: 初始时各类型计数为0")
    void testDemandDrivenInitialCounts() {
        ProductionTypeRegistry registry = createTestRegistry();
        DemandDrivenStrategy strategy = new DemandDrivenStrategy();

        assertEquals(0, strategy.getCompletionCount(1));
        assertEquals(0, strategy.getCompletionCount(999));
    }

    @Test
    @DisplayName("DemandDrivenStrategy: 平局时按 pID 升序选择")
    void testDemandDrivenTieBreaksByPID() {
        ProductionTypeRegistry registry = createTestRegistry();
        DemandDrivenStrategy strategy = new DemandDrivenStrategy();

        assertEquals(1, strategy.nextPID(registry));
        assertEquals(2, strategy.nextPID(registry));
        assertEquals(3, strategy.nextPID(registry));
        assertEquals(1, strategy.nextPID(registry));
    }

    @Test
    @DisplayName("DemandDrivenStrategy: reset 后计数清零")
    void testDemandDrivenReset() {
        ProductionTypeRegistry registry = createTestRegistry();
        DemandDrivenStrategy strategy = new DemandDrivenStrategy();

        for (int i = 0; i < 5; i++) {
            strategy.nextPID(registry);
        }
        assertTrue(strategy.getCompletionCount(1) > 0);

        strategy.reset();
        assertEquals(0, strategy.getCompletionCount(1));
        assertEquals(0, strategy.getCompletionCount(2));
        assertEquals(0, strategy.getCompletionCount(3));
    }

    @Test
    @DisplayName("DemandDrivenStrategy: 所有类型禁用时抛出 NoSuchElementException")
    void testDemandDrivenAllDisabledThrows() {
        List<ProductionType> types = List.of(
                new ProductionType(1, "A", "", 100L, 1, false)
        );
        ProductionTypeRegistry registry = new ProductionTypeRegistry(types);
        DemandDrivenStrategy strategy = new DemandDrivenStrategy();
        assertThrows(NoSuchElementException.class, () -> strategy.nextPID(registry));
    }

    @Test
    @DisplayName("ProductionScheduleStrategy: 四种策略均实现接口")
    void testAllStrategiesImplementInterface() {
        assertTrue(ProductionScheduleStrategy.class.isAssignableFrom(RoundRobinStrategy.class));
        assertTrue(ProductionScheduleStrategy.class.isAssignableFrom(RandomStrategy.class));
        assertTrue(ProductionScheduleStrategy.class.isAssignableFrom(PriorityStrategy.class));
        assertTrue(ProductionScheduleStrategy.class.isAssignableFrom(DemandDrivenStrategy.class));
    }
}
