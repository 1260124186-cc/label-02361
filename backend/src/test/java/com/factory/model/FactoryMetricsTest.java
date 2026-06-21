package com.factory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FactoryMetrics 运行时指标测试")
class FactoryMetricsTest {

    @Test
    @DisplayName("初始 Factory 的 createMetrics 应返回零值指标")
    void testMetricsInitiallyZero() {
        Factory factory = new Factory();
        FactoryMetrics metrics = factory.createMetrics();

        assertEquals(0.0, metrics.getAverageWaitDurationMs(), 0.001,
                "初始平均等待时长应为 0");
        assertEquals(0.0, metrics.getThroughputTasksPerMinute(), 0.001,
                "初始吞吐应为 0");
        assertEquals(0.0, metrics.getManagerIdleRate(), 0.001,
                "初始经理空闲率应为 0");
        assertEquals(0.0, metrics.getTeamLeaderIdleRate(), 0.001,
                "初始组长空闲率应为 0");
    }

    @Test
    @DisplayName("单次 put/get 后吞吐和等待指标应大于 0")
    void testMetricsAfterSinglePutGet() throws InterruptedException {
        Factory factory = new Factory();

        CountDownLatch getDone = new CountDownLatch(1);
        Thread consumer = new Thread(() -> {
            factory.get();
            getDone.countDown();
        }, "consumer");
        consumer.start();

        Thread.sleep(100);
        factory.put(1);

        assertTrue(getDone.await(2, TimeUnit.SECONDS));
        consumer.join(2000);

        FactoryMetrics metrics = factory.createMetrics();

        assertTrue(metrics.getThroughputTasksPerMinute() > 0,
                "吞吐应 > 0，实际: " + metrics.getThroughputTasksPerMinute());
        assertTrue(metrics.getTeamLeaderIdleRate() > 0,
                "组长空闲率应 > 0，实际: " + metrics.getTeamLeaderIdleRate());
    }

    @Test
    @DisplayName("经理阻塞时空闲率应大于 0")
    void testManagerIdleRateWhenBlocked() throws InterruptedException {
        Factory factory = new Factory();

        factory.put(1);

        CountDownLatch putStarted = new CountDownLatch(1);
        Thread producer2 = new Thread(() -> {
            putStarted.countDown();
            factory.put(2);
        }, "producer2");
        producer2.start();

        assertTrue(putStarted.await(1, TimeUnit.SECONDS));
        Thread.sleep(300);

        FactoryMetrics metrics = factory.createMetrics();

        assertTrue(metrics.getManagerIdleRate() > 0,
                "经理空闲率应 > 0，实际: " + metrics.getManagerIdleRate());

        factory.get();
        producer2.join(2000);
    }

    @Test
    @DisplayName("多轮 put/get 后吞吐应与 getCount 一致")
    void testThroughputMatchesGetCount() throws InterruptedException {
        Factory factory = new Factory();
        int cycles = 10;

        for (int i = 1; i <= cycles; i++) {
            CountDownLatch getDone = new CountDownLatch(1);
            Thread consumer = new Thread(() -> {
                factory.get();
                getDone.countDown();
            }, "consumer-" + i);
            consumer.start();
            Thread.sleep(20);
            factory.put(i);
            assertTrue(getDone.await(2, TimeUnit.SECONDS));
            consumer.join(1000);
        }

        FactoryMetrics metrics = factory.createMetrics();

        assertTrue(metrics.getThroughputTasksPerMinute() > 0,
                "吞吐应 > 0");

        assertEquals(cycles, factory.getTotalGetCount());
        assertEquals(cycles, factory.getTotalPutCount());
    }

    @Test
    @DisplayName("createMetrics 返回的是不可变快照")
    void testMetricsIsSnapshot() throws InterruptedException {
        Factory factory = new Factory();

        FactoryMetrics snapshot1 = factory.createMetrics();

        CountDownLatch getDone = new CountDownLatch(1);
        Thread consumer = new Thread(() -> {
            factory.get();
            getDone.countDown();
        }, "consumer");
        consumer.start();
        Thread.sleep(100);
        factory.put(1);
        assertTrue(getDone.await(2, TimeUnit.SECONDS));
        consumer.join(2000);

        FactoryMetrics snapshot2 = factory.createMetrics();

        assertEquals(0.0, snapshot1.getThroughputTasksPerMinute(), 0.001,
                "快照1吞吐应为 0");
        assertTrue(snapshot2.getThroughputTasksPerMinute() > 0,
                "快照2吞吐应 > 0");
    }

    @Test
    @DisplayName("FactoryMetrics toString 应包含所有指标")
    void testMetricsToString() {
        FactoryMetrics metrics = new FactoryMetrics(12.5, 60.0, 0.15, 0.25);
        String str = metrics.toString();

        assertTrue(str.contains("averageWaitDurationMs"));
        assertTrue(str.contains("throughputTasksPerMinute"));
        assertTrue(str.contains("managerIdleRate"));
        assertTrue(str.contains("teamLeaderIdleRate"));
    }

    @Test
    @DisplayName("FactoryMetrics equals 和 hashCode 应正确工作")
    void testMetricsEqualsAndHashCode() {
        FactoryMetrics a = new FactoryMetrics(10.0, 50.0, 0.1, 0.2);
        FactoryMetrics b = new FactoryMetrics(10.0, 50.0, 0.1, 0.2);
        FactoryMetrics c = new FactoryMetrics(20.0, 50.0, 0.1, 0.2);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    @DisplayName("空闲率应在 0 到 1 之间")
    void testIdleRateBoundaries() throws InterruptedException {
        Factory factory = new Factory();

        for (int i = 1; i <= 5; i++) {
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

        FactoryMetrics metrics = factory.createMetrics();

        assertTrue(metrics.getManagerIdleRate() >= 0.0 && metrics.getManagerIdleRate() <= 1.0,
                "经理空闲率应在 [0,1]，实际: " + metrics.getManagerIdleRate());
        assertTrue(metrics.getTeamLeaderIdleRate() >= 0.0 && metrics.getTeamLeaderIdleRate() <= 1.0,
                "组长空闲率应在 [0,1]，实际: " + metrics.getTeamLeaderIdleRate());
    }
}
