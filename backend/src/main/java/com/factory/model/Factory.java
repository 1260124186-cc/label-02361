// -*- coding: utf-8 -*-
package com.factory.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 带锁机制的共享任务盒（生产者-消费者模型）。
 * <p>
 * 详细设计与同步语义请参见 {@code docs/project_design.md} 第 2、4.1 节。
 * </p>
 *
 * @author factory-sync
 * @version 1.0.0
 * @see <a href="../../docs/project_design.md">project_design.md</a>
 */
public class Factory {

    private static final Logger logger = LoggerFactory.getLogger(Factory.class);

    /** Production type ID stored in the task box. */
    private int pID;

    /**
     * Lock state: {@code false}=opened(empty), {@code true}=locked(full).
     */
    private boolean lock = false;

    /**
     * Manager 将生产类型放入任务盒。
     * <p>
     * 非显而易见行为：
     * <ul>
     *   <li>使用 while 循环而非 if 判断，防止虚假唤醒（spurious wakeup）</li>
     *   <li>中断时恢复中断标志位并直接返回，不再执行 put</li>
     * </ul>
     * </p>
     *
     * @param pID the production type ID (1..n)
     */
    public synchronized void put(int pID) {
        // Manager waits while lock is locked (box already has an unprocessed task)
        while (lock) {
            try {
                logger.debug("经理等待中 - 任务盒已上锁（pID={} 尚未被处理）", this.pID);
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("经理线程在等待时被中断", e);
                return;
            }
        }

        // Put the production type into the task box
        this.pID = pID;

        // Lock the box after putting
        lock = true;

        logger.info("【经理】放入生产类型 pID={} 到任务盒 → lock=true（上锁），调用 notify() 通知组长", pID);

        // Inform the team leader
        notify();
    }

    /**
     * Team Leader 从任务盒取出生产类型。
     * <p>
     * 非显而易见行为：
     * <ul>
     *   <li>使用 while 循环而非 if 判断，防止虚假唤醒（spurious wakeup）</li>
     *   <li>先开锁再读取 pID，顺序不可颠倒</li>
     *   <li>中断时返回 {@code -1}，调用方需据此判断退出</li>
     * </ul>
     * </p>
     *
     * @return the production type ID, or {@code -1} if interrupted
     */
    public synchronized int get() {
        // Team leader waits if the box is empty (lock is opened, no task to get)
        while (!lock) {
            try {
                logger.debug("组长等待中 - 任务盒为空（尚无任务），调用 wait() 阻塞");
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("组长线程在等待时被中断", e);
                return -1;
            }
        }

        // Team leader opens the lock with the unique key
        lock = false;

        // Get the production type from the task box
        int result = this.pID;

        logger.info("【组长】用钥匙开锁，从任务盒取出生产类型 pID={} → lock=false（开锁），调用 notify() 通知经理", result);

        // Notify the manager that the box is now empty
        notify();

        return result;
    }
}
