// -*- coding: utf-8 -*-
package com.factory.process;

import com.factory.config.AppConfig;
import com.factory.model.CapacityStrategy;
import com.factory.model.Factory;
import com.factory.model.GetResult;
import com.factory.model.ManufacturingTask;
import com.factory.model.ProductionType;
import com.factory.model.ProductionTypeRegistry;
import com.factory.model.TeamMember;
import com.factory.model.TeamMemberPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class TeamLeaderProcess implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(TeamLeaderProcess.class);

    private final Factory factory;
    private final ProductionTypeRegistry registry;
    private final long defaultManufacturingMs;
    private final TeamMemberPool memberPool;
    private final int requiredPeoplePerTask;
    private final CapacityStrategy capacityStrategy;
    private final long capacityWaitTimeoutMs;
    private final boolean useThreadPool;
    private final int threadPoolSize;
    private ExecutorService threadPool;
    private final AtomicInteger taskIdCounter;

    public TeamLeaderProcess(Factory factory) {
        this(factory, null, AppConfig.DEFAULT_TEAM_LEADER_MANUFACTURING_MS,
                TeamMemberPool.createDefault(AppConfig.DEFAULT_TEAM_SIZE),
                AppConfig.DEFAULT_REQUIRED_PEOPLE_PER_TASK,
                CapacityStrategy.fromCode(AppConfig.DEFAULT_CAPACITY_STRATEGY),
                AppConfig.DEFAULT_CAPACITY_WAIT_TIMEOUT_MS,
                AppConfig.DEFAULT_USE_THREAD_POOL,
                AppConfig.DEFAULT_THREAD_POOL_SIZE);
    }

    public TeamLeaderProcess(Factory factory, long defaultManufacturingMs) {
        this(factory, null, defaultManufacturingMs,
                TeamMemberPool.createDefault(AppConfig.DEFAULT_TEAM_SIZE),
                AppConfig.DEFAULT_REQUIRED_PEOPLE_PER_TASK,
                CapacityStrategy.fromCode(AppConfig.DEFAULT_CAPACITY_STRATEGY),
                AppConfig.DEFAULT_CAPACITY_WAIT_TIMEOUT_MS,
                AppConfig.DEFAULT_USE_THREAD_POOL,
                AppConfig.DEFAULT_THREAD_POOL_SIZE);
    }

    public TeamLeaderProcess(Factory factory, ProductionTypeRegistry registry) {
        this(factory, registry, AppConfig.DEFAULT_TEAM_LEADER_MANUFACTURING_MS,
                TeamMemberPool.createDefault(AppConfig.DEFAULT_TEAM_SIZE),
                AppConfig.DEFAULT_REQUIRED_PEOPLE_PER_TASK,
                CapacityStrategy.fromCode(AppConfig.DEFAULT_CAPACITY_STRATEGY),
                AppConfig.DEFAULT_CAPACITY_WAIT_TIMEOUT_MS,
                AppConfig.DEFAULT_USE_THREAD_POOL,
                AppConfig.DEFAULT_THREAD_POOL_SIZE);
    }

    public TeamLeaderProcess(Factory factory, ProductionTypeRegistry registry, long defaultManufacturingMs) {
        this(factory, registry, defaultManufacturingMs,
                TeamMemberPool.createDefault(AppConfig.DEFAULT_TEAM_SIZE),
                AppConfig.DEFAULT_REQUIRED_PEOPLE_PER_TASK,
                CapacityStrategy.fromCode(AppConfig.DEFAULT_CAPACITY_STRATEGY),
                AppConfig.DEFAULT_CAPACITY_WAIT_TIMEOUT_MS,
                AppConfig.DEFAULT_USE_THREAD_POOL,
                AppConfig.DEFAULT_THREAD_POOL_SIZE);
    }

    public TeamLeaderProcess(Factory factory, ProductionTypeRegistry registry, AppConfig config) {
        this(factory, registry, config.getTeamLeaderManufacturingMs(),
                TeamMemberPool.createDefault(config.getTeamSize(),
                        CapacityStrategy.fromCode(config.getCapacityStrategy()),
                        config.getCapacityWaitTimeoutMs()),
                config.getRequiredPeoplePerTask(),
                CapacityStrategy.fromCode(config.getCapacityStrategy()),
                config.getCapacityWaitTimeoutMs(),
                config.isUseThreadPool(),
                config.getThreadPoolSize());
    }

    public TeamLeaderProcess(Factory factory, ProductionTypeRegistry registry, long defaultManufacturingMs,
                             TeamMemberPool memberPool, int requiredPeoplePerTask,
                             CapacityStrategy capacityStrategy, long capacityWaitTimeoutMs,
                             boolean useThreadPool, int threadPoolSize) {
        if (factory == null) {
            throw new IllegalArgumentException("Factory must not be null");
        }
        if (defaultManufacturingMs < 0) {
            throw new IllegalArgumentException("defaultManufacturingMs must be >= 0, got: " + defaultManufacturingMs);
        }
        if (memberPool == null) {
            throw new IllegalArgumentException("TeamMemberPool must not be null");
        }
        if (requiredPeoplePerTask < 1) {
            throw new IllegalArgumentException("requiredPeoplePerTask must be >= 1, got: " + requiredPeoplePerTask);
        }
        if (capacityStrategy == null) {
            throw new IllegalArgumentException("CapacityStrategy must not be null");
        }
        if (capacityWaitTimeoutMs < 0) {
            throw new IllegalArgumentException("capacityWaitTimeoutMs must be >= 0, got: " + capacityWaitTimeoutMs);
        }
        if (threadPoolSize < 1) {
            throw new IllegalArgumentException("threadPoolSize must be >= 1, got: " + threadPoolSize);
        }
        this.factory = factory;
        this.registry = registry;
        this.defaultManufacturingMs = defaultManufacturingMs;
        this.memberPool = memberPool;
        this.requiredPeoplePerTask = requiredPeoplePerTask;
        this.capacityStrategy = capacityStrategy;
        this.capacityWaitTimeoutMs = capacityWaitTimeoutMs;
        this.useThreadPool = useThreadPool;
        this.threadPoolSize = threadPoolSize;
        this.taskIdCounter = new AtomicInteger(0);
    }

    @Override
    public void run() {
        logger.info("组长进程启动 - 等待经理分配生产任务...");
        logger.info("团队配置: {} 人, 每任务需 {} 人, 产能策略={}, 线程池={} (大小={})",
                memberPool.getTotalCount(), requiredPeoplePerTask,
                capacityStrategy.getCode(), useThreadPool, threadPoolSize);
        memberPool.logStatus();

        initializeThreadPool();

        int taskCount = 0;

        try {
            while (!Thread.currentThread().isInterrupted()) {
                GetResult getResult = factory.get();

                if (!getResult.isSuccess()) {
                    if (getResult.isInterrupted()) {
                        logger.info("组长进程收到中断信号 - 已完成 {} 个任务，正在关闭", taskCount);
                    } else if (getResult.isInvalid()) {
                        logger.info("组长进程收到关闭信号（工厂已关闭） - 已完成 {} 个任务，正在关闭", taskCount);
                    }
                    break;
                }

                int pID = getResult.getValue();

                taskCount++;
                int taskId = taskIdCounter.incrementAndGet();

                if (!executeManufacturingTask(taskId, pID, taskCount)) {
                    logger.warn("[任务 #{}] 生产任务 pID={} 执行失败或被中断，继续处理下一个任务", taskCount, pID);
                }

                if (Thread.currentThread().isInterrupted()) {
                    logger.info("组长进程被中断 - 已完成 {} 个任务，正在关闭", taskCount);
                    break;
                }
            }
        } finally {
            shutdownThreadPool();
        }

        logger.info("组长进程已终止 - 共处理 {} 个任务，产能告警 {} 次",
                taskCount, memberPool.getAlertCount());
    }

    private void initializeThreadPool() {
        if (useThreadPool) {
            this.threadPool = Executors.newFixedThreadPool(threadPoolSize, new ThreadFactory() {
                private final AtomicInteger threadNumber = new AtomicInteger(1);

                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "worker-thread-" + threadNumber.getAndIncrement());
                    t.setDaemon(false);
                    return t;
                }
            });
            logger.info("线程池已初始化，大小: {}", threadPoolSize);
        }
    }

    private void shutdownThreadPool() {
        if (threadPool != null && !threadPool.isShutdown()) {
            logger.info("正在关闭线程池...");
            threadPool.shutdown();
            try {
                if (!threadPool.awaitTermination(5000, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    threadPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                threadPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
            logger.info("线程池已关闭");
        }
    }

    private boolean executeManufacturingTask(int taskId, int pID, int taskCount) {
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

        ManufacturingTask task = new ManufacturingTask(taskId, pID, requiredPeoplePerTask, manufacturingMs);

        if (typeName != null) {
            logger.info("[任务 #{}] 准备制造生产类型 pID={} ({})，预计耗时 {}ms，需 {} 人",
                    taskCount, pID, typeName, manufacturingMs, requiredPeoplePerTask);
        } else {
            logger.info("[任务 #{}] 准备制造生产类型 pID={}，预计耗时 {}ms，需 {} 人",
                    taskCount, pID, manufacturingMs, requiredPeoplePerTask);
        }

        try {
            List<TeamMember> availableMembers = memberPool.getAvailableMembersForPID(
                    pID, requiredPeoplePerTask, capacityStrategy, capacityWaitTimeoutMs);

            if (availableMembers.isEmpty()) {
                logger.error("[任务 #{}] 无法分配任何成员执行 pID={}，任务失败", taskCount, pID);
                return false;
            }

            if (availableMembers.size() < requiredPeoplePerTask) {
                logger.warn("[任务 #{}] 成员不足: 需要 {} 人，实际分配 {} 人，产能不足告警已记录",
                        taskCount, requiredPeoplePerTask, availableMembers.size());
            }

            for (TeamMember member : availableMembers) {
                task.assignMember(member);
            }

            task.start();

            logger.info("[任务 #{}] 任务 #{} 开始执行，已分配 {} 名成员: {}",
                    taskCount, taskId, task.getAssignedMembers().size(),
                    task.getAssignedMembers().stream()
                            .map(m -> "#" + m.getId() + "(" + m.getName() + ")")
                            .toList());

            executeSubtasks(task);

            task.awaitCompletion();

            return processTaskResult(task, taskCount, typeName);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("[任务 #{}] 分配成员时被中断", taskCount);
            return false;
        } catch (IllegalStateException e) {
            logger.error("[任务 #{}] 任务执行失败: {}", taskCount, e.getMessage());
            return false;
        }
    }

    private void executeSubtasks(ManufacturingTask task) {
        List<TeamMember> members = task.getAssignedMembers();
        List<Thread> threads = new ArrayList<>();

        for (TeamMember member : members) {
            WorkerThread worker = new WorkerThread(member, task);

            if (useThreadPool && threadPool != null) {
                threadPool.submit(worker);
                logger.debug("已提交成员 #{}({}) 到线程池执行子任务", member.getId(), member.getName());
            } else {
                Thread t = new Thread(worker, "worker-" + member.getId());
                threads.add(t);
                t.start();
                logger.debug("已启动成员 #{}({}) 的独立线程执行子任务", member.getId(), member.getName());
            }
        }

        if (!threads.isEmpty()) {
            for (Thread t : threads) {
                try {
                    t.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("等待子任务线程完成时被中断");
                    break;
                }
            }
        }
    }

    private boolean processTaskResult(ManufacturingTask task, int taskCount, String typeName) {
        ManufacturingTask.Status status = task.getStatus();

        if (status == ManufacturingTask.Status.DONE) {
            if (typeName != null) {
                logger.info("[任务 #{}] 生产类型 pID={} ({}) 制造完成！耗时 {}ms，{} 名成员参与",
                        taskCount, task.getPID(), typeName,
                        task.getElapsedDurationMs(), task.getAssignedMembers().size());
            } else {
                logger.info("[任务 #{}] 生产类型 pID={} 制造完成！耗时 {}ms，{} 名成员参与",
                        taskCount, task.getPID(),
                        task.getElapsedDurationMs(), task.getAssignedMembers().size());
            }
            return true;
        } else if (status == ManufacturingTask.Status.FAILED) {
            String reason = task.getFailureReason() != null ? task.getFailureReason() : "未知原因";
            int completed = task.getCompletedSubtasks();
            int failed = task.getFailedSubtasks();
            logger.error("[任务 #{}] 生产类型 pID={} 制造失败！完成 {} 个子任务，失败 {} 个，原因: {}",
                    taskCount, task.getPID(), completed, failed, reason);
            return false;
        } else {
            logger.error("[任务 #{}] 生产类型 pID={} 处于异常状态: {}", taskCount, task.getPID(), status);
            return false;
        }
    }

    public long getDefaultManufacturingMs() {
        return defaultManufacturingMs;
    }

    public TeamMemberPool getMemberPool() {
        return memberPool;
    }

    public int getRequiredPeoplePerTask() {
        return requiredPeoplePerTask;
    }

    public CapacityStrategy getCapacityStrategy() {
        return capacityStrategy;
    }

    public boolean isUseThreadPool() {
        return useThreadPool;
    }

    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    public ExecutorService getThreadPool() {
        return threadPool;
    }
}
