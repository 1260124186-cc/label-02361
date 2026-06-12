// -*- coding: utf-8 -*-
package com.factory.process;

import com.factory.model.Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manager Process (Producer).
 * <p>
 * The manager daily puts the manufacturing production type into the task box.
 * Production types are modelled as integers pID (1..n) and cycle repeatedly.
 * The process repeats without stopping until the thread is interrupted.
 * </p>
 * <p>
 * Synchronization behavior:
 * <ul>
 *   <li>Only puts when the box lock is opened ({@code lock == false})</li>
 *   <li>After putting, locks the box and notifies the Team Leader</li>
 *   <li>If the box is still locked, waits via {@code wait()} inside Factory</li>
 * </ul>
 * </p>
 *
 * @author factory-sync
 * @version 1.0.0
 */
public class ManagerProcess implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(ManagerProcess.class);

    /** Shared Factory (task box) instance */
    private final Factory factory;

    /** Maximum production type ID (n). Production types cycle 1..n */
    private final int maxProductionTypes;

    /**
     * Constructs a ManagerProcess.
     *
     * @param factory            the shared Factory task box
     * @param maxProductionTypes the maximum production type ID (n)
     */
    public ManagerProcess(Factory factory, int maxProductionTypes) {
        if (factory == null) {
            throw new IllegalArgumentException("Factory must not be null");
        }
        if (maxProductionTypes < 1) {
            throw new IllegalArgumentException("maxProductionTypes must be >= 1, got: " + maxProductionTypes);
        }
        this.factory = factory;
        this.maxProductionTypes = maxProductionTypes;
    }

    /**
     * Runs the manager production loop.
     * <p>
     * Continuously cycles through production types 1..n, putting each
     * into the task box. A simulated work interval represents the daily
     * production planning cycle.
     * </p>
     */
    @Override
    public void run() {
        logger.info("经理进程启动 - 将分配生产类型 pID 1..{}", maxProductionTypes);

        int pID = 1;
        int dayCount = 0;

        while (!Thread.currentThread().isInterrupted()) {
            dayCount++;
            logger.info("[第 {} 天] 经理准备分配生产类型 pID={}", dayCount, pID);

            // Put the production type into the task box
            factory.put(pID);

            // Cycle through production types: 1 → 2 → ... → n → 1 → ...
            pID = (pID % maxProductionTypes) + 1;

            try {
                // Simulate daily work interval (1 second = 1 day)
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.info("经理进程被中断 - 已工作 {} 天，正在关闭", dayCount);
                break;
            }
        }

        logger.info("经理进程已终止");
    }
}
