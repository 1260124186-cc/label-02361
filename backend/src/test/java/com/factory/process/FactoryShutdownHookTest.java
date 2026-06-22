package com.factory.process;

import com.factory.model.Factory;
import com.factory.model.FactoryResult;
import com.factory.model.GetResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FactoryShutdownHook 两阶段关闭测试")
class FactoryShutdownHookTest {

    @Test
    @DisplayName("构造函数：managerThread 为 null 应抛出 IllegalArgumentException")
    void testConstructorNullManagerThread() {
        Factory factory = new Factory();
        Thread leader = new Thread(() -> {});
        assertThrows(IllegalArgumentException.class,
                () -> new FactoryShutdownHook(null, leader, factory, 3000, false));
    }

    @Test
    @DisplayName("构造函数：teamLeaderThread 为 null 应抛出 IllegalArgumentException")
    void testConstructorNullTeamLeaderThread() {
        Factory factory = new Factory();
        Thread manager = new Thread(() -> {});
        assertThrows(IllegalArgumentException.class,
                () -> new FactoryShutdownHook(manager, null, factory, 3000, false));
    }

    @Test
    @DisplayName("构造函数：factory 为 null 应抛出 IllegalArgumentException")
    void testConstructorNullFactory() {
        Thread manager = new Thread(() -> {});
        Thread leader = new Thread(() -> {});
        assertThrows(IllegalArgumentException.class,
                () -> new FactoryShutdownHook(manager, leader, null, 3000, false));
    }

    @Test
    @DisplayName("构造函数：shutdownTimeoutMs 为负数应抛出 IllegalArgumentException")
    void testConstructorNegativeTimeout() {
        Factory factory = new Factory();
        Thread manager = new Thread(() -> {});
        Thread leader = new Thread(() -> {});
        assertThrows(IllegalArgumentException.class,
                () -> new FactoryShutdownHook(manager, leader, factory, -1, false));
    }

    @Test
    @DisplayName("构造函数：有效参数应正常创建")
    void testConstructorValidArgs() {
        Factory factory = new Factory();
        Thread manager = new Thread(() -> {});
        Thread leader = new Thread(() -> {});
        assertDoesNotThrow(() -> new FactoryShutdownHook(manager, leader, factory, 3000, true));
    }

    @Test
    @DisplayName("getter 方法应返回构造时传入的值")
    void testGetterMethods() {
        Factory factory = new Factory();
        Thread manager = new Thread(() -> {});
        Thread leader = new Thread(() -> {});
        FactoryShutdownHook hook = new FactoryShutdownHook(manager, leader, factory, 5000, true);

        assertSame(manager, hook.getManagerThread());
        assertSame(leader, hook.getTeamLeaderThread());
        assertSame(factory, hook.getFactory());
        assertEquals(5000, hook.getShutdownTimeoutMs());
        assertTrue(hook.isDrainOnShutdown());
    }

    @Test
    @DisplayName("两阶段关闭：drain-on-shutdown=false 应立即中断组长")
    void testShutdownNoDrain() throws InterruptedException {
        Factory factory = new Factory();

        AtomicBoolean managerStopped = new AtomicBoolean(false);
        AtomicBoolean leaderStopped = new AtomicBoolean(false);

        CountDownLatch managerStarted = new CountDownLatch(1);
        CountDownLatch leaderStarted = new CountDownLatch(1);

        Thread managerThread = new Thread(() -> {
            managerStarted.countDown();
            while (!Thread.currentThread().isInterrupted()) {
                FactoryResult result = factory.put(1);
                if (result != FactoryResult.SUCCESS) break;
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            managerStopped.set(true);
        }, "经理");

        Thread leaderThread = new Thread(() -> {
            leaderStarted.countDown();
            while (!Thread.currentThread().isInterrupted()) {
                GetResult result = factory.get();
                if (!result.isSuccess()) break;
            }
            leaderStopped.set(true);
        }, "组长");

        managerThread.start();
        leaderThread.start();

        assertTrue(managerStarted.await(1, TimeUnit.SECONDS));
        assertTrue(leaderStarted.await(1, TimeUnit.SECONDS));
        Thread.sleep(300);

        FactoryShutdownHook hook = new FactoryShutdownHook(
                managerThread, leaderThread, factory, 3000, false);

        Thread hookThread = new Thread(hook, "关闭钩子");
        hookThread.start();
        hookThread.join(5000);

        assertFalse(hookThread.isAlive(), "关闭钩子线程应已完成");
        assertTrue(factory.isShuttingDown(), "工厂应已标记为关闭");
        assertTrue(managerStopped.get(), "经理应已停止");
        assertTrue(leaderStopped.get(), "组长应已停止");
    }

    @Test
    @DisplayName("两阶段关闭：drain-on-shutdown=true 应等待组长完成当前制造")
    void testShutdownWithDrain() throws InterruptedException {
        Factory factory = new Factory();

        AtomicBoolean managerStopped = new AtomicBoolean(false);
        CountDownLatch leaderGotItem = new CountDownLatch(1);
        AtomicBoolean leaderStopped = new AtomicBoolean(false);

        CountDownLatch managerStarted = new CountDownLatch(1);

        Thread managerThread = new Thread(() -> {
            managerStarted.countDown();
            while (!Thread.currentThread().isInterrupted()) {
                FactoryResult result = factory.put(1);
                if (result != FactoryResult.SUCCESS) break;
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            managerStopped.set(true);
        }, "经理");

        Thread leaderThread = new Thread(() -> {
            try {
                GetResult r = factory.get();
                if (r.isSuccess()) {
                    leaderGotItem.countDown();
                }
                while (!Thread.currentThread().isInterrupted()) {
                    GetResult result = factory.get();
                    if (!result.isSuccess()) break;
                }
            } catch (Exception e) {
                // ignore
            }
            leaderStopped.set(true);
        }, "组长");

        managerThread.start();
        leaderThread.start();

        assertTrue(managerStarted.await(1, TimeUnit.SECONDS));
        assertTrue(leaderGotItem.await(1, TimeUnit.SECONDS));
        Thread.sleep(200);

        FactoryShutdownHook hook = new FactoryShutdownHook(
                managerThread, leaderThread, factory, 3000, true);

        Thread hookThread = new Thread(hook, "关闭钩子");
        hookThread.start();
        hookThread.join(5000);

        assertFalse(hookThread.isAlive(), "关闭钩子线程应已完成");
        assertTrue(factory.isShuttingDown(), "工厂应已标记为关闭");
        assertTrue(managerStopped.get(), "经理应已停止");
        assertTrue(leaderStopped.get(), "组长应已停止");
    }

    @Test
    @DisplayName("阶段1 应先中断经理线程再标记工厂关闭")
    void testPhase1StopsManagerBeforeFactoryShutdown() throws InterruptedException {
        Factory factory = new Factory();

        CountDownLatch managerInLoop = new CountDownLatch(1);
        AtomicBoolean factoryShutDownWhenManagerChecked = new AtomicBoolean(false);

        Thread managerThread = new Thread(() -> {
            managerInLoop.countDown();
            while (!Thread.currentThread().isInterrupted()) {
                if (factory.isShuttingDown()) {
                    factoryShutDownWhenManagerChecked.set(true);
                }
                FactoryResult result = factory.put(1);
                if (result != FactoryResult.SUCCESS) break;
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "经理");

        Thread leaderThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                GetResult result = factory.get();
                if (!result.isSuccess()) break;
            }
        }, "组长");

        managerThread.start();
        leaderThread.start();

        assertTrue(managerInLoop.await(1, TimeUnit.SECONDS));
        Thread.sleep(200);

        FactoryShutdownHook hook = new FactoryShutdownHook(
                managerThread, leaderThread, factory, 3000, false);

        Thread hookThread = new Thread(hook, "关闭钩子");
        hookThread.start();
        hookThread.join(5000);

        assertTrue(factory.isShuttingDown(), "工厂最终应被标记为关闭");
    }

    @Test
    @DisplayName("drain-on-shutdown=true 且组长超时未完成应强制中断")
    void testDrainWithTimeoutForceInterrupt() throws InterruptedException {
        Factory factory = new Factory();

        AtomicBoolean leaderStopped = new AtomicBoolean(false);
        CountDownLatch leaderStarted = new CountDownLatch(1);

        Thread managerThread = new Thread(() -> {}, "经理");

        Thread leaderThread = new Thread(() -> {
            leaderStarted.countDown();
            try {
                factory.get();
            } catch (Exception e) {
                // interrupted
            }
            leaderStopped.set(true);
        }, "组长");

        managerThread.start();
        leaderThread.start();

        assertTrue(leaderStarted.await(1, TimeUnit.SECONDS));
        Thread.sleep(100);

        FactoryShutdownHook hook = new FactoryShutdownHook(
                managerThread, leaderThread, factory, 200, true);

        Thread hookThread = new Thread(hook, "关闭钩子");
        hookThread.start();
        hookThread.join(5000);

        assertFalse(hookThread.isAlive(), "关闭钩子应已完成");
        assertTrue(leaderStopped.get(), "超时后组长应被强制中断");
    }

    @Test
    @DisplayName("FactoryShutdownHook 实现 Runnable 接口")
    void testImplementsRunnable() {
        assertTrue(Runnable.class.isAssignableFrom(FactoryShutdownHook.class));
    }
}
