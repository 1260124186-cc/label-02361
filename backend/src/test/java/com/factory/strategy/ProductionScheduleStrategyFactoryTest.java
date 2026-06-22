package com.factory.strategy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProductionScheduleStrategyFactory 策略工厂测试")
class ProductionScheduleStrategyFactoryTest {

    @Test
    @DisplayName("create: round-robin 策略创建成功")
    void testCreateRoundRobin() {
        ProductionScheduleStrategy strategy = ProductionScheduleStrategyFactory.create("round-robin");
        assertNotNull(strategy);
        assertTrue(strategy instanceof RoundRobinStrategy);
        assertEquals("round-robin", strategy.getName());
    }

    @Test
    @DisplayName("create: random 策略创建成功")
    void testCreateRandom() {
        ProductionScheduleStrategy strategy = ProductionScheduleStrategyFactory.create("random");
        assertNotNull(strategy);
        assertTrue(strategy instanceof RandomStrategy);
        assertEquals("random", strategy.getName());
    }

    @Test
    @DisplayName("create: priority 策略创建成功")
    void testCreatePriority() {
        ProductionScheduleStrategy strategy = ProductionScheduleStrategyFactory.create("priority");
        assertNotNull(strategy);
        assertTrue(strategy instanceof PriorityStrategy);
        assertEquals("priority", strategy.getName());
    }

    @Test
    @DisplayName("create: demand-driven 策略创建成功")
    void testCreateDemandDriven() {
        ProductionScheduleStrategy strategy = ProductionScheduleStrategyFactory.create("demand-driven");
        assertNotNull(strategy);
        assertTrue(strategy instanceof DemandDrivenStrategy);
        assertEquals("demand-driven", strategy.getName());
    }

    @Test
    @DisplayName("create: 策略名称不区分大小写")
    void testCreateCaseInsensitive() {
        assertNotNull(ProductionScheduleStrategyFactory.create("ROUND-ROBIN"));
        assertNotNull(ProductionScheduleStrategyFactory.create("Random"));
        assertNotNull(ProductionScheduleStrategyFactory.create("Priority"));
        assertNotNull(ProductionScheduleStrategyFactory.create("DEMAND-DRIVEN"));
    }

    @Test
    @DisplayName("create: 未知策略抛出 IllegalArgumentException")
    void testCreateUnknownStrategy() {
        assertThrows(IllegalArgumentException.class,
                () -> ProductionScheduleStrategyFactory.create("unknown"));
    }

    @Test
    @DisplayName("create: null 或空白名称抛出 IllegalArgumentException")
    void testCreateNullOrBlank() {
        assertThrows(IllegalArgumentException.class,
                () -> ProductionScheduleStrategyFactory.create(null));
        assertThrows(IllegalArgumentException.class,
                () -> ProductionScheduleStrategyFactory.create(""));
        assertThrows(IllegalArgumentException.class,
                () -> ProductionScheduleStrategyFactory.create("  "));
    }

    @Test
    @DisplayName("create with Random: random 和 priority 策略支持注入 Random")
    void testCreateWithRandom() {
        Random random = new Random(42);
        ProductionScheduleStrategy randomStrategy = ProductionScheduleStrategyFactory.create("random", random);
        assertTrue(randomStrategy instanceof RandomStrategy);

        ProductionScheduleStrategy priorityStrategy = ProductionScheduleStrategyFactory.create("priority", random);
        assertTrue(priorityStrategy instanceof PriorityStrategy);
    }

    @Test
    @DisplayName("isValidStrategy: 验证策略名称有效性")
    void testIsValidStrategy() {
        assertTrue(ProductionScheduleStrategyFactory.isValidStrategy("round-robin"));
        assertTrue(ProductionScheduleStrategyFactory.isValidStrategy("random"));
        assertTrue(ProductionScheduleStrategyFactory.isValidStrategy("priority"));
        assertTrue(ProductionScheduleStrategyFactory.isValidStrategy("demand-driven"));
        assertFalse(ProductionScheduleStrategyFactory.isValidStrategy("unknown"));
        assertFalse(ProductionScheduleStrategyFactory.isValidStrategy(null));
    }

    @Test
    @DisplayName("getAvailableStrategies: 返回所有可用策略")
    void testGetAvailableStrategies() {
        var strategies = ProductionScheduleStrategyFactory.getAvailableStrategies();
        assertNotNull(strategies);
        assertEquals(4, strategies.size());
        assertTrue(strategies.contains("round-robin"));
        assertTrue(strategies.contains("random"));
        assertTrue(strategies.contains("priority"));
        assertTrue(strategies.contains("demand-driven"));
    }
}
