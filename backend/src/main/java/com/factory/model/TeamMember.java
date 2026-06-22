// -*- coding: utf-8 -*-
package com.factory.model;

import java.util.HashSet;
import java.util.Set;

public class TeamMember {

    public enum Status {
        AVAILABLE,
        BUSY
    }

    private final int id;
    private final String name;
    private final Set<Integer> skilledPIDs;
    private volatile Status status;
    private ManufacturingTask currentTask;

    public TeamMember(int id, String name) {
        this(id, name, new HashSet<>());
    }

    public TeamMember(int id, String name, Set<Integer> skilledPIDs) {
        if (id < 1) {
            throw new IllegalArgumentException("TeamMember id must be >= 1, got: " + id);
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("TeamMember name must not be null or blank");
        }
        this.id = id;
        this.name = name;
        this.skilledPIDs = skilledPIDs != null ? new HashSet<>(skilledPIDs) : new HashSet<>();
        this.status = Status.AVAILABLE;
        this.currentTask = null;
    }

    public synchronized boolean isSkilledFor(int pID) {
        return skilledPIDs.isEmpty() || skilledPIDs.contains(pID);
    }

    public synchronized void addSkill(int pID) {
        skilledPIDs.add(pID);
    }

    public synchronized void removeSkill(int pID) {
        skilledPIDs.remove(pID);
    }

    public synchronized Set<Integer> getSkilledPIDs() {
        return new HashSet<>(skilledPIDs);
    }

    public synchronized Status getStatus() {
        return status;
    }

    public synchronized boolean isAvailable() {
        return status == Status.AVAILABLE;
    }

    public synchronized void assignTask(ManufacturingTask task) {
        if (status != Status.AVAILABLE) {
            throw new IllegalStateException("TeamMember " + id + " is not available");
        }
        this.status = Status.BUSY;
        this.currentTask = task;
    }

    public synchronized void completeTask() {
        this.status = Status.AVAILABLE;
        this.currentTask = null;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public synchronized ManufacturingTask getCurrentTask() {
        return currentTask;
    }

    @Override
    public String toString() {
        return "TeamMember{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", status=" + status +
                ", skilledPIDs=" + skilledPIDs +
                '}';
    }
}
