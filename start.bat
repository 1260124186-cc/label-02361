@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo ========================================
echo   Factory Production Sync - 一键启动脚本
echo ========================================

:: 1. 检查 Java
echo [1/3] 检测 Java 环境...
where java >nul 2>&1
if %errorlevel% neq 0 (
    echo   ✗ Java 未安装
    echo   正在打开 Java 下载页面...
    start https://adoptium.net/zh-CN/temurin/releases/?version=17
    echo.
    echo   请安装 Java 17 后重新运行此脚本
    pause
    exit /b 1
)
echo   ✓ Java 已安装

:: 2. 检查 Maven
echo [2/3] 检测构建工具...
where mvn >nul 2>&1
if %errorlevel% neq 0 (
    echo   ✗ Maven 未安装
    echo   请从 https://maven.apache.org/download.cgi 下载并安装 Maven
    echo   安装后将 Maven 的 bin 目录添加到系统 PATH 环境变量
    pause
    exit /b 1
)
echo   ✓ Maven 已安装

:: 3. 构建并运行
echo [3/3] 构建并启动应用...
cd backend

if not exist "target\factory-sync-1.0.0.jar" (
    echo   正在编译项目...
    call mvn clean package -q -DskipTests
    if %errorlevel% neq 0 (
        echo   ❌ 编译失败，请检查错误信息
        pause
        exit /b 1
    )
    echo   ✓ 构建完成
) else (
    echo   ✓ 项目已构建
)

echo.
echo ========================================
echo   🚀 启动 Factory Production Sync System
echo   按 Ctrl+C 停止
echo ========================================
echo.

java -jar target\factory-sync-1.0.0.jar %*
