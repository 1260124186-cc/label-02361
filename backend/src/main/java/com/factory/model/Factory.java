// -*- coding: utf-8 -*-
package com.factory.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Factory {

    private static final Logger logger = LoggerFactory.getLogger(Factory.class);

    private final ProductionTypeRegistry registry;

    private int pID;

    private boolean lock = false;

    public Factory() {
        this.registry = null;
    }

    public Factory(ProductionTypeRegistry registry) {
        this.registry = registry;
    }

    public ProductionTypeRegistry getRegistry() {
        return registry;
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

        this.pID = pID;
        lock = true;

        if (registry != null) {
            ProductionType pt = registry.getById(pID);
            logger.info("【经理】放入生产类型 pID={} ({}) 优先级={} 到任务盒 → lock=true（上锁），调用 notify() 通知组长",
                    pID, pt.getName(), pt.getPriority());
        } else {
            logger.info("【经理】放入生产类型 pID={} 到任务盒 → lock=true（上锁），调用 notify() 通知组长", pID);
        }

        notify();
    }

    public synchronized int get() {
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

        lock = false;
        int result = this.pID;

        if (registry != null) {
            ProductionType pt = registry.getById(result);
            logger.info("【组长】用钥匙开锁，从任务盒取出生产类型 pID={} ({}) 制造时长={}ms → lock=false（开锁），调用 notify() 通知经理",
                    result, pt.getName(), pt.getManufacturingDurationMs());
        } else {
            logger.info("【组长】用钥匙开锁，从任务盒取出生产类型 pID={} → lock=false（开锁），调用 notify() 通知经理", result);
        }

        notify();
        return result;
    }
}
