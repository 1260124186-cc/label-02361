// -*- coding: utf-8 -*-
package com.factory.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory class representing a task box with a lock mechanism.
 * <p>
 * The task box holds a production type ID (pID). Access is synchronized
 * between the Manager (producer) and Team Leader (consumer) using
 * Java's intrinsic lock with {@code wait()} and {@code notify()}.
 * </p>
 * <p>
 * Lock semantics:
 * <ul>
 *   <li>{@code lock = false} (opened) — the box is empty, Manager can put</li>
 *   <li>{@code lock = true} (locked) — the box is full, Team Leader can get</li>
 * </ul>
 * </p>
 *
 * @author factory-sync
 * @version 1.0.0
 */
public class Factory {

    private static final Logger logger = LoggerFactory.getLogger(Factory.class);

    /** Production type ID stored in the task box */
    private int pID;

    /**
     * Lock state of the task box.
     * <ul>
     *   <li>{@code false} = opened (initial state, box is empty)</li>
     *   <li>{@code true}  = locked (box contains an unprocessed task)</li>
     * </ul>
     */
    private boolean lock = false;

    /**
     * Manager puts a production type into the task box.
     * <p>
     * The manager only conducts the putting operation when the box lock is opened
     * ({@code lock == false}). If the lock is currently locked ({@code lock == true}),
     * the manager waits until the team leader processes the current task and opens
     * the lock.
     * </p>
     * <p>
     * After finishing the putting operation, the manager locks the box
     * ({@code lock = true}) and informs the team leader through {@code notify()}.
     * </p>
     *
     * @param pID the production type ID (1..n) to put into the task box
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
     * Team leader gets the production type from the task box.
     * <p>
     * If the getting operation of the team leader is ahead of the putting
     * operation of the manager (i.e., the box is empty / lock is opened),
     * the team leader is blocked through {@code wait()}.
     * </p>
     * <p>
     * The team leader has a unique key. Upon being notified, the team leader
     * opens the lock ({@code lock = false}) and then gets the production type
     * assigned by the manager.
     * </p>
     *
     * @return the production type ID from the task box, or -1 if interrupted
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
