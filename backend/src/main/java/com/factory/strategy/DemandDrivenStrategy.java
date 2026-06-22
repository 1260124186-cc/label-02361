// -*- coding: utf-8 -*-
package com.factory.strategy;

import com.factory.model.ProductionType;
import com.factory.model.ProductionTypeRegistry;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

public class DemandDrivenStrategy implements ProductionScheduleStrategy {

    private final Map<Integer, Integer> completionCounts = new HashMap<>();

    @Override
    public int nextPID(ProductionTypeRegistry registry) {
        List<ProductionType> enabled = registry.getEnabled();
        if (enabled.isEmpty()) {
            throw new NoSuchElementException("No enabled production types available");
        }

        for (ProductionType pt : enabled) {
            completionCounts.putIfAbsent(pt.getId(), 0);
        }

        int selectedId = enabled.stream()
                .map(ProductionType::getId)
                .min(Comparator.<Integer>comparingInt(completionCounts::get)
                        .thenComparingInt(id -> id))
                .orElseThrow(() -> new NoSuchElementException("No enabled production types available"));

        completionCounts.put(selectedId, completionCounts.get(selectedId) + 1);

        return selectedId;
    }

    public int getCompletionCount(int pID) {
        return completionCounts.getOrDefault(pID, 0);
    }

    public void reset() {
        completionCounts.clear();
    }

    @Override
    public String getName() {
        return "demand-driven";
    }
}
