// -*- coding: utf-8 -*-
package com.factory.process;

import com.factory.model.ManufacturingTask;
import com.factory.model.TeamMember;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkerThread implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(WorkerThread.class);

    private final TeamMember member;
    private final ManufacturingTask task;
    private final long workDurationMs;

    public WorkerThread(TeamMember member, ManufacturingTask task) {
        if (member == null) {
            throw new IllegalArgumentException("TeamMember must not be null");
        }
        if (task == null) {
            throw new IllegalArgumentException("ManufacturingTask must not be null");
        }
        this.member = member;
        this.task = task;
        this.workDurationMs = task.getManufacturingDurationMs();
    }

    public WorkerThread(TeamMember member, ManufacturingTask task, long workDurationMs) {
        if (member == null) {
            throw new IllegalArgumentException("TeamMember must not be null");
        }
        if (task == null) {
            throw new IllegalArgumentException("ManufacturingTask must not be null");
        }
        if (workDurationMs < 0) {
            throw new IllegalArgumentException("workDurationMs must be >= 0, got: " + workDurationMs);
        }
        this.member = member;
        this.task = task;
        this.workDurationMs = workDurationMs;
    }

    @Override
    public void run() {
        String threadName = Thread.currentThread().getName();
        logger.info("[{}] 成员 {}({}) 开始执行任务 #{} (pID={})，预计耗时 {}ms",
                threadName, member.getId(), member.getName(),
                task.getTaskId(), task.getPID(), workDurationMs);

        try {
            Thread.sleep(workDurationMs);

            task.markSubtaskComplete();
            logger.info("[{}] 成员 {}({}) 完成任务 #{} (pID={}) 的子任务，已完成 {}/{}",
                    threadName, member.getId(), member.getName(),
                    task.getTaskId(), task.getPID(),
                    task.getCompletedSubtasks(), task.getRequiredPeople());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            String reason = "Worker thread was interrupted";
            task.markSubtaskFailed(reason);
            logger.warn("[{}] 成员 {}({}) 执行任务 #{} (pID={}) 时被中断: {}",
                    threadName, member.getId(), member.getName(),
                    task.getTaskId(), task.getPID(), reason);
        } catch (Exception e) {
            task.markSubtaskFailed(e.getMessage());
            logger.error("[{}] 成员 {}({}) 执行任务 #{} (pID={}) 时发生异常: {}",
                    threadName, member.getId(), member.getName(),
                    task.getTaskId(), task.getPID(), e.getMessage(), e);
        }
    }

    public TeamMember getMember() {
        return member;
    }

    public ManufacturingTask getTask() {
        return task;
    }

    public long getWorkDurationMs() {
        return workDurationMs;
    }
}
