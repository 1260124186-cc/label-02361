// -*- coding: utf-8 -*-
package com.factory.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class ProductionTypeRegistry {

    private static final Logger logger = LoggerFactory.getLogger(ProductionTypeRegistry.class);

    private final Map<Integer, ProductionType> typesById;
    private final int maxId;

    public ProductionTypeRegistry(List<ProductionType> productionTypes) {
        if (productionTypes == null || productionTypes.isEmpty()) {
            throw new IllegalArgumentException("productionTypes must not be null or empty");
        }
        Map<Integer, ProductionType> map = new LinkedHashMap<>();
        int max = 0;
        for (ProductionType pt : productionTypes) {
            if (map.containsKey(pt.getId())) {
                throw new IllegalArgumentException("Duplicate ProductionType id: " + pt.getId());
            }
            map.put(pt.getId(), pt);
            if (pt.getId() > max) {
                max = pt.getId();
            }
        }
        this.typesById = Collections.unmodifiableMap(map);
        this.maxId = max;
        logger.info("ProductionTypeRegistry 初始化完成 - 注册 {} 个生产类型，最大 pID={}",
                typesById.size(), maxId);
    }

    public static ProductionTypeRegistry createDefault(int n) {
        if (n < 1) {
            throw new IllegalArgumentException("n must be >= 1, got: " + n);
        }
        List<ProductionType> types = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            types.add(new ProductionType(
                    i,
                    "Type-" + i,
                    "生产类型 " + i,
                    1500L,
                    n - i + 1,
                    true
            ));
        }
        return new ProductionTypeRegistry(types);
    }

    public Optional<ProductionType> findById(int id) {
        return Optional.ofNullable(typesById.get(id));
    }

    public ProductionType getById(int id) {
        ProductionType pt = typesById.get(id);
        if (pt == null) {
            throw new NoSuchElementException("No ProductionType found for id: " + id);
        }
        return pt;
    }

    public boolean isValidId(int id) {
        return typesById.containsKey(id);
    }

    public boolean isEnabled(int id) {
        ProductionType pt = typesById.get(id);
        return pt != null && pt.isEnabled();
    }

    public int getMaxId() {
        return maxId;
    }

    public int size() {
        return typesById.size();
    }

    public List<ProductionType> getAll() {
        return new ArrayList<>(typesById.values());
    }

    public List<ProductionType> getEnabled() {
        return typesById.values().stream()
                .filter(ProductionType::isEnabled)
                .collect(Collectors.toList());
    }

    public List<ProductionType> getEnabledSortedByPriorityDesc() {
        return typesById.values().stream()
                .filter(ProductionType::isEnabled)
                .sorted(Comparator.comparingInt(ProductionType::getPriority).reversed())
                .collect(Collectors.toList());
    }

    public void logAllTypes() {
        logger.info("==================== 生产类型清单 ====================");
        for (ProductionType pt : typesById.values()) {
            logger.info("  pID={} | 名称={} | 优先级={} | 启用={} | 制造时长={}ms | 描述={}",
                    pt.getId(),
                    pt.getName(),
                    pt.getPriority(),
                    pt.isEnabled(),
                    pt.getManufacturingDurationMs(),
                    pt.getDescription());
        }
        logger.info("======================================================");
    }
}
