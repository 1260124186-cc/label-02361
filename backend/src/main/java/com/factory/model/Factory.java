package com.factory.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Factory {

    private static final Logger logger = LoggerFactory.getLogger(Factory.class);

    private final ProductionTypeRegistry registry;

    private int pID;

    private boolean lock = false;

    private volatile boolean shuttingDown = false;

    private int totalPutCount;
    private int totalGetCount;
    private int managerWaitCount;
    private int teamLeaderWaitCount;
    private long managerWaitTotalNanos;
    private long teamLeaderWaitTotalNanos;
    private long managerWaitStartNanos;
    private long teamLeaderWaitStartNanos;
    private final long startTimeNanos;

    public Factory() {
        this.registry = null;
        this.startTimeNanos = System.nanoTime();
    }

    public Factory(ProductionTypeRegistry registry) {
        this.registry = registry;
        this.startTimeNanos = System.nanoTime();
    }

    public ProductionTypeRegistry getRegistry() {
        return registry;
    }

    public synchronized boolean isLocked() {
        return lock;
    }

    public synchronized int getCurrentPID() {
        return pID;
    }

    public synchronized int getTotalPutCount() {
        return totalPutCount;
    }

    public synchronized int getTotalGetCount() {
        return totalGetCount;
    }

    public synchronized int getManagerWaitCount() {
        return managerWaitCount;
    }

    public synchronized int getTeamLeaderWaitCount() {
        return teamLeaderWaitCount;
    }

    public synchronized FactoryResult put(int pID) {
        if (shuttingDown) {
            logger.warn("拒绝放入 pID={}: 工厂正在关闭", pID);
            return FactoryResult.INVALID;
        }

        if (registry != null) {
            if (!registry.isValidId(pID)) {
                logger.warn("拒绝放入非法 pID={}: 超出有效范围 1..{}（或未在注册表中定义）",
                        pID, registry.getMaxId());
                return FactoryResult.INVALID;
            }
            if (!registry.isEnabled(pID)) {
                logger.warn("拒绝放入已禁用的 pID={} ({})",
                        pID, registry.getById(pID).getName());
                return FactoryResult.INVALID;
            }
        }

        while (lock) {
            try {
                logger.debug("经理等待中 - 任务盒已上锁（pID={} 尚未被处理）", this.pID);
                managerWaitCount++;
                managerWaitStartNanos = System.nanoTime();
                wait();
                managerWaitTotalNanos += System.nanoTime() - managerWaitStartNanos;
                managerWaitStartNanos = 0;
            } catch (InterruptedException e) {
                managerWaitStartNanos = 0;
                Thread.currentThread().interrupt();
                logger.warn("经理线程在等待时被中断");
                return FactoryResult.INTERRUPTED;
            }

            if (shuttingDown) {
                logger.warn("工厂正在关闭，取消放入 pID={}", pID);
                return FactoryResult.INVALID;
            }
        }

        this.pID = pID;
        lock = true;
        totalPutCount++;

        if (registry != null) {
            ProductionType pt = registry.getById(pID);
            logger.info("【经理】放入生产类型 pID={} ({}) 优先级={} 到任务盒 → lock=true（上锁），调用 notify() 通知组长",
                    pID, pt.getName(), pt.getPriority());
        } else {
            logger.info("【经理】放入生产类型 pID={} 到任务盒 → lock=true（上锁），调用 notify() 通知组长", pID);
        }

        notify();
        return FactoryResult.SUCCESS;
    }

    public synchronized GetResult get() {
        while (!lock) {
            if (shuttingDown) {
                logger.info("工厂正在关闭且任务盒为空，组长退出");
                return GetResult.invalid();
            }
            try {
                logger.debug("组长等待中 - 任务盒为空（尚无任务），调用 wait() 阻塞");
                teamLeaderWaitCount++;
                teamLeaderWaitStartNanos = System.nanoTime();
                wait();
                teamLeaderWaitTotalNanos += System.nanoTime() - teamLeaderWaitStartNanos;
                teamLeaderWaitStartNanos = 0;
            } catch (InterruptedException e) {
                teamLeaderWaitStartNanos = 0;
                Thread.currentThread().interrupt();
                logger.warn("组长线程在等待时被中断");
                return GetResult.interrupted();
            }
        }

        lock = false;
        int result = this.pID;
        totalGetCount++;

        if (registry != null) {
            ProductionType pt = registry.getById(result);
            logger.info("【组长】用钥匙开锁，从任务盒取出生产类型 pID={} ({}) 制造时长={}ms → lock=false（开锁），调用 notify() 通知经理",
                    result, pt.getName(), pt.getManufacturingDurationMs());
        } else {
            logger.info("【组长】用钥匙开锁，从任务盒取出生产类型 pID={} → lock=false（开锁），调用 notify() 通知经理", result);
        }

        notify();
        return GetResult.success(result);
    }

    public synchronized void shutdown() {
        shuttingDown = true;
        notifyAll();
        logger.info("工厂已标记为关闭状态，notifyAll() 已调用");
    }

    public boolean isShuttingDown() {
        return shuttingDown;
    }

    public synchronized FactoryMetrics createMetrics() {
        long now = System.nanoTime();
        long elapsedNanos = now - startTimeNanos;
        double elapsedMs = elapsedNanos / 1_000_000.0;
        double elapsedMinutes = elapsedMs / 60_000.0;

        long currentManagerWaitNanos = managerWaitTotalNanos;
        if (managerWaitStartNanos > 0) {
            currentManagerWaitNanos += now - managerWaitStartNanos;
        }

        long currentTeamLeaderWaitNanos = teamLeaderWaitTotalNanos;
        if (teamLeaderWaitStartNanos > 0) {
            currentTeamLeaderWaitNanos += now - teamLeaderWaitStartNanos;
        }

        long totalWaitNanos = currentManagerWaitNanos + currentTeamLeaderWaitNanos;
        int totalWaits = managerWaitCount + teamLeaderWaitCount;
        double averageWaitDurationMs = totalWaits > 0
                ? (totalWaitNanos / 1_000_000.0) / totalWaits
                : 0.0;

        double throughputTasksPerMinute = elapsedMinutes > 0
                ? totalGetCount / elapsedMinutes
                : 0.0;

        double managerIdleRate = elapsedNanos > 0
                ? (double) currentManagerWaitNanos / elapsedNanos
                : 0.0;

        double teamLeaderIdleRate = elapsedNanos > 0
                ? (double) currentTeamLeaderWaitNanos / elapsedNanos
                : 0.0;

        return new FactoryMetrics(
                averageWaitDurationMs,
                throughputTasksPerMinute,
                managerIdleRate,
                teamLeaderIdleRate
        );
    }
}
