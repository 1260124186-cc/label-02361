// -*- coding: utf-8 -*-
package com.factory.process;

import com.factory.model.Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 组长进程（消费者）：从任务盒取 pID 并组织团队制造。
 * <p>
 * 详细流程请参见 {@code docs/project_design.md} 第 3、4.3 节。
 * </p>
 *
 * @author factory-sync
 * @version 1.0.0
 * @see <a href="../../docs/project_design.md">project_design.md</a>
 */
public class TeamLeaderProcess implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(TeamLeaderProcess.class);

    /** Shared Factory (task box) instance. */
    private final Factory factory;

    /**
     * @param factory the shared Factory task box (non-null)
     * @throws IllegalArgumentException if factory is null
     */
    public TeamLeaderProcess(Factory factory) {
        if (factory == null) {
            throw new IllegalArgumentException("Factory must not be null");
        }
        this.factory = factory;
    }

    /**
     * 组长消费循环：持续取任务并制造，直到线程被中断。
     * <p>
     * 非显而易见行为：
     * <ul>
     *   <li>factory.get() 返回 {@code -1} 表示等待期间被中断，需立即 break</li>
     *   <li>制造间隔 (1.5s) 大于经理分配间隔 (1s)，模拟生产慢于计划</li>
     * </ul>
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
