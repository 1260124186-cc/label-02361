// -*- coding: utf-8 -*-
package com.factory.strategy;

import com.factory.model.ProductionTypeRegistry;

public interface ProductionScheduleStrategy {

    int nextPID(ProductionTypeRegistry registry);

    String getName();
}
