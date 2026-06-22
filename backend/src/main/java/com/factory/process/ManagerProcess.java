package com.factory.process;

import com.factory.config.AppConfig;
import com.factory.config.ProductionStrategy;
import com.factory.model.Factory;
import com.factory.model.FactoryResult;
import com.factory.model.ProductionTypeRegistry;
import com.factory.strategy.ProductionScheduleStrategy;
import com.factory.strategy.RoundRobinStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class ManagerProcess implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(ManagerProcess.class);

    private final Factory factory;
    private final ProductionTypeRegistry registry;
    private final ProductionStrategy strategy;
    private final ProductionScheduleStrategy scheduleStrategy;
    private final int legacyMaxProductionTypes;
    private final boolean legacyMode;
    private final boolean scheduleStrategyMode;
    private final long intervalMs;

    public ManagerProcess(Factory factory, int maxProductionTypes) {
        this(factory, maxProductionTypes, AppConfig.DEFAULT_MANAGER_INTERVAL_MS);
    }

    public ManagerProcess(Factory factory, int maxProductionTypes, long intervalMs) {
        if (factory == null) {
            throw new IllegalArgumentException("Factory must not be null");
        }
        if (maxProductionTypes < 1) {
            throw new IllegalArgumentException("maxProductionTypes must be >= 1, got: " + maxProductionTypes);
        }
        if (intervalMs < 0) {
            throw new IllegalArgumentException("intervalMs must be >= 0, got: " + intervalMs);
        }
        this.factory = factory;
        this.registry = null;
        this.strategy = null;
        this.scheduleStrategy = null;
        this.legacyMaxProductionTypes = maxProductionTypes;
        this.legacyMode = true;
        this.scheduleStrategyMode = false;
        this.intervalMs = intervalMs;
    }

    public ManagerProcess(Factory factory, ProductionTypeRegistry registry, ProductionStrategy strategy) {
        this(factory, registry, strategy, AppConfig.DEFAULT_MANAGER_INTERVAL_MS);
    }

    public ManagerProcess(Factory factory, ProductionTypeRegistry registry, ProductionStrategy strategy, AppConfig config) {
        this(factory, registry, strategy, config.getManagerIntervalMs());
    }

    public ManagerProcess(Factory factory, ProductionTypeRegistry registry, ProductionStrategy strategy, long intervalMs) {
        if (factory == null) {
            throw new IllegalArgumentException("Factory must not be null");
        }
        if (registry == null) {
            throw new IllegalArgumentException("ProductionTypeRegistry must not be null");
        }
        if (strategy == null) {
            throw new IllegalArgumentException("ProductionStrategy must not be null");
        }
        if (intervalMs < 0) {
            throw new IllegalArgumentException("intervalMs must be >= 0, got: " + intervalMs);
        }
        this.factory = factory;
        this.registry = registry;
        this.strategy = strategy;
        this.scheduleStrategy = null;
        this.legacyMaxProductionTypes = registry.getMaxId();
        this.legacyMode = false;
        this.scheduleStrategyMode = false;
        this.intervalMs = intervalMs;
    }

    public ManagerProcess(Factory factory, ProductionTypeRegistry registry, ProductionScheduleStrategy scheduleStrategy) {
        this(factory, registry, scheduleStrategy, AppConfig.DEFAULT_MANAGER_INTERVAL_MS);
    }

    public ManagerProcess(Factory factory, ProductionTypeRegistry registry, ProductionScheduleStrategy scheduleStrategy, AppConfig config) {
        this(factory, registry, scheduleStrategy, config.getManagerIntervalMs());
    }

    public ManagerProcess(Factory factory, ProductionTypeRegistry registry, ProductionScheduleStrategy scheduleStrategy, long intervalMs) {
        if (factory == null) {
            throw new IllegalArgumentException("Factory must not be null");
        }
        if (registry == null) {
            throw new IllegalArgumentException("ProductionTypeRegistry must not be null");
        }
        if (scheduleStrategy == null) {
            throw new IllegalArgumentException("ProductionScheduleStrategy must not be null");
        }
        if (intervalMs < 0) {
            throw new IllegalArgumentException("intervalMs must be >= 0, got: " + intervalMs);
        }
        this.factory = factory;
        this.registry = registry;
        this.strategy = null;
        this.scheduleStrategy = scheduleStrategy;
        this.legacyMaxProductionTypes = registry.getMaxId();
        this.legacyMode = false;
        this.scheduleStrategyMode = true;
        this.intervalMs = intervalMs;
    }

    @Override
    public void run() {
        if (legacyMode) {
            runLegacy();
        } else if (scheduleStrategyMode) {
            runWithScheduleStrategy();
        } else {
            runWithStrategy();
        }
    }

    private void runLegacy() {
        logger.info("经理进程启动 - 将分配生产类型 pID 1..{} (兼容模式)", legacyMaxProductionTypes);

        int pID = 1;
        int dayCount = 0;

        while (!Thread.currentThread().isInterrupted()) {
            dayCount++;
            logger.info("[第 {} 天] 经理准备分配生产类型 pID={}", dayCount, pID);

            FactoryResult putResult = factory.put(pID);
            if (putResult == FactoryResult.SUCCESS) {
                pID = (pID % legacyMaxProductionTypes) + 1;
            } else if (putResult == FactoryResult.INTERRUPTED) {
                logger.info("经理进程被中断 - 已工作 {} 天，正在关闭", dayCount);
                break;
            } else {
                logger.warn("[第 {} 天] 放入任务盒失败（pID={}，结果={}），不递增 pID，不 sleep", dayCount, pID, putResult);
                break;
            }

            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.info("经理进程被中断 - 已工作 {} 天，正在关闭", dayCount);
                break;
            }
        }

        logger.info("经理进程已终止");
    }

    private void runWithStrategy() {
        logger.info("经理进程启动 - 生产策略={}，已启用生产类型 {} 个",
                strategy.getName(), registry.getEnabled().size());
        registry.logAllTypes();

        Iterator<Integer> pIDIterator = strategy.createIterator(registry);
        int dayCount = 0;

        while (!Thread.currentThread().isInterrupted()) {
            dayCount++;

            if (!pIDIterator.hasNext()) {
                logger.info("[第 {} 天] 当前策略跳过今日生产（如周末），等待下一天...", dayCount);
                try {
                    Thread.sleep(intervalMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.info("经理进程被中断 - 已工作 {} 天，正在关闭", dayCount);
                    break;
                }
                continue;
            }

            Integer nextPID;
            try {
                nextPID = pIDIterator.next();
            } catch (NoSuchElementException e) {
                logger.error("[第 {} 天] 策略迭代器无可用元素，终止循环", dayCount, e);
                break;
            }

            if (!registry.isEnabled(nextPID)) {
                logger.warn("[第 {} 天] pID={} 已被禁用，跳过", dayCount, nextPID);
                continue;
            }

            logger.info("[第 {} 天] 经理准备分配生产类型 pID={} ({}) 优先级={}",
                    dayCount, nextPID,
                    registry.getById(nextPID).getName(),
                    registry.getById(nextPID).getPriority());

            FactoryResult putResult = factory.put(nextPID);
            if (putResult != FactoryResult.SUCCESS) {
                if (putResult == FactoryResult.INTERRUPTED) {
                    logger.info("经理进程被中断 - 已工作 {} 天，正在关闭", dayCount);
                } else {
                    logger.warn("[第 {} 天] 放入任务盒失败（pID={}，结果={}），不递增 pID，不 sleep", dayCount, nextPID, putResult);
                }
                break;
            }

            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.info("经理进程被中断 - 已工作 {} 天，正在关闭", dayCount);
                break;
            }
        }

        logger.info("经理进程已终止");
    }

    private void runWithScheduleStrategy() {
        logger.info("经理进程启动 - 排产策略={}，已启用生产类型 {} 个",
                scheduleStrategy.getName(), registry.getEnabled().size());
        registry.logAllTypes();

        int dayCount = 0;

        while (!Thread.currentThread().isInterrupted()) {
            dayCount++;

            Integer nextPID;
            try {
                nextPID = scheduleStrategy.nextPID(registry);
            } catch (NoSuchElementException e) {
                logger.error("[第 {} 天] 排产策略无可用生产类型，终止循环", dayCount, e);
                break;
            }

            if (nextPID == null || !registry.isEnabled(nextPID)) {
                logger.warn("[第 {} 天] pID={} 无效或已被禁用，跳过", dayCount, nextPID);
                try {
                    Thread.sleep(intervalMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.info("经理进程被中断 - 已工作 {} 天，正在关闭", dayCount);
                    break;
                }
                continue;
            }

            logger.info("[第 {} 天] 经理准备分配生产类型 pID={} ({}) 优先级={}",
                    dayCount, nextPID,
                    registry.getById(nextPID).getName(),
                    registry.getById(nextPID).getPriority());

            FactoryResult putResult = factory.put(nextPID);
            if (putResult != FactoryResult.SUCCESS) {
                if (putResult == FactoryResult.INTERRUPTED) {
                    logger.info("经理进程被中断 - 已工作 {} 天，正在关闭", dayCount);
                } else {
                    logger.warn("[第 {} 天] 放入任务盒失败（pID={}，结果={}），不递增 pID，不 sleep", dayCount, nextPID, putResult);
                }
                break;
            }

            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.info("经理进程被中断 - 已工作 {} 天，正在关闭", dayCount);
                break;
            }
        }

        logger.info("经理进程已终止");
    }

    public long getIntervalMs() {
        return intervalMs;
    }

    public ProductionScheduleStrategy getScheduleStrategy() {
        return scheduleStrategy;
    }
}
