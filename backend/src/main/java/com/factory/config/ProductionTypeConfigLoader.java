// -*- coding: utf-8 -*-
package com.factory.config;

import com.factory.model.ProductionType;
import com.factory.model.ProductionTypeRegistry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class ProductionTypeConfigLoader {

    private static final Logger logger = LoggerFactory.getLogger(ProductionTypeConfigLoader.class);

    private static final String DEFAULT_JSON_RESOURCE = "production-types.json";
    private static final String DEFAULT_YAML_RESOURCE = "production-types.yaml";
    private static final String ENV_CONFIG_PATH = "PRODUCTION_TYPES_CONFIG";
    private static final String ENV_PRODUCTION_TYPES = "PRODUCTION_TYPES";

    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final AppConfig appConfig;

    public static class LoadResult {
        public final ProductionTypeRegistry registry;
        public final String source;
        public final ProductionStrategy strategy;

        public LoadResult(ProductionTypeRegistry registry, String source, ProductionStrategy strategy) {
            this.registry = registry;
            this.source = source;
            this.strategy = strategy;
        }
    }

    public ProductionTypeConfigLoader() {
        this(AppConfig.load(new String[0]));
    }

    public ProductionTypeConfigLoader(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    public LoadResult load(String[] args) {
        ConfigResolution resolution = resolveConfig(args);
        logger.info("生产类型配置来源: {}", resolution.source);

        ProductionTypeRegistry registry;
        if (resolution.registry != null) {
            registry = resolution.registry;
        } else if (resolution.n != null) {
            registry = ProductionTypeRegistry.createDefault(resolution.n);
        } else {
            registry = ProductionTypeRegistry.createDefault(appConfig.getProductionTypes());
        }

        ProductionStrategy strategy = resolution.strategy != null
                ? resolution.strategy
                : ProductionStrategy.sequential();

        return new LoadResult(registry, resolution.source, strategy);
    }

    private static class ConfigResolution {
        ProductionTypeRegistry registry;
        Integer n;
        ProductionStrategy strategy;
        String source;
    }

    private ConfigResolution resolveConfig(String[] args) {
        ConfigResolution result = new ConfigResolution();

        if (args.length >= 2) {
            ProductionTypeRegistry fromFile = tryLoadFromFile(args[1]);
            if (fromFile != null) {
                result.registry = fromFile;
                result.source = "命令行参数指定配置文件: " + args[1];
                result.strategy = resolveStrategy(args);
                return result;
            }
        }

        String envConfigPath = System.getenv(ENV_CONFIG_PATH);
        if (envConfigPath != null && !envConfigPath.isBlank()) {
            ProductionTypeRegistry fromFile = tryLoadFromFile(envConfigPath);
            if (fromFile != null) {
                result.registry = fromFile;
                result.source = "环境变量 " + ENV_CONFIG_PATH + "=" + envConfigPath;
                result.strategy = resolveStrategy(args);
                return result;
            }
        }

        ProductionTypeRegistry fromClasspath = tryLoadFromClasspath();
        if (fromClasspath != null) {
            result.registry = fromClasspath;
            result.source = "classpath 配置文件 (production-types.json/yaml)";
            result.strategy = resolveStrategy(args);
            return result;
        }

        if (args.length >= 1) {
            try {
                int n = Integer.parseInt(args[0]);
                if (n >= 1) {
                    result.n = n;
                    result.source = "命令行参数 n=" + n;
                    result.strategy = resolveStrategy(args);
                    return result;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        String envN = System.getenv(ENV_PRODUCTION_TYPES);
        if (envN != null && !envN.isBlank()) {
            try {
                int n = Integer.parseInt(envN);
                if (n >= 1) {
                    result.n = n;
                    result.source = "环境变量 " + ENV_PRODUCTION_TYPES + "=" + n;
                    result.strategy = resolveStrategy(args);
                    return result;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        result.n = appConfig.getProductionTypes();
        result.source = String.format("配置默认值 production.types=%d (来源: %s)",
                appConfig.getProductionTypes(),
                appConfig.getConfigSource(AppConfig.KEY_PRODUCTION_TYPES));
        result.strategy = resolveStrategy(args);
        return result;
    }

    private ProductionStrategy resolveStrategy(String[] args) {
        if (args.length >= 3) {
            String strategyArg = args[2].toLowerCase();
            switch (strategyArg) {
                case "priority":
                    return ProductionStrategy.priorityFirst();
                case "weekday":
                    return ProductionStrategy.weekdaySequential();
                case "priority-weekday":
                    return ProductionStrategy.priorityFirstWeekday();
                case "sequential":
                default:
                    return ProductionStrategy.sequential();
            }
        }
        return ProductionStrategy.sequential();
    }

    private ProductionTypeRegistry tryLoadFromFile(String path) {
        try {
            File file = new File(path);
            if (!file.exists() || !file.isFile()) {
                logger.warn("配置文件不存在: {}", path);
                return null;
            }
            List<ProductionType> types;
            if (path.endsWith(".yaml") || path.endsWith(".yml")) {
                types = yamlMapper.readValue(file, new TypeReference<List<ProductionType>>() {});
            } else {
                types = jsonMapper.readValue(file, new TypeReference<List<ProductionType>>() {});
            }
            logger.info("成功从文件加载 {} 个生产类型: {}", types.size(), path);
            return new ProductionTypeRegistry(types);
        } catch (IOException e) {
            logger.error("从文件加载生产类型配置失败: {}", path, e);
            return null;
        }
    }

    private ProductionTypeRegistry tryLoadFromClasspath() {
        InputStream jsonStream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(DEFAULT_JSON_RESOURCE);
        if (jsonStream != null) {
            try {
                List<ProductionType> types = jsonMapper.readValue(
                        jsonStream, new TypeReference<List<ProductionType>>() {});
                logger.info("成功从 classpath 加载 {} 个生产类型: {}", types.size(), DEFAULT_JSON_RESOURCE);
                return new ProductionTypeRegistry(types);
            } catch (IOException e) {
                logger.error("解析 classpath JSON 配置失败", e);
            }
        }

        InputStream yamlStream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(DEFAULT_YAML_RESOURCE);
        if (yamlStream != null) {
            try {
                List<ProductionType> types = yamlMapper.readValue(
                        yamlStream, new TypeReference<List<ProductionType>>() {});
                logger.info("成功从 classpath 加载 {} 个生产类型: {}", types.size(), DEFAULT_YAML_RESOURCE);
                return new ProductionTypeRegistry(types);
            } catch (IOException e) {
                logger.error("解析 classpath YAML 配置失败", e);
            }
        }

        return null;
    }
}
