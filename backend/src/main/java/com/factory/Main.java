// -*- coding: utf-8 -*-
package com.factory;

import com.factory.config.ProductionStrategy;
import com.factory.config.ProductionTypeConfigLoader;
import com.factory.model.Factory;
import com.factory.model.ProductionTypeRegistry;
import com.factory.process.ManagerProcess;
import com.factory.process.TeamLeaderProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        ProductionTypeConfigLoader loader = new ProductionTypeConfigLoader();
        ProductionTypeConfigLoader.LoadResult config = loader.load(args);

        ProductionTypeRegistry registry = config.registry;
        ProductionStrategy strategy = config.strategy;

        logger.info("========================================================");
        logger.info("  工厂生产同步系统 (Factory Production Sync)");
        logger.info("========================================================");
        logger.info("  配置来源       : {}", config.source);
        logger.info("  生产策略       : {}", strategy.getName());
        logger.info("  生产类型总数   : {} (启用: {})", registry.size(), registry.getEnabled().size());
        logger.info("  生产类型范围   : pID 1..{}", registry.getMaxId());
        logger.info("  经理 (Manager) : 生产者 - 将 pID 放入任务盒");
        logger.info("  组长 (Leader)  : 消费者 - 从任务盒取出 pID");
        logger.info("  同步机制       : wait() / notify() + boolean 锁");
        logger.info("  锁初始状态     : lock=false（开锁/任务盒为空）");
        logger.info("========================================================");
        logger.info("  策略说明:");
        logger.info("    sequential        - 按 pID 升序循环（1→2→...→n→1）");
        logger.info("    priority          - 按优先级从高到低循环");
        logger.info("    weekday           - 按 pID 升序，但周末不生产");
        logger.info("    priority-weekday  - 按优先级循环，周末不生产");
        logger.info("  命令行用法: java -jar ... [n] [configFile] [strategy]");
        logger.info("========================================================");
        logger.info("  按 Ctrl+C 停止系统");
        logger.info("========================================================");
        logger.info("");

        registry.logAllTypes();

        Factory factory = new Factory(registry);

        Thread managerThread = new Thread(
                new ManagerProcess(factory, registry, strategy), "经理");
        Thread teamLeaderThread = new Thread(
                new TeamLeaderProcess(factory, registry), "组长");

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

        managerThread.start();
        teamLeaderThread.start();

        logger.info("两个进程已启动，生产同步正在运行...");
        logger.info("");

        try {
            managerThread.join();
            teamLeaderThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.info("主线程被中断");
        }
    }
}
