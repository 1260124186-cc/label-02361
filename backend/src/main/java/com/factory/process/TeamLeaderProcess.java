// -*- coding: utf-8 -*-
package com.factory.process;

import com.factory.config.AppConfig;
import com.factory.model.Factory;
import com.factory.model.ProductionType;
import com.factory.model.ProductionTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TeamLeaderProcess implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(TeamLeaderProcess.class);

    private final Factory factory;
    private final ProductionTypeRegistry registry;
    private final long defaultManufacturingMs;

    public TeamLeaderProcess(Factory factory) {
        this(factory, null, AppConfig.DEFAULT_TEAM_LEADER_MANUFACTURING_MS);
    }

    public TeamLeaderProcess(Factory factory, long defaultManufacturingMs) {
        this(factory, null, defaultManufacturingMs);
    }

    public TeamLeaderProcess(Factory factory, ProductionTypeRegistry registry) {
        this(factory, registry, AppConfig.DEFAULT_TEAM_LEADER_MANUFACTURING_MS);
    }

    public TeamLeaderProcess(Factory factory, ProductionTypeRegistry registry, AppConfig config) {
        this(factory, registry, config.getTeamLeaderManufacturingMs());
    }

    public TeamLeaderProcess(Factory factory, ProductionTypeRegistry registry, long defaultManufacturingMs) {
        if (factory == null) {
            throw new IllegalArgumentException("Factory must not be null");
        }
        if (defaultManufacturingMs < 0) {
            throw new IllegalArgumentException("defaultManufacturingMs must be >= 0, got: " + defaultManufacturingMs);
        }
        this.factory = factory;
        this.registry = registry;
        this.defaultManufacturingMs = defaultManufacturingMs;
    }

    @Override
    public void run() {
        logger.info("组长进程启动 - 等待经理分配生产任务...");

        int taskCount = 0;

        while (!Thread.currentThread().isInterrupted()) {
            int pID = factory.get();

            if (pID == -1) {
                break;
            }

            taskCount++;

            long manufacturingMs = defaultManufacturingMs;
            String typeName = null;

            if (registry != null) {
                try {
                    ProductionType pt = registry.getById(pID);
                    manufacturingMs = pt.getManufacturingDurationMs();
                    typeName = pt.getName();
                } catch (Exception e) {
                    logger.warn("[任务 #{}] 未找到 pID={} 的元数据，使用默认制造时长 {}ms",
                            taskCount, pID, defaultManufacturingMs);
                }
            }

            if (typeName != null) {
                logger.info("[任务 #{}] 组长组织团队开始制造生产类型 pID={} ({})，预计耗时 {}ms",
                        taskCount, pID, typeName, manufacturingMs);
            } else {
                logger.info("[任务 #{}] 组长组织团队开始制造生产类型 pID={}，预计耗时 {}ms",
                        taskCount, pID, manufacturingMs);
            }

            try {
                Thread.sleep(manufacturingMs);
                if (typeName != null) {
                    logger.info("[任务 #{}] 生产类型 pID={} ({}) 制造完成！", taskCount, pID, typeName);
                } else {
                    logger.info("[任务 #{}] 生产类型 pID={} 制造完成！", taskCount, pID);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.info("组长进程被中断 - 已完成 {} 个任务，正在关闭", taskCount);
                break;
            }
        }

        logger.info("组长进程已终止");
    }

    public long getDefaultManufacturingMs() {
        return defaultManufacturingMs;
    }
}
