// -*- coding: utf-8 -*-
package com.factory.strategy;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

public class ProductionScheduleStrategyFactory {

    private static final Map<String, Supplier<ProductionScheduleStrategy>> STRATEGIES = new HashMap<>();

    static {
        register("round-robin", RoundRobinStrategy::new);
        register("random", RandomStrategy::new);
        register("priority", PriorityStrategy::new);
        register("demand-driven", DemandDrivenStrategy::new);
    }

    private static void register(String name, Supplier<ProductionScheduleStrategy> supplier) {
        STRATEGIES.put(name, supplier);
    }

    public static ProductionScheduleStrategy create(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Strategy name must not be null or blank");
        }
        Supplier<ProductionScheduleStrategy> supplier = STRATEGIES.get(name.toLowerCase());
        if (supplier == null) {
            throw new IllegalArgumentException("Unknown strategy: " + name
                    + ". Available strategies: " + STRATEGIES.keySet());
        }
        return supplier.get();
    }

    public static ProductionScheduleStrategy create(String name, Random random) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Strategy name must not be null or blank");
        }
        return switch (name.toLowerCase()) {
            case "round-robin" -> new RoundRobinStrategy();
            case "random" -> new RandomStrategy(random);
            case "priority" -> new PriorityStrategy(random);
            case "demand-driven" -> new DemandDrivenStrategy();
            default -> throw new IllegalArgumentException("Unknown strategy: " + name
                    + ". Available strategies: " + STRATEGIES.keySet());
        };
    }

    public static boolean isValidStrategy(String name) {
        return name != null && STRATEGIES.containsKey(name.toLowerCase());
    }

    public static java.util.Set<String> getAvailableStrategies() {
        return java.util.Collections.unmodifiableSet(STRATEGIES.keySet());
    }
}
