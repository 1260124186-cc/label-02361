package com.factory.model;

import com.factory.model.FactoryResult;
import com.factory.model.GetResult;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BoundedFactory 多槽任务盒同步模型测试")
class BoundedFactoryTest {

    // ========== 构造函数与基础状态测试 ==========

    @Test
    @DisplayName("capacity 必须 > 0，否则抛出 IllegalArgumentException")
    void testCapacityMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new BoundedFactory(0));
        assertThrows(IllegalArgumentException.class, () -> new BoundedFactory(-1));
        assertDoesNotThrow(() -> new BoundedFactory(1));
        assertDoesNotThrow(() -> new BoundedFactory(10));
    }

    @Test
    @DisplayName("初始状态：isEmpty=true, isFull=false, count=0")
    void testInitialState() {
        BoundedFactory factory = new BoundedFactory(5);
        assertTrue(factory.isEmpty());
        assertFalse(factory.isFull());
        assertEquals(0, factory.getCount());
        assertEquals(5, factory.getCapacity());
    }

    @Test
    @DisplayName("capacity=1 时单槽行为类似原始 Factory")
    void testCapacityOneBehavesLikeSingleSlot() throws InterruptedException {
        BoundedFactory factory = new BoundedFactory(1);

        AtomicInteger result = new AtomicInteger(-1);
        CountDownLatch getDone = new CountDownLatch(1);

        Thread consumer = new Thread(() -> {
            result.set(factory.get().getValue());
            getDone.countDown();
        }, "consumer");
        consumer.start();

        Thread.sleep(50);
        factory.put(42);

        assertTrue(getDone.await(2, TimeUnit.SECONDS));
        assertEquals(42, result.get());
        assertTrue(factory.isEmpty());
        consumer.join(2000);
    }

    // ========== put/get 核心语义测试 ==========

    @Test
    @DisplayName("put 后 get 应按 FIFO 顺序返回相同的 pID")
    void testPutThenGetFIFOOrder() throws InterruptedException {
        BoundedFactory factory = new BoundedFactory(3);

        factory.put(10);
        factory.put(20);
        factory.put(30);

        assertEquals(3, factory.getCount());
        assertTrue(factory.isFull());

        assertEquals(10, factory.get().getValue());
        assertEquals(20, factory.get().getValue());
        assertEquals(30, factory.get().getValue());

        assertTrue(factory.isEmpty());
        assertEquals(0, factory.getCount());
    }

    @Test
    @DisplayName("初始状态：get 应阻塞直到 put 被调用")
    void testGetBlocksWhenEmpty() throws InterruptedException {
        BoundedFactory factory = new BoundedFactory(3);

        AtomicBoolean getReturned = new AtomicBoolean(false);

        Thread consumer = new Thread(() -> {
            factory.get();
            getReturned.set(true);
        }, "consumer");
        consumer.start();

        Thread.sleep(300);
        assertFalse(getReturned.get(), "空盒时 get() 应该阻塞");

        factory.put(1);
        consumer.join(2000);
        assertTrue(getReturned.get(), "put() 后 get() 应该返回");
    }

    @Test
    @DisplayName("填满后再 put 应阻塞直到 get 取走")
    void testPutBlocksWhenFull() throws InterruptedException {
        BoundedFactory factory = new BoundedFactory(2);

        factory.put(1);
        factory.put(2);
        assertTrue(factory.isFull());

        AtomicBoolean thirdPutCompleted = new AtomicBoolean(false);

        Thread producer = new Thread(() -> {
            factory.put(3);
            thirdPutCompleted.set(true);
        }, "producer");
        producer.start();

        Thread.sleep(300);
        assertFalse(thirdPutCompleted.get(), "满盒时第3次 put() 应该阻塞");

        int taken = factory.get().getValue();
        assertEquals(1, taken, "应该先取走最先放入的 1");
        producer.join(2000);
        assertTrue(thirdPutCompleted.get(), "get() 后被阻塞的 put() 应该完成");

        assertEquals(2, factory.getCount());
        assertEquals(2, factory.get().getValue());
        assertEquals(3, factory.get().getValue());
        assertTrue(factory.isEmpty());
    }

    @Test
    @DisplayName("多轮填充-清空循环应保持 FIFO 与计数正确")
    void testMultipleFillDrainCycles() throws InterruptedException {
        int capacity = 3;
        BoundedFactory factory = new BoundedFactory(capacity);
        int cycles = 5;

        for (int c = 1; c <= cycles; c++) {
            for (int i = 1; i <= capacity; i++) {
                factory.put(c * 10 + i);
            }
            assertTrue(factory.isFull(), "第 " + c + " 轮填充后应为满");

            for (int i = 1; i <= capacity; i++) {
                int expected = c * 10 + i;
                int actual = factory.get().getValue();
                assertEquals(expected, actual, "第 " + c + " 轮取出顺序错误");
            }
            assertTrue(factory.isEmpty(), "第 " + c + " 轮清空后应为空");
        }
    }

    // ========== 中断处理测试 ==========

    @Test
    @DisplayName("get 在 wait 期间被中断应返回中断状态")
    void testGetInterruptedWhileWaiting() throws InterruptedException {
        BoundedFactory factory = new BoundedFactory(3);

        AtomicReference<GetResult> result = new AtomicReference<>(null);
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

        assertTrue(result.get().isInterrupted(), "被中断时 get() 应返回中断状态");
    }

    @Test
    @DisplayName("shutdown 后 get 在空队列应返回 invalid（统一中断语义）")
    void testGetReturnsInvalidAfterShutdown() throws InterruptedException {
        BoundedFactory factory = new BoundedFactory(3);

        AtomicReference<GetResult> result = new AtomicReference<>(null);
        CountDownLatch started = new CountDownLatch(1);

        Thread consumer = new Thread(() -> {
            started.countDown();
            result.set(factory.get());
        }, "consumer");
        consumer.start();

        assertTrue(started.await(1, TimeUnit.SECONDS));
        Thread.sleep(100);

        factory.shutdown();

        consumer.join(2000);

        assertTrue(result.get().isInvalid(), "关闭时空队列 get() 应返回 invalid");
        assertFalse(result.get().isInterrupted(), "关闭不是中断，不应返回 interrupted");
    }

    @Test
    @DisplayName("shutdown 后 get 在非空队列仍可正常取出")
    void testGetStillWorksWithItemsAfterShutdown() throws InterruptedException {
        BoundedFactory factory = new BoundedFactory(5);
        factory.put(10);
        factory.put(20);

        factory.shutdown();

        assertEquals(10, factory.get().getValue(), "关闭后队列中有数据仍可取出");
        assertEquals(20, factory.get().getValue(), "关闭后队列中有数据仍可取出");
        assertTrue(factory.isEmpty());

        AtomicReference<GetResult> result = new AtomicReference<>(null);
        CountDownLatch started = new CountDownLatch(1);

        Thread consumer = new Thread(() -> {
            started.countDown();
            result.set(factory.get());
        }, "consumer");
        consumer.start();

        assertTrue(started.await(1, TimeUnit.SECONDS));
        Thread.sleep(100);

        consumer.join(2000);

        assertTrue(result.get().isInvalid(), "队列排空后 get() 应返回 invalid");
    }

    @Test
    @DisplayName("put 在 wait（满）期间被中断应安全退出")
    void testPutInterruptedWhileWaiting() throws InterruptedException {
        BoundedFactory factory = new BoundedFactory(2);
        factory.put(1);
        factory.put(2);

        CountDownLatch started = new CountDownLatch(1);

        Thread producer = new Thread(() -> {
            started.countDown();
            factory.put(3);
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
        assertEquals(2, factory.getCount(), "中断不应改变 count");
    }

    // ========== 并发与压力测试 ==========

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("多生产者多消费者：所有数据按 FIFO 消费且不丢失")
    void testMultipleProducersConsumers() throws InterruptedException {
        final int capacity = 4;
        final int producers = 3;
        final int consumers = 3;
        final int itemsPerProducer = 50;
        final int totalItems = producers * itemsPerProducer;

        BoundedFactory factory = new BoundedFactory(capacity);

        AtomicInteger producedSum = new AtomicInteger(0);
        AtomicInteger consumedSum = new AtomicInteger(0);
        CountDownLatch allProduced = new CountDownLatch(producers);
        CountDownLatch allConsumed = new CountDownLatch(totalItems);
        AtomicReference<List<Integer>> consumedOrder = new AtomicReference<>(new ArrayList<>());

        List<Thread> producerThreads = new ArrayList<>();
        for (int p = 0; p < producers; p++) {
            final int base = p * 1000;
            Thread t = new Thread(() -> {
                for (int i = 1; i <= itemsPerProducer; i++) {
                    int val = base + i;
                    producedSum.addAndGet(val);
                    factory.put(val);
                }
                allProduced.countDown();
            }, "producer-" + p);
            producerThreads.add(t);
            t.start();
        }

        List<Thread> consumerThreads = new ArrayList<>();
        for (int c = 0; c < consumers; c++) {
            Thread t = new Thread(() -> {
                while (allConsumed.getCount() > 0) {
                    GetResult gr = factory.get();
                    if (gr.isSuccess()) {
                        int val = gr.getValue();
                        consumedSum.addAndGet(val);
                        synchronized (consumedOrder.get()) {
                            consumedOrder.get().add(val);
                        }
                        allConsumed.countDown();
                    }
                }
            }, "consumer-" + c);
            consumerThreads.add(t);
            t.start();
        }

        assertTrue(allProduced.await(8, TimeUnit.SECONDS));
        assertTrue(allConsumed.await(8, TimeUnit.SECONDS));

        for (Thread t : producerThreads) t.join(2000);
        for (Thread t : consumerThreads) t.interrupt();
        for (Thread t : consumerThreads) t.join(2000);

        assertEquals(producedSum.get(), consumedSum.get(),
                "消费总和应等于生产总和（无丢失无重复）");
        assertEquals(totalItems, consumedOrder.get().size(),
                "消费条目数应等于生产条目数");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("快速连续 put/get 不死锁")
    void testNoDeadlockUnderRapidExchange() throws InterruptedException {
        BoundedFactory factory = new BoundedFactory(3);
        int rounds = 200;
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

    // ========== 边界容量场景 ==========

    @Test
    @DisplayName("大容量：连续填充到 capacity 不阻塞")
    void testLargeCapacityFillsWithoutBlocking() throws InterruptedException {
        int capacity = 100;
        BoundedFactory factory = new BoundedFactory(capacity);

        CountDownLatch done = new CountDownLatch(1);
        AtomicBoolean completed = new AtomicBoolean(false);

        Thread producer = new Thread(() -> {
            for (int i = 1; i <= capacity; i++) {
                factory.put(i);
            }
            completed.set(true);
            done.countDown();
        }, "fast-producer");

        long start = System.nanoTime();
        producer.start();

        assertTrue(done.await(2, TimeUnit.SECONDS),
                "填充 " + capacity + " 个元素不应被阻塞");
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsedMs < 1500, "应该快速完成（未发生阻塞），实际耗时: " + elapsedMs + "ms");

        assertTrue(completed.get());
        assertEquals(capacity, factory.getCount());
        assertTrue(factory.isFull());

        producer.join(2000);
    }

    @Test
    @DisplayName("交替 partial fill / partial drain 计数正确")
    void testPartialFillDrainCounts() {
        BoundedFactory factory = new BoundedFactory(5);

        factory.put(1);
        factory.put(2);
        assertEquals(2, factory.getCount());

        assertEquals(1, factory.get().getValue());
        assertEquals(1, factory.getCount());

        factory.put(3);
        factory.put(4);
        factory.put(5);
        assertEquals(4, factory.getCount());

        assertEquals(2, factory.get().getValue());
        assertEquals(3, factory.get().getValue());
        assertEquals(2, factory.getCount());
        assertFalse(factory.isEmpty());
        assertFalse(factory.isFull());
    }

    // ========== ProductionTypeRegistry 校验测试 ==========

    @Test
    @DisplayName("put 非法 pID (超出范围) 应返回 FactoryResult.INVALID")
    void testPutInvalidIdWithRegistry() {
        ProductionTypeRegistry registry = ProductionTypeRegistry.createDefault(3);
        BoundedFactory factory = new BoundedFactory(registry, 5);

        assertEquals(FactoryResult.INVALID, factory.put(0));
        assertEquals(FactoryResult.INVALID, factory.put(4));
        assertEquals(FactoryResult.INVALID, factory.put(-1));
        assertEquals(0, factory.getCount(), "非法 put 不应改变 count");
    }

    @Test
    @DisplayName("put 已禁用的 pID 应返回 FactoryResult.INVALID")
    void testPutDisabledIdWithRegistry() {
        List<ProductionType> types = List.of(
                new ProductionType(1, "A", "", 100L, 1, true),
                new ProductionType(2, "B", "", 200L, 2, false),
                new ProductionType(3, "C", "", 300L, 3, true)
        );
        ProductionTypeRegistry registry = new ProductionTypeRegistry(types);
        BoundedFactory factory = new BoundedFactory(registry, 3);

        assertEquals(FactoryResult.SUCCESS, factory.put(1));
        assertEquals(FactoryResult.INVALID, factory.put(2));
        assertEquals(FactoryResult.SUCCESS, factory.put(3));

        assertEquals(2, factory.getCount());
    }

    @Test
    @DisplayName("带 registry 的正常 put/get FIFO 流程")
    void testPutGetWithRegistry() throws InterruptedException {
        ProductionTypeRegistry registry = ProductionTypeRegistry.createDefault(4);
        BoundedFactory factory = new BoundedFactory(registry, 3);

        factory.put(2);
        factory.put(4);
        factory.put(1);

        assertEquals(2, factory.get().getValue());
        assertEquals(4, factory.get().getValue());
        assertEquals(1, factory.get().getValue());
        assertTrue(factory.isEmpty());
    }

    @Test
    @DisplayName("构造函数：带 registry 和不带 registry 都应正常")
    void testConstructors() {
        assertDoesNotThrow(() -> new BoundedFactory(3));
        ProductionTypeRegistry registry = ProductionTypeRegistry.createDefault(3);
        BoundedFactory withReg = new BoundedFactory(registry, 5);
        assertSame(registry, withReg.getRegistry());
        assertEquals(5, withReg.getCapacity());

        BoundedFactory noReg = new BoundedFactory(2);
        assertNull(noReg.getRegistry());
        assertEquals(2, noReg.getCapacity());
    }

    // ========== 等待计数与统计测试 ==========

    @Test
    @DisplayName("getTeamLeaderWaitCount() 应记录组长被阻塞的次数")
    void testTeamLeaderWaitCount() throws InterruptedException {
        BoundedFactory factory = new BoundedFactory(2);

        assertEquals(0, factory.getTeamLeaderWaitCount(), "初始应为 0");

        CountDownLatch getStarted = new CountDownLatch(1);
        AtomicBoolean getDone = new AtomicBoolean(false);

        Thread consumer = new Thread(() -> {
            getStarted.countDown();
            factory.get();
            getDone.set(true);
        }, "consumer");
        consumer.start();

        assertTrue(getStarted.await(1, TimeUnit.SECONDS));
        Thread.sleep(200);

        assertEquals(1, factory.getTeamLeaderWaitCount(), "空盒时 get 应阻塞一次");

        factory.put(1);
        consumer.join(2000);
        assertTrue(getDone.get());
        assertEquals(1, factory.getTeamLeaderWaitCount(), "get 完成后等待次数不变");
    }

    @Test
    @DisplayName("getManagerWaitCount() 应记录经理被阻塞的次数")
    void testManagerWaitCount() throws InterruptedException {
        BoundedFactory factory = new BoundedFactory(2);
        factory.put(1);
        factory.put(2);

        assertEquals(0, factory.getManagerWaitCount(), "无竞争时不应等待");

        CountDownLatch producerStarted = new CountDownLatch(1);
        AtomicBoolean producerDone = new AtomicBoolean(false);

        Thread producer = new Thread(() -> {
            producerStarted.countDown();
            factory.put(3);
            producerDone.set(true);
        }, "producer");
        producer.start();

        assertTrue(producerStarted.await(1, TimeUnit.SECONDS));
        Thread.sleep(200);

        assertEquals(1, factory.getManagerWaitCount(), "满盒时 put 应阻塞一次");

        factory.get();
        producer.join(2000);
        assertTrue(producerDone.get());
        assertEquals(1, factory.getManagerWaitCount(), "put 完成后等待次数不变");
    }

    @Test
    @DisplayName("totalPutCount / totalGetCount 应正确累计")
    void testTotalCounts() throws InterruptedException {
        BoundedFactory factory = new BoundedFactory(3);
        int items = 10;

        CountDownLatch allDone = new CountDownLatch(1);

        Thread consumer = new Thread(() -> {
            for (int i = 0; i < items; i++) {
                factory.get();
            }
            allDone.countDown();
        }, "consumer");
        consumer.start();

        for (int i = 1; i <= items; i++) {
            factory.put(i);
        }

        assertTrue(allDone.await(2, TimeUnit.SECONDS));
        assertEquals(items, factory.getTotalPutCount());
        assertEquals(items, factory.getTotalGetCount());
        consumer.join(2000);
    }

    @Test
    @DisplayName("createMetrics() 应返回非空 FactoryMetrics")
    void testCreateMetricsReturnsNonNull() {
        BoundedFactory factory = new BoundedFactory(3);
        factory.put(1);
        factory.get();

        FactoryMetrics metrics = factory.createMetrics();
        assertNotNull(metrics);
    }
}
