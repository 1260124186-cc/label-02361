// -*- coding: utf-8 -*-
package com.factory.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.Queue;

public class BoundedFactory {

    private static final Logger logger = LoggerFactory.getLogger(BoundedFactory.class);

    private final ProductionTypeRegistry registry;

    private final int capacity;

    private final Queue<Integer> taskQueue;

    private int count;

    private int totalPutCount;
    private int totalGetCount;
    private int managerWaitCount;
    private int teamLeaderWaitCount;
    private long managerWaitTotalNanos;
    private long teamLeaderWaitTotalNanos;
    private long managerWaitStartNanos;
    private long teamLeaderWaitStartNanos;
    private final long startTimeNanos;

    public BoundedFactory(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0, got: " + capacity);
        }
        this.capacity = capacity;
        this.registry = null;
        this.taskQueue = new LinkedList<>();
        this.count = 0;
        this.startTimeNanos = System.nanoTime();
    }

    public BoundedFactory(ProductionTypeRegistry registry, int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0, got: " + capacity);
        }
        this.capacity = capacity;
        this.registry = registry;
        this.taskQueue = new LinkedList<>();
        this.count = 0;
        this.startTimeNanos = System.nanoTime();
    }

    public ProductionTypeRegistry getRegistry() {
        return registry;
    }

    public synchronized int getCapacity() {
        return capacity;
    }

    public synchronized int getCount() {
        return count;
    }

    public synchronized boolean isEmpty() {
        return count == 0;
    }

    public synchronized boolean isFull() {
        return count == capacity;
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

    public synchronized void put(int pID) {
        if (registry != null) {
            if (!registry.isValidId(pID)) {
                String msg = String.format(
                        "拒绝放入非法 pID=%d: 超出有效范围 1..%d（或未在注册表中定义）",
                        pID, registry.getMaxId());
                logger.error(msg);
                throw new IllegalArgumentException(msg);
            }
            if (!registry.isEnabled(pID)) {
                String msg = String.format(
                        "拒绝放入已禁用的 pID=%d (%s)",
                        pID, registry.getById(pID).getName());
                logger.error(msg);
                throw new IllegalArgumentException(msg);
            }
        }

        while (count == capacity) {
            try {
                logger.debug("经理等待中 - 任务盒已满（容量={}，当前={}）", capacity, count);
                managerWaitCount++;
                managerWaitStartNanos = System.nanoTime();
                wait();
                managerWaitTotalNanos += System.nanoTime() - managerWaitStartNanos;
                managerWaitStartNanos = 0;
            } catch (InterruptedException e) {
                managerWaitStartNanos = 0;
                Thread.currentThread().interrupt();
                logger.error("经理线程在等待时被中断", e);
                return;
            }
        }

        taskQueue.offer(pID);
        count++;
        totalPutCount++;

        if (registry != null) {
            ProductionType pt = registry.getById(pID);
            logger.info("【经理】放入生产类型 pID={} ({}) 优先级={} 到任务盒 → count={}/{}，调用 notifyAll() 通知组长",
                    pID, pt.getName(), pt.getPriority(), count, capacity);
        } else {
            logger.info("【经理】放入生产类型 pID={} 到任务盒 → count={}/{}，调用 notifyAll() 通知组长",
                    pID, count, capacity);
        }

        notifyAll();
    }

    public synchronized int get() {
        while (count == 0) {
            try {
                logger.debug("组长等待中 - 任务盒为空（count=0），调用 wait() 阻塞");
                teamLeaderWaitCount++;
                teamLeaderWaitStartNanos = System.nanoTime();
                wait();
                teamLeaderWaitTotalNanos += System.nanoTime() - teamLeaderWaitStartNanos;
                teamLeaderWaitStartNanos = 0;
            } catch (InterruptedException e) {
                teamLeaderWaitStartNanos = 0;
                Thread.currentThread().interrupt();
                logger.error("组长线程在等待时被中断", e);
                return -1;
            }
        }

        int result = taskQueue.poll();
        count--;
        totalGetCount++;

        if (registry != null) {
            ProductionType pt = registry.getById(result);
            logger.info("【组长】从任务盒取出生产类型 pID={} ({}) 制造时长={}ms → count={}/{}，调用 notifyAll() 通知经理",
                    result, pt.getName(), pt.getManufacturingDurationMs(), count, capacity);
        } else {
            logger.info("【组长】从任务盒取出生产类型 pID={} → count={}/{}，调用 notifyAll() 通知经理",
                    result, count, capacity);
        }

        notifyAll();
        return result;
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
