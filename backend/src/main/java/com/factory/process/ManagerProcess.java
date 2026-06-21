// -*- coding: utf-8 -*-
package com.factory.process;

import com.factory.model.Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 经理进程（生产者）：循环向任务盒分配生产类型 pID。
 * <p>
 * 详细流程请参见 {@code docs/project_design.md} 第 3、4.2 节。
 * </p>
 *
 * @author factory-sync
 * @version 1.0.0
 * @see <a href="../../docs/project_design.md">project_design.md</a>
 */
public class ManagerProcess implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(ManagerProcess.class);

    /** Shared Factory (task box) instance. */
    private final Factory factory;

    /** Maximum production type ID. Production types cycle 1..n. */
    private final int maxProductionTypes;

    /**
     * @param factory            the shared Factory task box (non-null)
     * @param maxProductionTypes the maximum pID, must be {@code >= 1}
     * @throws IllegalArgumentException if factory is null or maxProductionTypes invalid
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
     * 经理生产循环：持续分配 pID 1..n，直到线程被中断。
     * <p>
     * 非显而易见行为：
     * <ul>
     *   <li>pID 取模循环：{@code (pID % maxProductionTypes) + 1}</li>
     *   <li>Sleep 期间被中断后恢复中断标志位，使 while 条件正常退出</li>
     * </ul>
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
