package com.factory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Factory 核心同步模型测试")
class FactoryTest {

    @Test
    @DisplayName("put 后 get 应返回相同的 pID")
    void testPutThenGetReturnsSameValue() throws InterruptedException {
        Factory factory = new Factory();

        AtomicInteger result = new AtomicInteger(-1);
        CountDownLatch getDone = new CountDownLatch(1);

        Thread consumer = new Thread(() -> {
            result.set(factory.get());
            getDone.countDown();
        }, "consumer");
        consumer.start();

        Thread.sleep(50);
        factory.put(42);

        assertTrue(getDone.await(2, TimeUnit.SECONDS));
        assertEquals(42, result.get());
        consumer.join(2000);
    }

    @Test
    @DisplayName("初始状态：get 应阻塞直到 put 被调用")
    void testGetBlocksWhenBoxEmpty() throws InterruptedException {
        Factory factory = new Factory();

        AtomicBoolean getReturned = new AtomicBoolean(false);

        Thread consumer = new Thread(() -> {
            factory.get();
            getReturned.set(true);
        }, "consumer");
        consumer.start();

        Thread.sleep(300);
        assertFalse(getReturned.get(), "get() 在没有 put() 时应该阻塞");

        factory.put(1);
        consumer.join(2000);
        assertTrue(getReturned.get(), "put() 后 get() 应该返回");
    }

    @Test
    @DisplayName("put 后再次 put 应阻塞直到 get 取走")
    void testPutBlocksWhenBoxFull() throws InterruptedException {
        Factory factory = new Factory();

        AtomicBoolean secondPutCompleted = new AtomicBoolean(false);
        CountDownLatch firstPutDone = new CountDownLatch(1);

        Thread producer1 = new Thread(() -> {
            factory.put(1);
            firstPutDone.countDown();
        }, "producer1");
        producer1.start();

        assertTrue(firstPutDone.await(2, TimeUnit.SECONDS));

        Thread producer2 = new Thread(() -> {
            factory.put(2);
            secondPutCompleted.set(true);
        }, "producer2");
        producer2.start();

        Thread.sleep(300);
        assertFalse(secondPutCompleted.get(), "第二次 put() 在 get() 之前应该阻塞");

        factory.get();
        producer2.join(2000);
        assertTrue(secondPutCompleted.get(), "get() 后第二次 put() 应该完成");
        producer1.join(2000);
    }

    @Test
    @DisplayName("多轮 put/get 交替执行应保持数据正确")
    void testMultiplePutGetCycles() throws InterruptedException {
        Factory factory = new Factory();
        int cycles = 10;

        for (int i = 1; i <= cycles; i++) {
            AtomicInteger result = new AtomicInteger(-1);
            CountDownLatch getDone = new CountDownLatch(1);

            Thread consumer = new Thread(() -> {
                result.set(factory.get());
                getDone.countDown();
            }, "consumer-" + i);
            consumer.start();

            factory.put(i);

            assertTrue(getDone.await(2, TimeUnit.SECONDS),
                    "第 " + i + " 轮 get() 应该在 put() 后返回");
            assertEquals(i, result.get(),
                    "第 " + i + " 轮返回值应等于 " + i);
            consumer.join(1000);
        }
    }

    @Test
    @DisplayName("get 在 wait 期间被中断应返回 -1")
    void testGetInterruptedWhileWaiting() throws InterruptedException {
        Factory factory = new Factory();

        AtomicReference<Integer> result = new AtomicReference<>(0);
        CountDownLatch started = new CountDownLatch(1);

        Thread consumer = new Thread(() -> {
            started.countDown();
            result.set(factory.get());
        }, "consumer");
        consumer.start();

        assertTrue(started.await(1, TimeUnit.SECONDS));
        Thread.sleep(100);

        consumer.interrupt();
        consumer.join(2000);

        assertEquals(-1, result.get(), "被中断时 get() 应返回 -1");
    }

    @Test
    @DisplayName("put 在 wait 期间被中断应安全退出")
    void testPutInterruptedWhileWaiting() throws InterruptedException {
        Factory factory = new Factory();

        factory.put(1);

        CountDownLatch started = new CountDownLatch(1);

        Thread producer = new Thread(() -> {
            started.countDown();
            factory.put(2);
        }, "producer");
        producer.start();

        assertTrue(started.await(1, TimeUnit.SECONDS));

        while (producer.getState() != Thread.State.WAITING) {
            Thread.yield();
        }

        producer.interrupt();
        producer.join(2000);

        assertTrue(producer.isInterrupted(),
                "被中断后线程中断标志应被恢复");
    }

    @Test
    @DisplayName("put 先于 get：先放入后取出应正常工作")
    void testPutBeforeGet() throws InterruptedException {
        Factory factory = new Factory();

        factory.put(99);

        AtomicInteger result = new AtomicInteger(-1);
        CountDownLatch done = new CountDownLatch(1);

        Thread consumer = new Thread(() -> {
            result.set(factory.get());
            done.countDown();
        }, "consumer");
        consumer.start();

        assertTrue(done.await(2, TimeUnit.SECONDS));
        assertEquals(99, result.get());
        consumer.join(1000);
    }

    @Test
    @DisplayName("get 先于 put：消费者先等待，生产者放入后唤醒消费者")
    void testGetBeforePut() throws InterruptedException {
        Factory factory = new Factory();

        AtomicInteger result = new AtomicInteger(-1);
        CountDownLatch consumerWaiting = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);

        Thread consumer = new Thread(() -> {
            consumerWaiting.countDown();
            result.set(factory.get());
            done.countDown();
        }, "consumer");
        consumer.start();

        assertTrue(consumerWaiting.await(1, TimeUnit.SECONDS));
        Thread.sleep(100);

        factory.put(77);

        assertTrue(done.await(2, TimeUnit.SECONDS));
        assertEquals(77, result.get());
        consumer.join(1000);
    }

    @Test
    @DisplayName("并发场景：多个生产者和消费者交替操作不丢失数据")
    void testConcurrentPutGetPreservesData() throws InterruptedException {
        Factory factory = new Factory();
        int totalItems = 20;
        AtomicInteger consumedSum = new AtomicInteger(0);
        CountDownLatch allConsumed = new CountDownLatch(totalItems);

        Thread consumer = new Thread(() -> {
            for (int i = 0; i < totalItems; i++) {
                int val = factory.get();
                if (val != -1) {
                    consumedSum.addAndGet(val);
                }
                allConsumed.countDown();
            }
        }, "consumer");

        Thread producer = new Thread(() -> {
            for (int i = 1; i <= totalItems; i++) {
                factory.put(i);
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "producer");

        consumer.start();
        producer.start();

        assertTrue(allConsumed.await(10, TimeUnit.SECONDS));
        int expectedSum = totalItems * (totalItems + 1) / 2;
        assertEquals(expectedSum, consumedSum.get(),
                "消费的总和应等于 1+2+...+" + totalItems);

        producer.join(2000);
        consumer.join(2000);
    }

    @Test
    @DisplayName("put(0) 应正常工作，get 返回 0")
    void testPutZeroValue() throws InterruptedException {
        Factory factory = new Factory();

        AtomicInteger result = new AtomicInteger(-999);
        CountDownLatch done = new CountDownLatch(1);

        Thread consumer = new Thread(() -> {
            result.set(factory.get());
            done.countDown();
        }, "consumer");
        consumer.start();

        factory.put(0);

        assertTrue(done.await(2, TimeUnit.SECONDS));
        assertEquals(0, result.get());
        consumer.join(1000);
    }

    @Test
    @DisplayName("put 负数也应正常传递")
    void testPutNegativeValue() throws InterruptedException {
        Factory factory = new Factory();

        AtomicInteger result = new AtomicInteger(0);
        CountDownLatch done = new CountDownLatch(1);

        Thread consumer = new Thread(() -> {
            result.set(factory.get());
            done.countDown();
        }, "consumer");
        consumer.start();

        factory.put(-5);

        assertTrue(done.await(2, TimeUnit.SECONDS));
        assertEquals(-5, result.get());
        consumer.join(1000);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("快速连续 put/get 不死锁")
    void testNoDeadlockUnderRapidExchange() throws InterruptedException {
        Factory factory = new Factory();
        int rounds = 50;
        CountDownLatch allDone = new CountDownLatch(2);

        Thread producer = new Thread(() -> {
            for (int i = 1; i <= rounds; i++) {
                factory.put(i);
            }
            allDone.countDown();
        }, "producer");

        Thread consumer = new Thread(() -> {
            for (int i = 1; i <= rounds; i++) {
                factory.get();
            }
            allDone.countDown();
        }, "consumer");

        producer.start();
        consumer.start();

        assertTrue(allDone.await(5, TimeUnit.SECONDS), "快速交替操作不应死锁");
        producer.join(2000);
        consumer.join(2000);
    }

    // ========== 带 ProductionTypeRegistry 的 pID 校验测试 ==========

    @Test
    @DisplayName("put 非法 pID (超出范围) 应抛出 IllegalArgumentException")
    void testPutInvalidIdWithRegistry() {
        ProductionTypeRegistry registry = ProductionTypeRegistry.createDefault(3);
        Factory factory = new Factory(registry);

        assertThrows(IllegalArgumentException.class, () -> factory.put(0));
        assertThrows(IllegalArgumentException.class, () -> factory.put(4));
        assertThrows(IllegalArgumentException.class, () -> factory.put(-1));
        assertThrows(IllegalArgumentException.class, () -> factory.put(999));
    }

    @Test
    @DisplayName("put 已禁用的 pID 应抛出 IllegalArgumentException")
    void testPutDisabledIdWithRegistry() {
        List<ProductionType> types = List.of(
                new ProductionType(1, "A", "", 100L, 1, true),
                new ProductionType(2, "B", "", 200L, 2, false),
                new ProductionType(3, "C", "", 300L, 3, true)
        );
        ProductionTypeRegistry registry = new ProductionTypeRegistry(types);
        Factory factory = new Factory(registry);

        assertDoesNotThrow(() -> factory.put(1));
        factory.get();

        assertThrows(IllegalArgumentException.class, () -> factory.put(2));

        assertDoesNotThrow(() -> factory.put(3));
        factory.get();
    }

    @Test
    @DisplayName("无 registry 时 put 不应校验 pID (向后兼容)")
    void testPutNoRegistryNoValidation() {
        Factory factory = new Factory();
        assertDoesNotThrow(() -> factory.put(0));
        factory.get();
        assertDoesNotThrow(() -> factory.put(-5));
        factory.get();
        assertDoesNotThrow(() -> factory.put(9999));
        factory.get();
    }

    @Test
    @DisplayName("带 registry 的正常 put/get 流程")
    void testPutGetWithRegistry() throws InterruptedException {
        ProductionTypeRegistry registry = ProductionTypeRegistry.createDefault(3);
        Factory factory = new Factory(registry);

        AtomicInteger result = new AtomicInteger(-1);
        CountDownLatch getDone = new CountDownLatch(1);

        Thread consumer = new Thread(() -> {
            result.set(factory.get());
            getDone.countDown();
        }, "consumer");
        consumer.start();

        Thread.sleep(50);
        factory.put(2);

        assertTrue(getDone.await(2, TimeUnit.SECONDS));
        assertEquals(2, result.get());
        consumer.join(2000);
    }

    @Test
    @DisplayName("Factory 构造函数：带 registry 和不带 registry 都应正常")
    void testFactoryConstructors() {
        assertDoesNotThrow(() -> new Factory());
        ProductionTypeRegistry registry = ProductionTypeRegistry.createDefault(3);
        Factory factory = new Factory(registry);
        assertSame(registry, factory.getRegistry());

        Factory noRegistry = new Factory();
        assertNull(noRegistry.getRegistry());
    }

    // ========== 只读状态方法测试 ==========

    @Test
    @DisplayName("isLocked() 初始应为 false，put 后为 true，get 后恢复 false")
    void testIsLockedStateTransitions() throws InterruptedException {
        Factory factory = new Factory();

        assertFalse(factory.isLocked(), "初始状态应为 false");

        CountDownLatch getDone = new CountDownLatch(1);
        AtomicInteger result = new AtomicInteger(-1);

        Thread consumer = new Thread(() -> {
            result.set(factory.get());
            getDone.countDown();
        }, "consumer");
        consumer.start();

        Thread.sleep(50);
        factory.put(1);

        assertTrue(getDone.await(2, TimeUnit.SECONDS));
        assertFalse(factory.isLocked(), "get() 后应恢复 false");

        consumer.join(2000);
    }

    @Test
    @DisplayName("isLocked() 在 put 后 get 前应为 true")
    void testIsLockedTrueAfterPut() throws InterruptedException {
        Factory factory = new Factory();

        factory.put(42);

        assertTrue(factory.isLocked(), "put() 后应为 true");

        factory.get();

        assertFalse(factory.isLocked(), "get() 后应为 false");
    }

    @Test
    @DisplayName("getCurrentPID() 初始为 0，put 后返回对应 pID")
    void testGetCurrentPID() throws InterruptedException {
        Factory factory = new Factory();

        assertEquals(0, factory.getCurrentPID(), "初始 pID 应为 0");

        factory.put(7);

        assertEquals(7, factory.getCurrentPID(), "put(7) 后应返回 7");

        CountDownLatch getDone = new CountDownLatch(1);
        Thread consumer = new Thread(() -> {
            factory.get();
            getDone.countDown();
        }, "consumer");
        consumer.start();

        assertTrue(getDone.await(2, TimeUnit.SECONDS));
        consumer.join(1000);
    }

    @Test
    @DisplayName("getTotalPutCount() 和 getTotalGetCount() 应正确累计")
    void testTotalPutGetCounts() throws InterruptedException {
        Factory factory = new Factory();
        int cycles = 5;

        assertEquals(0, factory.getTotalPutCount());
        assertEquals(0, factory.getTotalGetCount());

        for (int i = 1; i <= cycles; i++) {
            CountDownLatch getDone = new CountDownLatch(1);

            Thread consumer = new Thread(() -> {
                factory.get();
                getDone.countDown();
            }, "consumer-" + i);
            consumer.start();

            Thread.sleep(30);
            factory.put(i);

            assertTrue(getDone.await(2, TimeUnit.SECONDS));
            consumer.join(1000);
        }

        assertEquals(cycles, factory.getTotalPutCount());
        assertEquals(cycles, factory.getTotalGetCount());
    }

    @Test
    @DisplayName("getManagerWaitCount() 应记录经理被阻塞的次数")
    void testManagerWaitCount() throws InterruptedException {
        Factory factory = new Factory();

        assertEquals(0, factory.getManagerWaitCount(), "初始应为 0");

        factory.put(1);

        assertEquals(0, factory.getManagerWaitCount(), "无竞争时不应等待");

        CountDownLatch secondPutStarted = new CountDownLatch(1);
        AtomicBoolean secondPutDone = new AtomicBoolean(false);

        Thread producer2 = new Thread(() -> {
            secondPutStarted.countDown();
            factory.put(2);
            secondPutDone.set(true);
        }, "producer2");
        producer2.start();

        assertTrue(secondPutStarted.await(1, TimeUnit.SECONDS));
        Thread.sleep(200);

        assertEquals(1, factory.getManagerWaitCount(), "第二次 put 应阻塞一次");

        factory.get();
        producer2.join(2000);
        assertTrue(secondPutDone.get());

        assertEquals(1, factory.getManagerWaitCount(), "put 完成后等待次数不变");
    }

    @Test
    @DisplayName("getTeamLeaderWaitCount() 应记录组长被阻塞的次数")
    void testTeamLeaderWaitCount() throws InterruptedException {
        Factory factory = new Factory();

        assertEquals(0, factory.getTeamLeaderWaitCount(), "初始应为 0");

        CountDownLatch getDone = new CountDownLatch(1);
        AtomicBoolean getReturned = new AtomicBoolean(false);

        Thread consumer = new Thread(() -> {
            getReturned.set(true);
            factory.get();
            getDone.countDown();
        }, "consumer");
        consumer.start();

        Thread.sleep(200);

        assertEquals(1, factory.getTeamLeaderWaitCount(), "空盒时 get 应阻塞一次");

        factory.put(1);

        assertTrue(getDone.await(2, TimeUnit.SECONDS));
        consumer.join(2000);

        assertEquals(1, factory.getTeamLeaderWaitCount(), "get 完成后等待次数不变");
    }

    @Test
    @DisplayName("多轮交替下 waitCount 应持续累计")
    void testWaitCountAccumulates() throws InterruptedException {
        Factory factory = new Factory();

        for (int i = 1; i <= 3; i++) {
            CountDownLatch getDone = new CountDownLatch(1);
            Thread consumer = new Thread(() -> {
                factory.get();
                getDone.countDown();
            }, "consumer-" + i);
            consumer.start();
            Thread.sleep(50);
            factory.put(i);
            assertTrue(getDone.await(2, TimeUnit.SECONDS));
            consumer.join(1000);
        }

        assertTrue(factory.getTeamLeaderWaitCount() >= 3,
                "组长等待次数应 >= 3，实际: " + factory.getTeamLeaderWaitCount());
        assertEquals(0, factory.getManagerWaitCount(),
                "经理在无竞争时等待次数应为 0");
    }
}
