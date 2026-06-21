package com.factory.process;

import com.factory.model.Factory;
import com.factory.model.ProductionTypeRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TeamLeaderProcess 消费者测试")
class TeamLeaderProcessTest {

    @Test
    @DisplayName("构造函数：factory 为 null 应抛出 IllegalArgumentException")
    void testConstructorNullFactory() {
        assertThrows(IllegalArgumentException.class, () -> new TeamLeaderProcess(null));
    }

    @Test
    @DisplayName("构造函数：factory 非 null 应正常创建")
    void testConstructorValidFactory() {
        Factory factory = new Factory();
        assertDoesNotThrow(() -> new TeamLeaderProcess(factory));
    }

    @Test
    @DisplayName("TeamLeaderProcess 实现 Runnable 接口")
    void testImplementsRunnable() {
        assertTrue(Runnable.class.isAssignableFrom(TeamLeaderProcess.class));
    }

    @Test
    @DisplayName("消费者应从 Factory 中获取生产者放入的值")
    void testConsumerGetsProducedValue() throws InterruptedException {
        Factory factory = new Factory();

        List<Integer> consumedValues = new ArrayList<>();
        CountDownLatch consumed = new CountDownLatch(3);

        TeamLeaderProcess leader = new TeamLeaderProcess(factory) {
            @Override
            public void run() {
                for (int i = 0; i < 3; i++) {
                    int val = factory.get();
                    consumedValues.add(val);
                    consumed.countDown();
                }
            }
        };

        Thread consumer = new Thread(leader, "consumer");
        Thread producer = new Thread(() -> {
            factory.put(10);
            factory.put(20);
            factory.put(30);
        }, "producer");

        consumer.start();
        producer.start();

        assertTrue(consumed.await(5, TimeUnit.SECONDS));
        consumer.join(2000);
        producer.join(2000);

        assertEquals(List.of(10, 20, 30), consumedValues,
                "消费者应按顺序获取生产者放入的值");
    }

    @Test
    @DisplayName("中断线程应使 TeamLeaderProcess 退出循环")
    void testInterruptStopsProcess() throws InterruptedException {
        Factory factory = new Factory();
        TeamLeaderProcess leader = new TeamLeaderProcess(factory);

        Thread consumer = new Thread(leader, "consumer");

        Thread producer = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                factory.put(1);
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "producer");

        consumer.start();
        producer.start();

        Thread.sleep(500);

        consumer.interrupt();
        consumer.join(3000);
        assertFalse(consumer.isAlive(), "消费者线程应在中断后终止");

        producer.interrupt();
        producer.join(3000);
    }

    @Test
    @DisplayName("get() 返回 -1 时消费者应退出循环")
    void testConsumerExitsOnMinusOne() throws InterruptedException {
        Factory factory = new Factory();

        AtomicBoolean loopExited = new AtomicBoolean(false);

        TeamLeaderProcess leader = new TeamLeaderProcess(factory) {
            @Override
            public void run() {
                int pID = factory.get();
                if (pID == -1) {
                    loopExited.set(true);
                    return;
                }
            }
        };

        Thread consumer = new Thread(leader, "consumer");
        consumer.start();

        Thread.sleep(100);
        consumer.interrupt();
        consumer.join(3000);

        assertTrue(loopExited.get(), "get() 返回 -1 时应退出循环");
    }

    @Test
    @DisplayName("消费者应能处理连续多个任务")
    void testConsumerHandlesMultipleTasks() throws InterruptedException {
        Factory factory = new Factory();
        int taskCount = 10;
        CountDownLatch allConsumed = new CountDownLatch(taskCount);

        List<Integer> consumedValues = new ArrayList<>();

        Thread consumer = new Thread(() -> {
            for (int i = 0; i < taskCount; i++) {
                int val = factory.get();
                if (val != -1) {
                    consumedValues.add(val);
                }
                allConsumed.countDown();
            }
        }, "consumer");

        Thread producer = new Thread(() -> {
            for (int i = 1; i <= taskCount; i++) {
                factory.put(i);
            }
        }, "producer");

        consumer.start();
        producer.start();

        assertTrue(allConsumed.await(5, TimeUnit.SECONDS));
        consumer.join(2000);
        producer.join(2000);

        assertEquals(taskCount, consumedValues.size(),
                "消费者应处理所有任务");
        for (int i = 0; i < taskCount; i++) {
            assertEquals(i + 1, consumedValues.get(i),
                    "第 " + (i + 1) + " 个任务应为 pID=" + (i + 1));
        }
    }

    @Test
    @DisplayName("新构造函数：factory 为 null 应抛出 IllegalArgumentException")
    void testNewConstructorNullFactory() {
        ProductionTypeRegistry registry = ProductionTypeRegistry.createDefault(3);
        assertThrows(IllegalArgumentException.class,
                () -> new TeamLeaderProcess(null, registry));
    }

    @Test
    @DisplayName("新构造函数：有效参数应正常创建")
    void testNewConstructorValidArgs() {
        Factory factory = new Factory();
        ProductionTypeRegistry registry = ProductionTypeRegistry.createDefault(3);
        assertDoesNotThrow(() -> new TeamLeaderProcess(factory, registry));
        assertDoesNotThrow(() -> new TeamLeaderProcess(factory, null));
    }
}
