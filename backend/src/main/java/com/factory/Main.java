// -*- coding: utf-8 -*-
package com.factory;

import com.factory.model.Factory;
import com.factory.process.ManagerProcess;
import com.factory.process.TeamLeaderProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for the Factory Production Synchronization System.
 * <p>
 * Demonstrates the Producer-Consumer pattern using Java's intrinsic
 * lock mechanism ({@code synchronized}, {@code wait()}, {@code notify()}).
 * </p>
 * <p>
 * Architecture:
 * <ul>
 *   <li>Factory — shared task box with lock-based condition synchronization</li>
 *   <li>ManagerProcess — producer thread that puts production types</li>
 *   <li>TeamLeaderProcess — consumer thread that gets production types</li>
 * </ul>
 * </p>
 *
 * @author factory-sync
 * @version 1.0.0
 */
public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    /** Default number of production types (n) */
    private static final int DEFAULT_PRODUCTION_TYPES = 5;
    private static final String ENV_PRODUCTION_TYPES = "PRODUCTION_TYPES";

    private static int resolveProductionTypes(String[] args) {
        if (args.length > 0) {
            try {
                int n = Integer.parseInt(args[0]);
                if (n < 1) {
                    logger.warn("无效的命令行参数: {}，尝试环境变量", args[0]);
                } else {
                    logger.info("配置来源: 命令行参数");
                    return n;
                }
            } catch (NumberFormatException e) {
                logger.warn("无法解析命令行参数 '{}' 为整数，尝试环境变量", args[0]);
            }
        }

        String envValue = System.getenv(ENV_PRODUCTION_TYPES);
        if (envValue != null && !envValue.isEmpty()) {
            try {
                int n = Integer.parseInt(envValue);
                if (n < 1) {
                    logger.warn("无效的环境变量 {}={}", ENV_PRODUCTION_TYPES, envValue);
                } else {
                    logger.info("配置来源: 环境变量 {}={}", ENV_PRODUCTION_TYPES, envValue);
                    return n;
                }
            } catch (NumberFormatException e) {
                logger.warn("无法解析环境变量 {}='{}' 为整数", ENV_PRODUCTION_TYPES, envValue);
            }
        }

        logger.info("配置来源: 默认值 {}", DEFAULT_PRODUCTION_TYPES);
        return DEFAULT_PRODUCTION_TYPES;
    }

    public static void main(String[] args) {
        int n = resolveProductionTypes(args);

        logger.info("========================================================");
        logger.info("  工厂生产同步系统 (Factory Production Sync)");
        logger.info("========================================================");
        logger.info("  生产类型范围   : pID 1..{}", n);
        logger.info("  经理 (Manager) : 生产者 - 将 pID 放入任务盒");
        logger.info("  组长 (Leader)  : 消费者 - 从任务盒取出 pID");
        logger.info("  同步机制       : wait() / notify() + boolean 锁");
        logger.info("  锁初始状态     : lock=false（开锁/任务盒为空）");
        logger.info("========================================================");
        logger.info("  按 Ctrl+C 停止系统");
        logger.info("========================================================");
        logger.info("");

        // Create the shared Factory (task box)
        Factory factory = new Factory();

        // Create process threads
        Thread managerThread = new Thread(new ManagerProcess(factory, n), "经理");
        Thread teamLeaderThread = new Thread(new TeamLeaderProcess(factory), "组长");

        // Register shutdown hook for graceful termination
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("");
            logger.info("========================================================");
            logger.info("  收到关闭信号 - 正在停止所有进程...");
            logger.info("========================================================");

            managerThread.interrupt();
            teamLeaderThread.interrupt();

            try {
                managerThread.join(3000);
                teamLeaderThread.join(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            logger.info("所有进程已停止，系统关闭完成。");
        }, "关闭钩子"));

        // Start both processes
        managerThread.start();
        teamLeaderThread.start();

        logger.info("两个进程已启动，生产同步正在运行...");
        logger.info("");

        // Main thread waits for both processes (they run indefinitely)
        try {
            managerThread.join();
            teamLeaderThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.info("主线程被中断");
        }
    }
}
