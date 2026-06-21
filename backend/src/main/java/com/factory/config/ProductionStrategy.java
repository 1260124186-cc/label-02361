// -*- coding: utf-8 -*-
package com.factory.config;

import com.factory.model.ProductionType;
import com.factory.model.ProductionTypeRegistry;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public interface ProductionStrategy {

    Iterator<Integer> createIterator(ProductionTypeRegistry registry);

    String getName();

    static ProductionStrategy sequential() {
        return new SequentialStrategy();
    }

    static ProductionStrategy priorityFirst() {
        return new PriorityFirstStrategy();
    }

    static ProductionStrategy weekdaySequential() {
        return new WeekdaySequentialStrategy();
    }

    static ProductionStrategy priorityFirstWeekday() {
        return new PriorityFirstWeekdayStrategy();
    }

    class SequentialStrategy implements ProductionStrategy {
        @Override
        public Iterator<Integer> createIterator(ProductionTypeRegistry registry) {
            List<ProductionType> enabled = registry.getEnabled();
            if (enabled.isEmpty()) {
                throw new NoSuchElementException("No enabled production types available");
            }
            List<Integer> ids = enabled.stream()
                    .map(ProductionType::getId)
                    .sorted()
                    .toList();
            return new CyclingIterator(ids);
        }

        @Override
        public String getName() {
            return "sequential";
        }
    }

    class PriorityFirstStrategy implements ProductionStrategy {
        @Override
        public Iterator<Integer> createIterator(ProductionTypeRegistry registry) {
            List<ProductionType> sorted = registry.getEnabledSortedByPriorityDesc();
            if (sorted.isEmpty()) {
                throw new NoSuchElementException("No enabled production types available");
            }
            List<Integer> ids = sorted.stream()
                    .map(ProductionType::getId)
                    .toList();
            return new CyclingIterator(ids);
        }

        @Override
        public String getName() {
            return "priority";
        }
    }

    class WeekdaySequentialStrategy implements ProductionStrategy {
        @Override
        public Iterator<Integer> createIterator(ProductionTypeRegistry registry) {
            List<ProductionType> enabled = registry.getEnabled();
            if (enabled.isEmpty()) {
                throw new NoSuchElementException("No enabled production types available");
            }
            List<Integer> ids = enabled.stream()
                    .map(ProductionType::getId)
                    .sorted()
                    .toList();
            return new CyclingIterator(ids) {
                @Override
                public boolean hasNext() {
                    DayOfWeek dow = LocalDate.now().getDayOfWeek();
                    return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
                }
            };
        }

        @Override
        public String getName() {
            return "weekday";
        }
    }

    class PriorityFirstWeekdayStrategy implements ProductionStrategy {
        @Override
        public Iterator<Integer> createIterator(ProductionTypeRegistry registry) {
            List<ProductionType> sorted = registry.getEnabledSortedByPriorityDesc();
            if (sorted.isEmpty()) {
                throw new NoSuchElementException("No enabled production types available");
            }
            List<Integer> ids = sorted.stream()
                    .map(ProductionType::getId)
                    .toList();
            return new CyclingIterator(ids) {
                @Override
                public boolean hasNext() {
                    DayOfWeek dow = LocalDate.now().getDayOfWeek();
                    return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
                }
            };
        }

        @Override
        public String getName() {
            return "priority-weekday";
        }
    }

    class CyclingIterator implements Iterator<Integer> {
        private final List<Integer> ids;
        private int index = 0;

        public CyclingIterator(List<Integer> ids) {
            this.ids = ids;
        }

        @Override
        public boolean hasNext() {
            return true;
        }

        @Override
        public Integer next() {
            if (ids.isEmpty()) {
                throw new NoSuchElementException();
            }
            Integer id = ids.get(index);
            index = (index + 1) % ids.size();
            return id;
        }
    }
}
