#!/bin/bash
# -*- coding: utf-8 -*-
set -e
cd "$(dirname "$0")"

export LANG=zh_CN.UTF-8
export LC_ALL=zh_CN.UTF-8

echo "========================================"
echo "  Factory Production Sync - 一键启动脚本"
echo "========================================"

# 1. 检查 Java
echo "[1/3] 检测 Java 环境..."
if ! command -v java &> /dev/null; then
    echo "  ✗ Java 未安装，正在自动安装 Java 17..."
    if [[ "$OSTYPE" == "darwin"* ]]; then
        if command -v brew &> /dev/null; then
            brew install openjdk@17
        else
            echo "  请先安装 Homebrew: https://brew.sh"
            echo "  或手动安装 Java 17: https://adoptium.net/zh-CN/temurin/releases/?version=17"
            exit 1
        fi
    elif command -v apt-get &> /dev/null; then
        sudo apt-get update && sudo apt-get install -y openjdk-17-jdk
    elif command -v yum &> /dev/null; then
        sudo yum install -y java-17-openjdk-devel
    else
        echo "  ❌ 无法自动安装 Java，请手动安装 Java 17:"
        echo "     https://adoptium.net/zh-CN/temurin/releases/?version=17"
        exit 1
    fi
    echo "  ✓ Java 17 安装完成"
fi
echo "  ✓ $(java -version 2>&1 | head -n 1)"

# 2. 检查 Maven
echo "[2/3] 检测构建工具..."
if command -v mvn &> /dev/null; then
    echo "  ✓ Maven 已安装"
else
    echo "  ✗ Maven 未安装，正在安装..."
    if [[ "$OSTYPE" == "darwin"* ]]; then
        if command -v brew &> /dev/null; then
            brew install maven
        else
            echo "  请先安装 Maven: https://maven.apache.org/download.cgi"
            exit 1
        fi
    elif command -v apt-get &> /dev/null; then
        sudo apt-get install -y maven
    elif command -v yum &> /dev/null; then
        sudo yum install -y maven
    else
        echo "  ❌ 无法自动安装 Maven，请手动安装:"
        echo "     https://maven.apache.org/download.cgi"
        exit 1
    fi
    echo "  ✓ Maven 安装完成"
fi

# 3. 构建并运行
echo "[3/3] 构建并启动应用..."
cd backend

if [ ! -f "target/factory-sync-1.0.0.jar" ]; then
    echo "  正在编译项目..."
    mvn clean package -q -DskipTests
    echo "  ✓ 构建完成"
else
    echo "  ✓ 项目已构建（如需重新构建，请删除 backend/target/ 目录）"
fi

echo ""
echo "========================================"
echo "  🚀 启动 Factory Production Sync System"
echo "  按 Ctrl+C 停止"
echo "========================================"
echo ""

java -jar target/factory-sync-1.0.0.jar "$@"
