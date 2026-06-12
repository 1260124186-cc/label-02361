// -*- coding: utf-8 -*-
package com.factory.process;

import com.factory.model.Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Team Leader Process (Consumer).
 * <p>
 * The team leader gets the production type from the task box and then
 * organizes team members to manufacture it. The team leader has a unique
 * key to open the lock on the task box.
 * The process repeats without stopping until the thread is interrupted.
 * </p>
 * <p>
 * Synchronization behavior:
 * <ul>
 *   <li>If the getting operation is ahead of the putting operation,
 *       the team leader is blocked through {@code wait()}</li>
 *   <li>Opens the lock with the unique key ({@code lock = false})</li>
 *   <li>Gets the production type and notifies the Manager</li>
 * </ul>
 * </p>
 *
 * @author factory-sync
 * @version 1.0.0
 */
public class TeamLeaderProcess implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(TeamLeaderProcess.class);

    /** Shared Factory (task box) instance */
    private final Factory factory;

    /**
     * Constructs a TeamLeaderProcess.
     *
     * @param factory the shared Factory task box
     */
    public TeamLeaderProcess(Factory factory) {
        if (factory == null) {
            throw new IllegalArgumentException("Factory must not be null");
        }
        this.factory = factory;
    }

    /**
     * Runs the team leader consumption loop.
     * <p>
     * Continuously retrieves production types from the task box and
     * simulates organizing team members for manufacturing. A simulated
     * manufacturing interval represents the production time.
     * </p>
     */
    @Override
    public void run() {
        logger.info("组长进程启动 - 等待经理分配生产任务...");

        int taskCount = 0;

        while (!Thread.currentThread().isInterrupted()) {
            // Get the production type from the task box (blocks if empty)
            int pID = factory.get();

            if (pID == -1) {
                // Thread was interrupted while waiting
                break;
            }

            taskCount++;
            logger.info("[任务 #{}] 组长组织团队开始制造生产类型 pID={}", taskCount, pID);

            try {
                // Simulate manufacturing time (1.5 seconds per production run)
                Thread.sleep(1500);
                logger.info("[任务 #{}] 生产类型 pID={} 制造完成！", taskCount, pID);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.info("组长进程被中断 - 已完成 {} 个任务，正在关闭", taskCount);
                break;
            }
        }

        logger.info("组长进程已终止");
    }
}
