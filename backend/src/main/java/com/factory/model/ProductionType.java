// -*- coding: utf-8 -*-
package com.factory.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class ProductionType {

    private final int id;
    private final String name;
    private final String description;
    private final long manufacturingDurationMs;
    private final int priority;
    private final boolean enabled;

    @JsonCreator
    public ProductionType(
            @JsonProperty("id") int id,
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("manufacturingDurationMs") long manufacturingDurationMs,
            @JsonProperty("priority") int priority,
            @JsonProperty("enabled") boolean enabled) {
        if (id < 1) {
            throw new IllegalArgumentException("ProductionType id must be >= 1, got: " + id);
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("ProductionType name must not be null or blank");
        }
        if (manufacturingDurationMs < 0) {
            throw new IllegalArgumentException("manufacturingDurationMs must be >= 0, got: " + manufacturingDurationMs);
        }
        this.id = id;
        this.name = name;
        this.description = description != null ? description : "";
        this.manufacturingDurationMs = manufacturingDurationMs;
        this.priority = priority;
        this.enabled = enabled;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public long getManufacturingDurationMs() {
        return manufacturingDurationMs;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProductionType that)) return false;
        return id == that.id
                && manufacturingDurationMs == that.manufacturingDurationMs
                && priority == that.priority
                && enabled == that.enabled
                && Objects.equals(name, that.name)
                && Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, description, manufacturingDurationMs, priority, enabled);
    }

    @Override
    public String toString() {
        return "ProductionType{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", manufacturingDurationMs=" + manufacturingDurationMs +
                ", priority=" + priority +
                ", enabled=" + enabled +
                '}';
    }
}
