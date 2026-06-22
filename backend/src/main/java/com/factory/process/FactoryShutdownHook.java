package com.factory.process;

import com.factory.model.Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FactoryShutdownHook implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(FactoryShutdownHook.class);

    private final Thread managerThread;
    private final Thread teamLeaderThread;
    private final Factory factory;
    private final long shutdownTimeoutMs;
    private final boolean drainOnShutdown;

    public FactoryShutdownHook(Thread managerThread, Thread teamLeaderThread,
                               Factory factory, long shutdownTimeoutMs, boolean drainOnShutdown) {
        if (managerThread == null) {
            throw new IllegalArgumentException("managerThread must not be null");
        }
        if (teamLeaderThread == null) {
            throw new IllegalArgumentException("teamLeaderThread must not be null");
        }
        if (factory == null) {
            throw new IllegalArgumentException("factory must not be null");
        }
        if (shutdownTimeoutMs < 0) {
            throw new IllegalArgumentException("shutdownTimeoutMs must be >= 0, got: " + shutdownTimeoutMs);
        }
        this.managerThread = managerThread;
        this.teamLeaderThread = teamLeaderThread;
        this.factory = factory;
        this.shutdownTimeoutMs = shutdownTimeoutMs;
        this.drainOnShutdown = drainOnShutdown;
    }

    @Override
    public void run() {
        logger.info("");
        logger.info("========================================================");
        logger.info("  收到关闭信号 - 正在停止所有进程...");
        logger.info("  两阶段关闭: drain-on-shutdown={}, 超时: {}ms", drainOnShutdown, shutdownTimeoutMs);
        logger.info("========================================================");

        phase1_stopManager();
        phase2_stopOrDrainLeader();

        logger.info("所有进程已停止，系统关闭完成。");
    }

    private void phase1_stopManager() {
        logger.info("[阶段1] 停止经理（不再投放新任务）...");
        managerThread.interrupt();

        try {
            managerThread.join(shutdownTimeoutMs);
            if (managerThread.isAlive()) {
                logger.warn("经理线程在 {}ms 后仍未终止，继续关闭", shutdownTimeoutMs);
            } else {
                logger.info("[阶段1] 经理已停止");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        factory.shutdown();
        logger.info("工厂已标记为关闭状态，不再接受新任务");
    }

    private void phase2_stopOrDrainLeader() {
        if (drainOnShutdown) {
            logger.info("[阶段2] drain-on-shutdown=true，等待组长完成当前制造...");
            try {
                teamLeaderThread.join(shutdownTimeoutMs);
                if (teamLeaderThread.isAlive()) {
                    logger.warn("组长在 {}ms 后仍未完成，强制中断", shutdownTimeoutMs);
                    teamLeaderThread.interrupt();
                    teamLeaderThread.join(shutdownTimeoutMs);
                } else {
                    logger.info("[阶段2] 组长已完成所有制造任务");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                teamLeaderThread.interrupt();
            }
        } else {
            logger.info("[阶段2] drain-on-shutdown=false，立即中断组长...");
            teamLeaderThread.interrupt();
            try {
                teamLeaderThread.join(shutdownTimeoutMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public Thread getManagerThread() {
        return managerThread;
    }

    public Thread getTeamLeaderThread() {
        return teamLeaderThread;
    }

    public Factory getFactory() {
        return factory;
    }

    public long getShutdownTimeoutMs() {
        return shutdownTimeoutMs;
    }

    public boolean isDrainOnShutdown() {
        return drainOnShutdown;
    }
}
