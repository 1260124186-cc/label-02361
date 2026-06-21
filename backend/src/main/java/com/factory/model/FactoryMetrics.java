// -*- coding: utf-8 -*-
package com.factory.model;

import java.util.Objects;

public class FactoryMetrics {

    private final double averageWaitDurationMs;
    private final double throughputTasksPerMinute;
    private final double managerIdleRate;
    private final double teamLeaderIdleRate;

    public FactoryMetrics(
            double averageWaitDurationMs,
            double throughputTasksPerMinute,
            double managerIdleRate,
            double teamLeaderIdleRate) {
        this.averageWaitDurationMs = averageWaitDurationMs;
        this.throughputTasksPerMinute = throughputTasksPerMinute;
        this.managerIdleRate = managerIdleRate;
        this.teamLeaderIdleRate = teamLeaderIdleRate;
    }

    public double getAverageWaitDurationMs() {
        return averageWaitDurationMs;
    }

    public double getThroughputTasksPerMinute() {
        return throughputTasksPerMinute;
    }

    public double getManagerIdleRate() {
        return managerIdleRate;
    }

    public double getTeamLeaderIdleRate() {
        return teamLeaderIdleRate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FactoryMetrics that)) return false;
        return Double.compare(that.averageWaitDurationMs, averageWaitDurationMs) == 0
                && Double.compare(that.throughputTasksPerMinute, throughputTasksPerMinute) == 0
                && Double.compare(that.managerIdleRate, managerIdleRate) == 0
                && Double.compare(that.teamLeaderIdleRate, teamLeaderIdleRate) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(averageWaitDurationMs, throughputTasksPerMinute, managerIdleRate, teamLeaderIdleRate);
    }

    @Override
    public String toString() {
        return "FactoryMetrics{" +
                "averageWaitDurationMs=" + String.format("%.2f", averageWaitDurationMs) +
                ", throughputTasksPerMinute=" + String.format("%.2f", throughputTasksPerMinute) +
                ", managerIdleRate=" + String.format("%.2f%%", managerIdleRate * 100) +
                ", teamLeaderIdleRate=" + String.format("%.2f%%", teamLeaderIdleRate * 100) +
                '}';
    }
}
