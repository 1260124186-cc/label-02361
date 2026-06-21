// -*- coding: utf-8 -*-
package com.factory.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

public class AppConfig {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(AppConfig.class);

    public static final String KEY_MANAGER_INTERVAL_MS = "manager.interval.ms";
    public static final String KEY_TEAM_LEADER_MANUFACTURING_MS = "team-leader.manufacturing.ms";
    public static final String KEY_SHUTDOWN_JOIN_TIMEOUT_MS = "shutdown.join.timeout.ms";
    public static final String KEY_PRODUCTION_TYPES = "production.types";
    public static final String KEY_LOG_LEVEL = "log.level";

    public static final long DEFAULT_MANAGER_INTERVAL_MS = 1000L;
    public static final long DEFAULT_TEAM_LEADER_MANUFACTURING_MS = 1500L;
    public static final long DEFAULT_SHUTDOWN_JOIN_TIMEOUT_MS = 3000L;
    public static final int DEFAULT_PRODUCTION_TYPES = 5;
    public static final String DEFAULT_LOG_LEVEL = "INFO";

    public static final String ENV_MANAGER_INTERVAL_MS = "MANAGER_INTERVAL_MS";
    public static final String ENV_TEAM_LEADER_MANUFACTURING_MS = "TEAM_LEADER_MANUFACTURING_MS";
    public static final String ENV_SHUTDOWN_JOIN_TIMEOUT_MS = "SHUTDOWN_JOIN_TIMEOUT_MS";
    public static final String ENV_PRODUCTION_TYPES = "PRODUCTION_TYPES";
    public static final String ENV_LOG_LEVEL = "LOG_LEVEL";

    public static final String PROPERTIES_FILE = "application.properties";

    private final Map<String, ConfigValue<?>> configValues = new LinkedHashMap<>();
    private final Map<String, String> propertyFileValues = new HashMap<>();
    private final String[] commandLineArgs;

    private AppConfig(String[] args) {
        this.commandLineArgs = args != null ? args.clone() : new String[0];
        loadPropertyFile();
        initializeConfigValues();
    }

    public static AppConfig load(String[] args) {
        AppConfig config = new AppConfig(args);
        config.applyLogLevel();
        return config;
    }

    private void loadPropertyFile() {
        try (InputStream is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(PROPERTIES_FILE)) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                for (String name : props.stringPropertyNames()) {
                    propertyFileValues.put(name, props.getProperty(name));
                }
                logger.debug("已加载配置文件: {}", PROPERTIES_FILE);
            } else {
                logger.debug("未找到配置文件: {}，将使用默认值", PROPERTIES_FILE);
            }
        } catch (IOException e) {
            logger.warn("加载配置文件失败: {}", PROPERTIES_FILE, e);
        }
    }

    private void initializeConfigValues() {
        configValues.put(KEY_MANAGER_INTERVAL_MS,
                ConfigValue.longValue(KEY_MANAGER_INTERVAL_MS,
                        resolveLong(KEY_MANAGER_INTERVAL_MS, ENV_MANAGER_INTERVAL_MS, DEFAULT_MANAGER_INTERVAL_MS),
                        DEFAULT_MANAGER_INTERVAL_MS));

        configValues.put(KEY_TEAM_LEADER_MANUFACTURING_MS,
                ConfigValue.longValue(KEY_TEAM_LEADER_MANUFACTURING_MS,
                        resolveLong(KEY_TEAM_LEADER_MANUFACTURING_MS, ENV_TEAM_LEADER_MANUFACTURING_MS, DEFAULT_TEAM_LEADER_MANUFACTURING_MS),
                        DEFAULT_TEAM_LEADER_MANUFACTURING_MS));

        configValues.put(KEY_SHUTDOWN_JOIN_TIMEOUT_MS,
                ConfigValue.longValue(KEY_SHUTDOWN_JOIN_TIMEOUT_MS,
                        resolveLong(KEY_SHUTDOWN_JOIN_TIMEOUT_MS, ENV_SHUTDOWN_JOIN_TIMEOUT_MS, DEFAULT_SHUTDOWN_JOIN_TIMEOUT_MS),
                        DEFAULT_SHUTDOWN_JOIN_TIMEOUT_MS));

        configValues.put(KEY_PRODUCTION_TYPES,
                ConfigValue.intValue(KEY_PRODUCTION_TYPES,
                        resolveInt(KEY_PRODUCTION_TYPES, ENV_PRODUCTION_TYPES, DEFAULT_PRODUCTION_TYPES),
                        DEFAULT_PRODUCTION_TYPES));

        configValues.put(KEY_LOG_LEVEL,
                ConfigValue.stringValue(KEY_LOG_LEVEL,
                        resolveString(KEY_LOG_LEVEL, ENV_LOG_LEVEL, DEFAULT_LOG_LEVEL),
                        DEFAULT_LOG_LEVEL));
    }

    private String resolveString(String propertyKey, String envKey, String defaultValue) {
        String cmdValue = parseCommandLineArg(propertyKey);
        if (cmdValue != null) {
            return cmdValue;
        }

        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }

        String propValue = propertyFileValues.get(propertyKey);
        if (propValue != null && !propValue.isBlank()) {
            return propValue;
        }

        return defaultValue;
    }

    private long resolveLong(String propertyKey, String envKey, long defaultValue) {
        String value = resolveString(propertyKey, envKey, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            logger.warn("配置项 {} 值 '{}' 不是有效的 long 类型，使用默认值 {}", propertyKey, value, defaultValue);
            return defaultValue;
        }
    }

    private int resolveInt(String propertyKey, String envKey, int defaultValue) {
        String value = resolveString(propertyKey, envKey, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            logger.warn("配置项 {} 值 '{}' 不是有效的 int 类型，使用默认值 {}", propertyKey, value, defaultValue);
            return defaultValue;
        }
    }

    private String parseCommandLineArg(String propertyKey) {
        String prefix1 = "-D" + propertyKey + "=";
        String prefix2 = "--" + propertyKey + "=";

        for (String arg : commandLineArgs) {
            if (arg.startsWith(prefix1)) {
                return arg.substring(prefix1.length());
            }
            if (arg.startsWith(prefix2)) {
                return arg.substring(prefix2.length());
            }
        }
        return null;
    }

    private String resolveSource(String propertyKey, String envKey) {
        String cmdValue = parseCommandLineArg(propertyKey);
        if (cmdValue != null) {
            return "命令行参数";
        }

        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isBlank()) {
            return "环境变量 " + envKey;
        }

        String propValue = propertyFileValues.get(propertyKey);
        if (propValue != null && !propValue.isBlank()) {
            return "配置文件 application.properties";
        }

        return "默认值";
    }

    public long getManagerIntervalMs() {
        return (Long) configValues.get(KEY_MANAGER_INTERVAL_MS).getValue();
    }

    public long getTeamLeaderManufacturingMs() {
        return (Long) configValues.get(KEY_TEAM_LEADER_MANUFACTURING_MS).getValue();
    }

    public long getShutdownJoinTimeoutMs() {
        return (Long) configValues.get(KEY_SHUTDOWN_JOIN_TIMEOUT_MS).getValue();
    }

    public int getProductionTypes() {
        return (Integer) configValues.get(KEY_PRODUCTION_TYPES).getValue();
    }

    public String getLogLevel() {
        return (String) configValues.get(KEY_LOG_LEVEL).getValue();
    }

    private void applyLogLevel() {
        String levelStr = getLogLevel();
        try {
            Level level = Level.valueOf(levelStr.toUpperCase());
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

            Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
            Level oldRootLevel = rootLogger.getLevel();
            rootLogger.setLevel(level);

            Logger appLogger = context.getLogger("com.factory");
            Level oldAppLevel = appLogger.getLevel();
            appLogger.setLevel(level);

            logger.info("日志级别已设置为 {} (ROOT: {} → {}, com.factory: {} → {})",
                    levelStr, oldRootLevel, level, oldAppLevel, level);
        } catch (Exception e) {
            logger.error("设置日志级别失败: {}", levelStr, e);
        }
    }

    public String getConfigSource(String propertyKey) {
        return switch (propertyKey) {
            case KEY_MANAGER_INTERVAL_MS -> resolveSource(KEY_MANAGER_INTERVAL_MS, ENV_MANAGER_INTERVAL_MS);
            case KEY_TEAM_LEADER_MANUFACTURING_MS -> resolveSource(KEY_TEAM_LEADER_MANUFACTURING_MS, ENV_TEAM_LEADER_MANUFACTURING_MS);
            case KEY_SHUTDOWN_JOIN_TIMEOUT_MS -> resolveSource(KEY_SHUTDOWN_JOIN_TIMEOUT_MS, ENV_SHUTDOWN_JOIN_TIMEOUT_MS);
            case KEY_PRODUCTION_TYPES -> resolveSource(KEY_PRODUCTION_TYPES, ENV_PRODUCTION_TYPES);
            case KEY_LOG_LEVEL -> resolveSource(KEY_LOG_LEVEL, ENV_LOG_LEVEL);
            default -> "未知";
        };
    }

    public void printConfigSnapshot() {
        logger.info("");
        logger.info("========================================================");
        logger.info("  生效配置快照 (Effective Configuration)");
        logger.info("========================================================");
        logger.info("  配置优先级: 命令行 > 环境变量 > application.properties > 默认值");
        logger.info("--------------------------------------------------------");

        int maxKeyLen = configValues.keySet().stream()
                .mapToInt(String::length)
                .max()
                .orElse(20);

        for (Map.Entry<String, ConfigValue<?>> entry : configValues.entrySet()) {
            String key = entry.getKey();
            ConfigValue<?> configValue = entry.getValue();
            String source = getConfigSource(key);
            String valueStr = String.valueOf(configValue.getValue());
            String paddedKey = String.format("%-" + maxKeyLen + "s", key);

            if (configValue.isDefault()) {
                logger.info("  {} = {}  [{}] (默认)", paddedKey, valueStr, source);
            } else {
                logger.info("  {} = {}  [{}]", paddedKey, valueStr, source);
            }
        }

        if (commandLineArgs.length > 0) {
            logger.info("--------------------------------------------------------");
            logger.info("  命令行参数: {}", String.join(" ", commandLineArgs));
        }

        Map<String, String> relevantEnvs = getRelevantEnvironmentVariables();
        if (!relevantEnvs.isEmpty()) {
            logger.info("--------------------------------------------------------");
            logger.info("  相关环境变量:");
            for (Map.Entry<String, String> env : relevantEnvs.entrySet()) {
                logger.info("    {}={}", env.getKey(), env.getValue());
            }
        }

        if (!propertyFileValues.isEmpty()) {
            logger.info("--------------------------------------------------------");
            logger.info("  application.properties 内容:");
            for (Map.Entry<String, String> prop : propertyFileValues.entrySet()) {
                logger.info("    {}={}", prop.getKey(), prop.getValue());
            }
        }

        logger.info("========================================================");
        logger.info("");
    }

    private Map<String, String> getRelevantEnvironmentVariables() {
        List<String> relevantKeys = List.of(
                ENV_MANAGER_INTERVAL_MS,
                ENV_TEAM_LEADER_MANUFACTURING_MS,
                ENV_SHUTDOWN_JOIN_TIMEOUT_MS,
                ENV_PRODUCTION_TYPES,
                ENV_LOG_LEVEL
        );

        Map<String, String> result = new LinkedHashMap<>();
        for (String key : relevantKeys) {
            String value = System.getenv(key);
            if (value != null) {
                result.put(key, value);
            }
        }
        return result;
    }

    public List<String> getAllKeys() {
        return new ArrayList<>(configValues.keySet());
    }

    public Object getValue(String key) {
        ConfigValue<?> cv = configValues.get(key);
        return cv != null ? cv.getValue() : null;
    }

    public boolean isDefault(String key) {
        ConfigValue<?> cv = configValues.get(key);
        return cv != null && cv.isDefault();
    }

    public Map<String, Object> getAllConfigAsMap() {
        return configValues.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().getValue(),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    public static class ConfigValue<T> {
        private final String key;
        private final T value;
        private final T defaultValue;

        private ConfigValue(String key, T value, T defaultValue) {
            this.key = key;
            this.value = value;
            this.defaultValue = defaultValue;
        }

        public static ConfigValue<Long> longValue(String key, long value, long defaultValue) {
            return new ConfigValue<>(key, value, defaultValue);
        }

        public static ConfigValue<Integer> intValue(String key, int value, int defaultValue) {
            return new ConfigValue<>(key, value, defaultValue);
        }

        public static ConfigValue<String> stringValue(String key, String value, String defaultValue) {
            return new ConfigValue<>(key, value, defaultValue);
        }

        public String getKey() {
            return key;
        }

        public T getValue() {
            return value;
        }

        public T getDefaultValue() {
            return defaultValue;
        }

        public boolean isDefault() {
            return Objects.equals(value, defaultValue);
        }
    }
}
