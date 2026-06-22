// -*- coding: utf-8 -*-
package com.factory.strategy;

import com.factory.model.ProductionType;
import com.factory.model.ProductionTypeRegistry;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;

public class RandomStrategy implements ProductionScheduleStrategy {

    private final Random random;

    public RandomStrategy() {
        this(new Random());
    }

    public RandomStrategy(Random random) {
        if (random == null) {
            throw new IllegalArgumentException("Random must not be null");
        }
        this.random = random;
    }

    @Override
    public int nextPID(ProductionTypeRegistry registry) {
        List<ProductionType> enabled = registry.getEnabled();
        if (enabled.isEmpty()) {
            throw new NoSuchElementException("No enabled production types available");
        }
        List<Integer> ids = enabled.stream()
                .map(ProductionType::getId)
                .toList();
        return ids.get(random.nextInt(ids.size()));
    }

    @Override
    public String getName() {
        return "random";
    }
}
