// -*- coding: utf-8 -*-
package com.factory.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class TeamMemberPool {

    private static final Logger logger = LoggerFactory.getLogger(TeamMemberPool.class);

    private final List<TeamMember> members;
    private final AtomicInteger alertCount;
    private final CapacityStrategy capacityStrategy;
    private final long waitTimeoutMs;

    public TeamMemberPool(List<TeamMember> members) {
        this(members, CapacityStrategy.WAIT, 0);
    }

    public TeamMemberPool(List<TeamMember> members, CapacityStrategy strategy) {
        this(members, strategy, 0);
    }

    public TeamMemberPool(List<TeamMember> members, CapacityStrategy strategy, long waitTimeoutMs) {
        if (members == null || members.isEmpty()) {
            throw new IllegalArgumentException("members must not be null or empty");
        }
        if (strategy == null) {
            throw new IllegalArgumentException("capacityStrategy must not be null");
        }
        if (waitTimeoutMs < 0) {
            throw new IllegalArgumentException("waitTimeoutMs must be >= 0, got: " + waitTimeoutMs);
        }
        this.members = new CopyOnWriteArrayList<>(members);
        this.capacityStrategy = strategy;
        this.waitTimeoutMs = waitTimeoutMs;
        this.alertCount = new AtomicInteger(0);
        logger.info("TeamMemberPool 初始化完成 - 团队成员 {} 人，产能策略={}，等待超时={}ms",
                members.size(), strategy.getCode(), waitTimeoutMs);
    }

    public static TeamMemberPool createDefault(int size) {
        return createDefault(size, CapacityStrategy.WAIT, 0);
    }

    public static TeamMemberPool createDefault(int size, CapacityStrategy strategy, long waitTimeoutMs) {
        if (size < 1) {
            throw new IllegalArgumentException("size must be >= 1, got: " + size);
        }
        List<TeamMember> members = new ArrayList<>();
        for (int i = 1; i <= size; i++) {
            members.add(new TeamMember(i, "Worker-" + i));
        }
        return new TeamMemberPool(members, strategy, waitTimeoutMs);
    }

    public synchronized List<TeamMember> getAvailableMembersForPID(int pID, int count) throws InterruptedException {
        return getAvailableMembersForPID(pID, count, capacityStrategy, waitTimeoutMs);
    }

    public synchronized List<TeamMember> getAvailableMembersForPID(int pID, int count,
                                                                    CapacityStrategy strategy,
                                                                    long timeoutMs) throws InterruptedException {
        if (count < 1) {
            throw new IllegalArgumentException("count must be >= 1, got: " + count);
        }

        List<TeamMember> result = new ArrayList<>();
        List<TeamMember> selectedMembers = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        while (result.size() < count) {
            List<TeamMember> available = findAvailableMembers(pID);
            available.removeAll(selectedMembers);

            int needed = count - result.size();
            int toAssign = Math.min(needed, available.size());

            for (int i = 0; i < toAssign; i++) {
                TeamMember member = available.get(i);
                result.add(member);
                selectedMembers.add(member);
            }

            if (result.size() >= count) {
                break;
            }

            int currentAvailable = available.size();
            int stillNeeded = count - result.size();

            logger.debug("获取成员不足 - pID={}, 已分配={}/{}, 可用={}, 还需={}, 策略={}",
                    pID, result.size(), count, currentAvailable, stillNeeded, strategy.getCode());

            switch (strategy) {
                case WAIT:
                    if (timeoutMs > 0) {
                        long elapsed = System.currentTimeMillis() - startTime;
                        long remaining = timeoutMs - elapsed;
                        if (remaining <= 0) {
                            logger.warn("【产能不足告警】等待超时 - pID={}, 需要={}人, 仅分配到={}人, 等待 {}ms",
                                    pID, count, result.size(), timeoutMs);
                            alertCount.incrementAndGet();
                            return result;
                        }
                        wait(Math.min(remaining, 100));
                    } else {
                        wait(100);
                    }
                    break;

                case ALERT:
                    logger.warn("【产能不足告警】pID={}, 需要={}人, 仅分配到={}/可用={}人 - {}",
                            pID, count, result.size(), currentAvailable, strategy.getDescription());
                    alertCount.incrementAndGet();
                    return result;

                case FAIL:
                    logger.error("【产能不足失败】pID={}, 需要={}人, 仅可用={}人",
                            pID, count, currentAvailable);
                    alertCount.incrementAndGet();
                    throw new IllegalStateException(
                            String.format("Insufficient capacity for pID=%d: need %d, available %d",
                                    pID, count, currentAvailable));

                default:
                    throw new IllegalStateException("Unknown capacity strategy: " + strategy);
            }

            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Interrupted while waiting for team members");
            }
        }

        return result;
    }

    private List<TeamMember> findAvailableMembers(int pID) {
        List<TeamMember> available = new ArrayList<>();
        for (TeamMember member : members) {
            if (member.isAvailable() && member.isSkilledFor(pID)) {
                available.add(member);
            }
        }
        return available;
    }

    public synchronized int getAvailableCountForPID(int pID) {
        return findAvailableMembers(pID).size();
    }

    public synchronized int getTotalCount() {
        return members.size();
    }

    public synchronized int getBusyCount() {
        int count = 0;
        for (TeamMember member : members) {
            if (!member.isAvailable()) {
                count++;
            }
        }
        return count;
    }

    public int getAlertCount() {
        return alertCount.get();
    }

    public CapacityStrategy getCapacityStrategy() {
        return capacityStrategy;
    }

    public long getWaitTimeoutMs() {
        return waitTimeoutMs;
    }

    public List<TeamMember> getAllMembers() {
        return Collections.unmodifiableList(members);
    }

    public synchronized void addMember(TeamMember member) {
        if (member == null) {
            throw new IllegalArgumentException("member must not be null");
        }
        members.add(member);
    }

    public synchronized void resetAlertCount() {
        alertCount.set(0);
    }

    public synchronized void logStatus() {
        int total = getTotalCount();
        int busy = getBusyCount();
        int available = total - busy;
        logger.info("==================== 团队成员状态 ====================");
        logger.info("  总人数: {}, 忙碌: {}, 可用: {}, 产能告警: {}",
                total, busy, available, alertCount.get());
        for (TeamMember member : members) {
            String skillInfo = member.getSkilledPIDs().isEmpty()
                    ? "全能"
                    : "技能pID: " + member.getSkilledPIDs();
            logger.info("  成员 #{} ({}): {} - {}",
                    member.getId(), member.getName(),
                    member.getStatus(), skillInfo);
        }
        logger.info("======================================================");
    }
}
