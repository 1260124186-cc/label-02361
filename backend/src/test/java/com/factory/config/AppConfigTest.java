package com.factory.config;

import com.factory.model.ProductionTypeRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AppConfig 运行时配置测试")
class AppConfigTest {

    private Map<String, String> originalEnv;

    @BeforeEach
    void setUp() {
        originalEnv = System.getenv();
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("manager.interval.ms");
        System.clearProperty("team-leader.manufacturing.ms");
        System.clearProperty("shutdown.join.timeout.ms");
        System.clearProperty("production.types");
        System.clearProperty("log.level");
    }

    @Test
    @DisplayName("默认值测试：所有配置项应有正确的默认值")
    void testDefaultValues() {
        AppConfig config = AppConfig.load(new String[0]);

        assertEquals(AppConfig.DEFAULT_MANAGER_INTERVAL_MS, config.getManagerIntervalMs());
        assertEquals(AppConfig.DEFAULT_TEAM_LEADER_MANUFACTURING_MS, config.getTeamLeaderManufacturingMs());
        assertEquals(AppConfig.DEFAULT_SHUTDOWN_JOIN_TIMEOUT_MS, config.getShutdownJoinTimeoutMs());
        assertEquals(AppConfig.DEFAULT_PRODUCTION_TYPES, config.getProductionTypes());
        assertEquals(AppConfig.DEFAULT_LOG_LEVEL, config.getLogLevel());
    }

    @Test
    @DisplayName("命令行参数优先级测试：-D 格式参数应覆盖默认值")
    void testCommandLineArgsDashD() {
        String[] args = {
                "-Dmanager.interval.ms=2000",
                "-Dteam-leader.manufacturing.ms=3000",
                "-Dshutdown.join.timeout.ms=5000",
                "-Dproduction.types=10",
                "-Dlog.level=DEBUG"
        };

        AppConfig config = AppConfig.load(args);

        assertEquals(2000L, config.getManagerIntervalMs());
        assertEquals(3000L, config.getTeamLeaderManufacturingMs());
        assertEquals(5000L, config.getShutdownJoinTimeoutMs());
        assertEquals(10, config.getProductionTypes());
        assertEquals("DEBUG", config.getLogLevel());

        assertEquals("命令行参数", config.getConfigSource(AppConfig.KEY_MANAGER_INTERVAL_MS));
        assertFalse(config.isDefault(AppConfig.KEY_MANAGER_INTERVAL_MS));
    }

    @Test
    @DisplayName("命令行参数优先级测试：-- 格式参数应覆盖默认值")
    void testCommandLineArgsDoubleDash() {
        String[] args = {
                "--manager.interval.ms=2500",
                "--log.level=WARN"
        };

        AppConfig config = AppConfig.load(args);

        assertEquals(2500L, config.getManagerIntervalMs());
        assertEquals("WARN", config.getLogLevel());
        assertEquals("命令行参数", config.getConfigSource(AppConfig.KEY_LOG_LEVEL));
    }

    @Test
    @DisplayName("配置优先级测试：命令行参数应覆盖默认值")
    void testCommandLineOverridesDefaults() {
        String[] args = {"-Dmanager.interval.ms=1500"};
        AppConfig config = AppConfig.load(args);

        assertEquals(1500L, config.getManagerIntervalMs());
        assertEquals("命令行参数", config.getConfigSource(AppConfig.KEY_MANAGER_INTERVAL_MS));
    }

    @Test
    @DisplayName("配置优先级测试：命令行参数优先级高于环境变量（模拟）")
    void testCommandLinePriority() {
        String[] args = {"-Dmanager.interval.ms=100"};
        AppConfig config = AppConfig.load(args);

        assertEquals(100L, config.getManagerIntervalMs());
    }

    @Test
    @DisplayName("无效数值测试：无效 long 值应回退到默认值")
    void testInvalidLongValueFallsBackToDefault() {
        String[] args = {"-Dmanager.interval.ms=not_a_number"};
        AppConfig config = AppConfig.load(args);

        assertEquals(AppConfig.DEFAULT_MANAGER_INTERVAL_MS, config.getManagerIntervalMs());
    }

    @Test
    @DisplayName("无效数值测试：无效 int 值应回退到默认值")
    void testInvalidIntValueFallsBackToDefault() {
        String[] args = {"-Dproduction.types=not_a_number"};
        AppConfig config = AppConfig.load(args);

        assertEquals(AppConfig.DEFAULT_PRODUCTION_TYPES, config.getProductionTypes());
    }

    @Test
    @DisplayName("构造函数参数验证：负数 intervalMs 应抛出异常")
    void testNegativeIntervalMs() {
        assertThrows(IllegalArgumentException.class, () ->
                new com.factory.process.ManagerProcess(
                        new com.factory.model.Factory(),
                        ProductionTypeRegistry.createDefault(3),
                        ProductionStrategy.sequential(),
                        -1L));
    }

    @Test
    @DisplayName("构造函数参数验证：负数 defaultManufacturingMs 应抛出异常")
    void testNegativeManufacturingMs() {
        assertThrows(IllegalArgumentException.class, () ->
                new com.factory.process.TeamLeaderProcess(
                        new com.factory.model.Factory(),
                        null,
                        -1L));
    }

    @Test
    @DisplayName("getAllKeys 应返回所有配置键")
    void testGetAllKeys() {
        AppConfig config = AppConfig.load(new String[0]);

        assertTrue(config.getAllKeys().contains(AppConfig.KEY_MANAGER_INTERVAL_MS));
        assertTrue(config.getAllKeys().contains(AppConfig.KEY_TEAM_LEADER_MANUFACTURING_MS));
        assertTrue(config.getAllKeys().contains(AppConfig.KEY_SHUTDOWN_JOIN_TIMEOUT_MS));
        assertTrue(config.getAllKeys().contains(AppConfig.KEY_PRODUCTION_TYPES));
        assertTrue(config.getAllKeys().contains(AppConfig.KEY_LOG_LEVEL));
        assertEquals(5, config.getAllKeys().size());
    }

    @Test
    @DisplayName("getValue 应返回正确类型的值")
    void testGetValue() {
        String[] args = {"-Dmanager.interval.ms=1234"};
        AppConfig config = AppConfig.load(args);

        assertEquals(1234L, config.getValue(AppConfig.KEY_MANAGER_INTERVAL_MS));
        assertEquals(AppConfig.DEFAULT_LOG_LEVEL, config.getValue(AppConfig.KEY_LOG_LEVEL));
        assertNull(config.getValue("non.existent.key"));
    }

    @Test
    @DisplayName("isDefault 应正确标识默认值")
    void testIsDefault() {
        String[] args = {"-Dmanager.interval.ms=999"};
        AppConfig config = AppConfig.load(args);

        assertFalse(config.isDefault(AppConfig.KEY_MANAGER_INTERVAL_MS));
        assertTrue(config.isDefault(AppConfig.KEY_TEAM_LEADER_MANUFACTURING_MS));
        assertTrue(config.isDefault(AppConfig.KEY_SHUTDOWN_JOIN_TIMEOUT_MS));
        assertTrue(config.isDefault(AppConfig.KEY_PRODUCTION_TYPES));
        assertTrue(config.isDefault(AppConfig.KEY_LOG_LEVEL));
    }

    @Test
    @DisplayName("getAllConfigAsMap 应返回所有配置")
    void testGetAllConfigAsMap() {
        String[] args = {"-Dmanager.interval.ms=777"};
        AppConfig config = AppConfig.load(args);

        Map<String, Object> map = config.getAllConfigAsMap();

        assertEquals(5, map.size());
        assertEquals(777L, map.get(AppConfig.KEY_MANAGER_INTERVAL_MS));
        assertEquals(AppConfig.DEFAULT_TEAM_LEADER_MANUFACTURING_MS, map.get(AppConfig.KEY_TEAM_LEADER_MANUFACTURING_MS));
    }

    @Test
    @DisplayName("printConfigSnapshot 不应抛出异常")
    void testPrintConfigSnapshot() {
        AppConfig config = AppConfig.load(new String[]{"-Dlog.level=INFO"});
        assertDoesNotThrow(config::printConfigSnapshot);
    }

    @Test
    @DisplayName("空参数数组不应抛出异常")
    void testNullArgs() {
        assertDoesNotThrow(() -> AppConfig.load(null));
        AppConfig config = AppConfig.load(null);
        assertEquals(AppConfig.DEFAULT_MANAGER_INTERVAL_MS, config.getManagerIntervalMs());
    }

    @Test
    @DisplayName("ManagerProcess 应正确使用配置的 intervalMs")
    void testManagerProcessUsesConfig() {
        String[] args = {"-Dmanager.interval.ms=500"};
        AppConfig config = AppConfig.load(args);

        com.factory.process.ManagerProcess manager = new com.factory.process.ManagerProcess(
                new com.factory.model.Factory(),
                ProductionTypeRegistry.createDefault(3),
                ProductionStrategy.sequential(),
                config);

        assertEquals(500L, manager.getIntervalMs());
    }

    @Test
    @DisplayName("TeamLeaderProcess 应正确使用配置的 defaultManufacturingMs")
    void testTeamLeaderProcessUsesConfig() {
        String[] args = {"-Dteam-leader.manufacturing.ms=2500"};
        AppConfig config = AppConfig.load(args);

        com.factory.process.TeamLeaderProcess leader = new com.factory.process.TeamLeaderProcess(
                new com.factory.model.Factory(),
                ProductionTypeRegistry.createDefault(3),
                config);

        assertEquals(2500L, leader.getDefaultManufacturingMs());
    }

    @Test
    @DisplayName("向后兼容：ManagerProcess 旧构造函数应使用默认 intervalMs")
    void testManagerProcessBackwardCompatibility() {
        com.factory.process.ManagerProcess manager = new com.factory.process.ManagerProcess(
                new com.factory.model.Factory(),
                ProductionTypeRegistry.createDefault(3),
                ProductionStrategy.sequential());

        assertEquals(AppConfig.DEFAULT_MANAGER_INTERVAL_MS, manager.getIntervalMs());
    }

    @Test
    @DisplayName("向后兼容：TeamLeaderProcess 旧构造函数应使用默认 manufacturingMs")
    void testTeamLeaderProcessBackwardCompatibility() {
        com.factory.process.TeamLeaderProcess leader = new com.factory.process.TeamLeaderProcess(
                new com.factory.model.Factory(),
                ProductionTypeRegistry.createDefault(3));

        assertEquals(AppConfig.DEFAULT_TEAM_LEADER_MANUFACTURING_MS, leader.getDefaultManufacturingMs());
    }
}
