// -*- coding: utf-8 -*-
package com.factory.strategy;

import com.factory.model.ProductionType;
import com.factory.model.ProductionTypeRegistry;

import java.util.List;
import java.util.NoSuchElementException;

public class RoundRobinStrategy implements ProductionScheduleStrategy {

    private int index = 0;

    @Override
    public int nextPID(ProductionTypeRegistry registry) {
        List<ProductionType> enabled = registry.getEnabled();
        if (enabled.isEmpty()) {
            throw new NoSuchElementException("No enabled production types available");
        }
        List<Integer> ids = enabled.stream()
                .map(ProductionType::getId)
                .sorted()
                .toList();
        if (index >= ids.size()) {
            index = 0;
        }
        int pid = ids.get(index);
        index = (index + 1) % ids.size();
        return pid;
    }

    @Override
    public String getName() {
        return "round-robin";
    }
}
