package com.factory.process;

import com.factory.config.ProductionStrategy;
import com.factory.model.Factory;
import com.factory.model.ProductionTypeRegistry;
import com.factory.strategy.ProductionScheduleStrategy;
import com.factory.strategy.RoundRobinStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ManagerProcess 生产者测试")
class ManagerProcessTest {

    @Test
    @DisplayName("构造函数：factory 为 null 应抛出 IllegalArgumentException")
    void testConstructorNullFactory() {
        assertThrows(IllegalArgumentException.class, () -> new ManagerProcess(null, 5));
    }

    @Test
    @DisplayName("构造函数：maxProductionTypes 为 0 应抛出 IllegalArgumentException")
    void testConstructorZeroMaxProductionTypes() {
        Factory factory = new Factory();
        assertThrows(IllegalArgumentException.class, () -> new ManagerProcess(factory, 0));
    }

    @Test
    @DisplayName("构造函数：maxProductionTypes 为负数应抛出 IllegalArgumentException")
    void testConstructorNegativeMaxProductionTypes() {
        Factory factory = new Factory();
        assertThrows(IllegalArgumentException.class, () -> new ManagerProcess(factory, -1));
    }

    @Test
    @DisplayName("构造函数：maxProductionTypes 为 1 应正常创建")
    void testConstructorMaxProductionTypesOne() {
        Factory factory = new Factory();
        assertDoesNotThrow(() -> new ManagerProcess(factory, 1));
    }

    @Test
    @DisplayName("pID 应按 1..n 循环递增")
    void testCyclicProductionTypes() throws InterruptedException {
        Factory factory = new Factory();
        int maxTypes = 3;
        int expectedRounds = 3;
        int totalPuts = maxTypes * expectedRounds;

        List<Integer> producedValues = new ArrayList<>();
        CountDownLatch allProduced = new CountDownLatch(totalPuts);

        ManagerProcess manager = new ManagerProcess(factory, maxTypes) {
            @Override
            public void run() {
                int pID = 1;
                for (int i = 0; i < totalPuts; i++) {
                    factory.put(pID);
                    producedValues.add(pID);
                    pID = (pID % maxTypes) + 1;
                    allProduced.countDown();
                }
            }
        };

        Thread consumer = new Thread(() -> {
            for (int i = 0; i < totalPuts; i++) {
                factory.get();
            }
        }, "consumer");

        Thread producer = new Thread(manager, "producer");

        consumer.start();
        producer.start();

        assertTrue(allProduced.await(5, TimeUnit.SECONDS));
        producer.join(2000);
        consumer.join(2000);

        List<Integer> expected = List.of(1, 2, 3, 1, 2, 3, 1, 2, 3);
        assertEquals(expected, producedValues,
                "pID 应按 1,2,3,1,2,3,1,2,3 循环");
    }

    @Test
    @DisplayName("中断线程应使 ManagerProcess 退出循环")
    void testInterruptStopsProcess() throws InterruptedException {
        Factory factory = new Factory();
        ManagerProcess manager = new ManagerProcess(factory, 5);

        AtomicBoolean consumerDone = new AtomicBoolean(false);
        Thread consumer = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                int val = factory.get();
                if (val == -1) break;
            }
            consumerDone.set(true);
        }, "consumer");

        Thread producer = new Thread(manager, "producer");

        consumer.start();
        producer.start();

        Thread.sleep(500);

        producer.interrupt();
        producer.join(3000);
        assertFalse(producer.isAlive(), "生产者线程应在中断后终止");

        consumer.interrupt();
        consumer.join(3000);
    }

    @Test
    @DisplayName("maxProductionTypes=1 时 pID 始终为 1")
    void testSingleProductionType() throws InterruptedException {
        Factory factory = new Factory();
        int totalPuts = 5;
        List<Integer> producedValues = new ArrayList<>();
        CountDownLatch allProduced = new CountDownLatch(totalPuts);

        ManagerProcess manager = new ManagerProcess(factory, 1) {
            @Override
            public void run() {
                int pID = 1;
                for (int i = 0; i < totalPuts; i++) {
                    factory.put(pID);
                    producedValues.add(pID);
                    pID = (pID % 1) + 1;
                    allProduced.countDown();
                }
            }
        };

        Thread consumer = new Thread(() -> {
            for (int i = 0; i < totalPuts; i++) {
                factory.get();
            }
        }, "consumer");

        Thread producer = new Thread(manager, "producer");

        consumer.start();
        producer.start();

        assertTrue(allProduced.await(5, TimeUnit.SECONDS));
        producer.join(2000);
        consumer.join(2000);

        List<Integer> expected = List.of(1, 1, 1, 1, 1);
        assertEquals(expected, producedValues,
                "maxProductionTypes=1 时 pID 应始终为 1");
    }

    @Test
    @DisplayName("ManagerProcess 实现 Runnable 接口")
    void testImplementsRunnable() {
        assertTrue(Runnable.class.isAssignableFrom(ManagerProcess.class));
    }

    @Test
    @DisplayName("新构造函数：factory/registry/strategy 任一 null 应抛出 IllegalArgumentException")
    void testNewConstructorNullArgs() {
        Factory factory = new Factory();
        ProductionTypeRegistry registry = ProductionTypeRegistry.createDefault(3);
        ProductionStrategy strategy = ProductionStrategy.sequential();

        assertThrows(IllegalArgumentException.class,
                () -> new ManagerProcess(null, registry, strategy));
        assertThrows(IllegalArgumentException.class,
                () -> new ManagerProcess(factory, null, strategy));
        assertThrows(IllegalArgumentException.class,
                () -> new ManagerProcess(factory, registry, (ProductionStrategy) null));
    }

    @Test
    @DisplayName("新构造函数：有效参数应正常创建")
    void testNewConstructorValidArgs() {
        Factory factory = new Factory();
        ProductionTypeRegistry registry = ProductionTypeRegistry.createDefault(3);
        ProductionStrategy strategy = ProductionStrategy.sequential();
        assertDoesNotThrow(() -> new ManagerProcess(factory, registry, strategy));
    }

    @Test
    @DisplayName("新构造函数：priority 策略下 pID 应按优先级循环")
    void testPriorityStrategyCycling() throws InterruptedException {
        ProductionTypeRegistry registry = ProductionTypeRegistry.createDefault(3);
        Factory factory = new Factory(registry);
        int totalPuts = 6;

        List<Integer> producedValues = new ArrayList<>();
        CountDownLatch allProduced = new CountDownLatch(totalPuts);

        ManagerProcess manager = new ManagerProcess(factory, registry, ProductionStrategy.priorityFirst()) {
            @Override
            public void run() {
                var it = ProductionStrategy.priorityFirst().createIterator(registry);
                for (int i = 0; i < totalPuts; i++) {
                    Integer pid = it.next();
                    factory.put(pid);
                    producedValues.add(pid);
                    allProduced.countDown();
                }
            }
        };

        Thread consumer = new Thread(() -> {
            for (int i = 0; i < totalPuts; i++) {
                factory.get();
            }
        }, "consumer");

        Thread producer = new Thread(manager, "producer");

        consumer.start();
        producer.start();

        assertTrue(allProduced.await(5, TimeUnit.SECONDS));
        producer.join(2000);
        consumer.join(2000);

        List<Integer> expected = List.of(1, 2, 3, 1, 2, 3);
        assertEquals(expected, producedValues);
    }

    @Test
    @DisplayName("ProductionScheduleStrategy 构造函数：任一 null 应抛出 IllegalArgumentException")
    void testScheduleStrategyConstructorNullArgs() {
        Factory factory = new Factory();
        ProductionTypeRegistry registry = ProductionTypeRegistry.createDefault(3);
        ProductionScheduleStrategy strategy = new RoundRobinStrategy();

        assertThrows(IllegalArgumentException.class,
                () -> new ManagerProcess(null, registry, strategy));
        assertThrows(IllegalArgumentException.class,
                () -> new ManagerProcess(factory, null, strategy));
        assertThrows(IllegalArgumentException.class,
                () -> new ManagerProcess(factory, registry, (ProductionScheduleStrategy) null));
    }

    @Test
    @DisplayName("ProductionScheduleStrategy 构造函数：有效参数应正常创建")
    void testScheduleStrategyConstructorValidArgs() {
        Factory factory = new Factory();
        ProductionTypeRegistry registry = ProductionTypeRegistry.createDefault(3);
        ProductionScheduleStrategy strategy = new RoundRobinStrategy();
        ManagerProcess manager = assertDoesNotThrow(() ->
                new ManagerProcess(factory, registry, strategy));
        assertEquals(strategy, manager.getScheduleStrategy());
    }

    @Test
    @DisplayName("ProductionScheduleStrategy: RoundRobin 策略下 pID 应循环")
    void testScheduleStrategyRoundRobinCycling() throws InterruptedException {
        ProductionTypeRegistry registry = ProductionTypeRegistry.createDefault(3);
        Factory factory = new Factory(registry);
        int totalPuts = 6;

        List<Integer> producedValues = new ArrayList<>();
        CountDownLatch allProduced = new CountDownLatch(totalPuts);

        ManagerProcess manager = new ManagerProcess(factory, registry, new RoundRobinStrategy()) {
            @Override
            public void run() {
                ProductionScheduleStrategy strategy = getScheduleStrategy();
                for (int i = 0; i < totalPuts; i++) {
                    Integer pid = strategy.nextPID(registry);
                    factory.put(pid);
                    producedValues.add(pid);
                    allProduced.countDown();
                }
            }
        };

        Thread consumer = new Thread(() -> {
            for (int i = 0; i < totalPuts; i++) {
                factory.get();
            }
        }, "consumer");

        Thread producer = new Thread(manager, "producer");

        consumer.start();
        producer.start();

        assertTrue(allProduced.await(5, TimeUnit.SECONDS));
        producer.join(2000);
        consumer.join(2000);

        List<Integer> expected = List.of(1, 2, 3, 1, 2, 3);
        assertEquals(expected, producedValues);
    }
}
