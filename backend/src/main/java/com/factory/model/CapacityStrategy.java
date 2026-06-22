// -*- coding: utf-8 -*-
package com.factory.model;

public enum CapacityStrategy {

    WAIT("wait", "等待可用成员，直到满足需求"),
    ALERT("alert", "记录产能不足告警，继续执行（可能使用部分成员）"),
    FAIL("fail", "产能不足时任务失败");

    private final String code;
    private final String description;

    CapacityStrategy(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static CapacityStrategy fromCode(String code) {
        if (code == null || code.isBlank()) {
            return WAIT;
        }
        for (CapacityStrategy strategy : values()) {
            if (strategy.code.equalsIgnoreCase(code.trim())) {
                return strategy;
            }
        }
        throw new IllegalArgumentException("Unknown capacity strategy: " + code);
    }
}
