// -*- coding: utf-8 -*-
package com.factory.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class ManufacturingTask {

    public enum Status {
        PENDING,
        IN_PROGRESS,
        DONE,
        FAILED
    }

    private final int taskId;
    private final int pID;
    private final int requiredPeople;
    private final long manufacturingDurationMs;
    private final Instant createdAt;
    private volatile Instant startTime;
    private volatile Instant endTime;
    private volatile Status status;
    private final List<TeamMember> assignedMembers;
    private final CountDownLatch completionLatch;
    private final AtomicInteger completedSubtasks;
    private final AtomicInteger failedSubtasks;
    private volatile String failureReason;

    public ManufacturingTask(int taskId, int pID, int requiredPeople, long manufacturingDurationMs) {
        if (taskId < 1) {
            throw new IllegalArgumentException("taskId must be >= 1, got: " + taskId);
        }
        if (pID < 1) {
            throw new IllegalArgumentException("pID must be >= 1, got: " + pID);
        }
        if (requiredPeople < 1) {
            throw new IllegalArgumentException("requiredPeople must be >= 1, got: " + requiredPeople);
        }
        if (manufacturingDurationMs < 0) {
            throw new IllegalArgumentException("manufacturingDurationMs must be >= 0, got: " + manufacturingDurationMs);
        }
        this.taskId = taskId;
        this.pID = pID;
        this.requiredPeople = requiredPeople;
        this.manufacturingDurationMs = manufacturingDurationMs;
        this.createdAt = Instant.now();
        this.status = Status.PENDING;
        this.assignedMembers = new ArrayList<>();
        this.completionLatch = new CountDownLatch(requiredPeople);
        this.completedSubtasks = new AtomicInteger(0);
        this.failedSubtasks = new AtomicInteger(0);
    }

    public synchronized void start() {
        if (status != Status.PENDING) {
            throw new IllegalStateException("Cannot start task that is not PENDING, current status: " + status);
        }
        if (assignedMembers.size() < requiredPeople) {
            throw new IllegalStateException("Not enough members assigned: " + assignedMembers.size() + "/" + requiredPeople);
        }
        this.status = Status.IN_PROGRESS;
        this.startTime = Instant.now();
    }

    public synchronized void assignMember(TeamMember member) {
        if (status != Status.PENDING) {
            throw new IllegalStateException("Cannot assign members after task has started");
        }
        if (assignedMembers.size() >= requiredPeople) {
            throw new IllegalStateException("Task already has required number of members");
        }
        if (!member.isSkilledFor(pID)) {
            throw new IllegalArgumentException("TeamMember " + member.getId() + " is not skilled for pID=" + pID);
        }
        assignedMembers.add(member);
        member.assignTask(this);
    }

    public void markSubtaskComplete() {
        completedSubtasks.incrementAndGet();
        completionLatch.countDown();
        checkTaskCompletion();
    }

    public void markSubtaskFailed(String reason) {
        failedSubtasks.incrementAndGet();
        completionLatch.countDown();
        this.failureReason = reason;
        checkTaskCompletion();
    }

    private synchronized void checkTaskCompletion() {
        if (status != Status.IN_PROGRESS) {
            return;
        }
        int completed = completedSubtasks.get();
        int failed = failedSubtasks.get();
        int total = completed + failed;

        if (total >= requiredPeople) {
            this.endTime = Instant.now();
            if (failed > 0) {
                this.status = Status.FAILED;
            } else {
                this.status = Status.DONE;
            }
            assignedMembers.forEach(TeamMember::completeTask);
        }
    }

    public void awaitCompletion() throws InterruptedException {
        completionLatch.await();
    }

    public boolean awaitCompletion(long timeoutMs) throws InterruptedException {
        return completionLatch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public int getTaskId() {
        return taskId;
    }

    public int getPID() {
        return pID;
    }

    public int getRequiredPeople() {
        return requiredPeople;
    }

    public long getManufacturingDurationMs() {
        return manufacturingDurationMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public synchronized Instant getStartTime() {
        return startTime;
    }

    public synchronized Instant getEndTime() {
        return endTime;
    }

    public synchronized Status getStatus() {
        return status;
    }

    public synchronized List<TeamMember> getAssignedMembers() {
        return new ArrayList<>(assignedMembers);
    }

    public int getCompletedSubtasks() {
        return completedSubtasks.get();
    }

    public int getFailedSubtasks() {
        return failedSubtasks.get();
    }

    public String getFailureReason() {
        return failureReason;
    }

    public synchronized long getElapsedDurationMs() {
        if (startTime == null) {
            return 0;
        }
        Instant end = endTime != null ? endTime : Instant.now();
        return java.time.Duration.between(startTime, end).toMillis();
    }

    @Override
    public String toString() {
        return "ManufacturingTask{" +
                "taskId=" + taskId +
                ", pID=" + pID +
                ", requiredPeople=" + requiredPeople +
                ", status=" + status +
                ", durationMs=" + manufacturingDurationMs +
                '}';
    }
}
