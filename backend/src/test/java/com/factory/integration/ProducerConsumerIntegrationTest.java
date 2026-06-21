package com.factory.integration;

import com.factory.model.Factory;
import com.factory.process.ManagerProcess;
import com.factory.process.TeamLeaderProcess;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("生产者-消费者集成测试")
class ProducerConsumerIntegrationTest {

    @Test
    @DisplayName("完整生产流程：经理放入的 pID 与组长取出的 pID 完全一致")
    void testFullProductionCycle() throws InterruptedException {
        Factory factory = new Factory();
        int maxTypes = 3;
        int totalItems = 9;

        List<Integer> producedList = Collections.synchronizedList(new ArrayList<>());
        List<Integer> consumedList = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch allConsumed = new CountDownLatch(totalItems);

        ManagerProcess manager = new ManagerProcess(factory, maxTypes) {
            @Override
            public void run() {
                int pID = 1;
                for (int i = 0; i < totalItems; i++) {
                    factory.put(pID);
                    producedList.add(pID);
                    pID = (pID % maxTypes) + 1;
                }
            }
        };

        TeamLeaderProcess leader = new TeamLeaderProcess(factory) {
            @Override
            public void run() {
                for (int i = 0; i < totalItems; i++) {
                    int pID = factory.get();
                    consumedList.add(pID);
                    allConsumed.countDown();
                }
            }
        };

        Thread producer = new Thread(manager, "经理");
        Thread consumer = new Thread(leader, "组长");

        consumer.start();
        producer.start();

        assertTrue(allConsumed.await(5, TimeUnit.SECONDS));
        producer.join(3000);
        consumer.join(3000);

        assertEquals(producedList, consumedList,
                "生产与消费的 pID 列表应完全一致");
    }

    @Test
    @DisplayName("pID 循环：1→2→3→1→2→3→1→2→3 模式验证")
    void testProductionCyclingPattern() throws InterruptedException {
        Factory factory = new Factory();
        int maxTypes = 3;
        int totalItems = 9;

        List<Integer> consumedList = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch allConsumed = new CountDownLatch(totalItems);

        ManagerProcess manager = new ManagerProcess(factory, maxTypes) {
            @Override
            public void run() {
                int pID = 1;
                for (int i = 0; i < totalItems; i++) {
                    factory.put(pID);
                    pID = (pID % maxTypes) + 1;
                }
            }
        };

        TeamLeaderProcess leader = new TeamLeaderProcess(factory) {
            @Override
            public void run() {
                for (int i = 0; i < totalItems; i++) {
                    consumedList.add(factory.get());
                    allConsumed.countDown();
                }
            }
        };

        Thread producer = new Thread(manager, "经理");
        Thread consumer = new Thread(leader, "组长");

        consumer.start();
        producer.start();

        assertTrue(allConsumed.await(5, TimeUnit.SECONDS));
        producer.join(3000);
        consumer.join(3000);

        List<Integer> expected = List.of(1, 2, 3, 1, 2, 3, 1, 2, 3);
        assertEquals(expected, consumedList,
                "pID 应按 1,2,3 循环模式出现");
    }

    @Test
    @DisplayName("优雅关闭：中断后生产者和消费者都应安全退出")
    void testGracefulShutdown() throws InterruptedException {
        Factory factory = new Factory();
        ManagerProcess manager = new ManagerProcess(factory, 5);
        TeamLeaderProcess leader = new TeamLeaderProcess(factory);

        Thread producer = new Thread(manager, "经理");
        Thread consumer = new Thread(leader, "组长");

        producer.start();
        consumer.start();

        Thread.sleep(1000);

        producer.interrupt();
        consumer.interrupt();

        producer.join(3000);
        consumer.join(3000);

        assertFalse(producer.isAlive(), "生产者线程应已终止");
        assertFalse(consumer.isAlive(), "消费者线程应已终止");
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("高压力测试：50轮快速 put/get 不死锁不丢数据")
    void testHighPressureNoDeadlock() throws InterruptedException {
        Factory factory = new Factory();
        int rounds = 50;

        AtomicInteger consumedCount = new AtomicInteger(0);
        CountDownLatch allDone = new CountDownLatch(rounds);

        ManagerProcess manager = new ManagerProcess(factory, 10) {
            @Override
            public void run() {
                int pID = 1;
                for (int i = 0; i < rounds; i++) {
                    factory.put(pID);
                    pID = (pID % 10) + 1;
                }
            }
        };

        TeamLeaderProcess leader = new TeamLeaderProcess(factory) {
            @Override
            public void run() {
                for (int i = 0; i < rounds; i++) {
                    int val = factory.get();
                    if (val != -1) {
                        consumedCount.incrementAndGet();
                    }
                    allDone.countDown();
                }
            }
        };

        Thread producer = new Thread(manager, "经理");
        Thread consumer = new Thread(leader, "组长");

        consumer.start();
        producer.start();

        assertTrue(allDone.await(10, TimeUnit.SECONDS),
                "高压力测试不应死锁");
        producer.join(3000);
        consumer.join(3000);

        assertEquals(rounds, consumedCount.get(),
                "消费数量应等于生产数量");
    }

    @Test
    @DisplayName("生产类型边界：maxProductionTypes=1 时所有 pID 均为 1")
    void testSingleTypeProduction() throws InterruptedException {
        Factory factory = new Factory();
        int totalItems = 5;

        List<Integer> consumedList = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch allConsumed = new CountDownLatch(totalItems);

        ManagerProcess manager = new ManagerProcess(factory, 1) {
            @Override
            public void run() {
                int pID = 1;
                for (int i = 0; i < totalItems; i++) {
                    factory.put(pID);
                    pID = (pID % 1) + 1;
                }
            }
        };

        TeamLeaderProcess leader = new TeamLeaderProcess(factory) {
            @Override
            public void run() {
                for (int i = 0; i < totalItems; i++) {
                    consumedList.add(factory.get());
                    allConsumed.countDown();
                }
            }
        };

        Thread producer = new Thread(manager, "经理");
        Thread consumer = new Thread(leader, "组长");

        consumer.start();
        producer.start();

        assertTrue(allConsumed.await(5, TimeUnit.SECONDS));
        producer.join(3000);
        consumer.join(3000);

        for (int i = 0; i < totalItems; i++) {
            assertEquals(1, consumedList.get(i),
                    "maxProductionTypes=1 时所有 pID 应为 1");
        }
    }

    @Test
    @DisplayName("消费者先启动等待，生产者后启动仍能正确协作")
    void testConsumerStartsFirst() throws InterruptedException {
        Factory factory = new Factory();
        int totalItems = 5;

        List<Integer> consumedList = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch allConsumed = new CountDownLatch(totalItems);

        ManagerProcess manager = new ManagerProcess(factory, 3) {
            @Override
            public void run() {
                int pID = 1;
                for (int i = 0; i < totalItems; i++) {
                    factory.put(pID);
                    pID = (pID % 3) + 1;
                }
            }
        };

        TeamLeaderProcess leader = new TeamLeaderProcess(factory) {
            @Override
            public void run() {
                for (int i = 0; i < totalItems; i++) {
                    consumedList.add(factory.get());
                    allConsumed.countDown();
                }
            }
        };

        Thread consumer = new Thread(leader, "组长");
        Thread producer = new Thread(manager, "经理");

        consumer.start();
        Thread.sleep(200);
        producer.start();

        assertTrue(allConsumed.await(5, TimeUnit.SECONDS));
        producer.join(3000);
        consumer.join(3000);

        assertEquals(totalItems, consumedList.size(),
                "消费者应成功获取所有任务");
    }

    @Test
    @DisplayName("生产者先启动放入，消费者后启动仍能取出")
    void testProducerStartsFirst() throws InterruptedException {
        Factory factory = new Factory();
        int totalItems = 5;

        List<Integer> consumedList = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch allConsumed = new CountDownLatch(totalItems);

        ManagerProcess manager = new ManagerProcess(factory, 2) {
            @Override
            public void run() {
                int pID = 1;
                for (int i = 0; i < totalItems; i++) {
                    factory.put(pID);
                    pID = (pID % 2) + 1;
                }
            }
        };

        TeamLeaderProcess leader = new TeamLeaderProcess(factory) {
            @Override
            public void run() {
                for (int i = 0; i < totalItems; i++) {
                    consumedList.add(factory.get());
                    allConsumed.countDown();
                }
            }
        };

        Thread producer = new Thread(manager, "经理");
        Thread consumer = new Thread(leader, "组长");

        producer.start();
        Thread.sleep(200);
        consumer.start();

        assertTrue(allConsumed.await(5, TimeUnit.SECONDS));
        producer.join(3000);
        consumer.join(3000);

        List<Integer> expected = List.of(1, 2, 1, 2, 1);
        assertEquals(expected, consumedList,
                "生产者先启动，消费者应获取完整正确的数据");
    }
}
