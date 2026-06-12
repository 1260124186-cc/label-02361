# Factory Production Synchronization System

基于 Java `wait()/notify()` 机制实现的工厂生产同步系统，演示经典的 Producer-Consumer（生产者-消费者）模式。

## 1. How to Run

### 方式一：Docker Compose 一键启动（推荐）

```bash
docker-compose up --build -d
```

查看实时日志：

```bash
docker-compose logs -f
```

按需指定生产类型数量（默认 5）：

```bash
# Linux / macOS
PRODUCTION_TYPES=10 docker-compose up --build -d

# Windows PowerShell
$env:PRODUCTION_TYPES=10; docker-compose up --build -d
```

停止并清理容器：

```bash
docker-compose down
```

### 方式二：一键启动脚本

**Linux / macOS:**
```bash
chmod +x start.sh
./start.sh
```

**Windows:**
```cmd
start.bat
```

> 启动脚本会自动检测并提示安装 Java 17 和 Maven（如缺失）。

### 方式三：手动构建运行

**前置条件：**
- Java 17+
- Maven 3.6+

```bash
cd backend
mvn clean package -DskipTests
java -jar target/factory-sync-1.0.0.jar
```

### 自定义生产类型数量

默认生产类型为 5 种（pID 1..5）。可通过命令行参数指定：

```bash
# 使用 10 种生产类型
java -jar target/factory-sync-1.0.0.jar 10

# 或通过启动脚本
./start.sh 10
```

### 停止运行

按 `Ctrl+C` 优雅停止所有进程。

## 2. Services

| 服务 | 说明 |
|------|------|
| Main | 程序入口，创建并启动 Manager 和 Team Leader 线程 |
| Factory | 共享任务盒，通过 synchronized + wait/notify 实现同步 |
| ManagerProcess | 生产者线程，循环放入生产类型 pID |
| TeamLeaderProcess | 消费者线程，循环获取并执行生产任务 |
| Docker Compose (`factory-sync`) | 容器化运行入口，支持 `docker-compose up --build -d` 一键启动 |

## 3. 测试账号

本项目为纯后端并发演示程序，无登录功能，无需测试账号。

## 4. 题目内容

For a factory, the production types are modelled as an integer pID (1..n). A manager daily puts the manufacturing production type into a task box, and then a team leader gets the production type from the task box, and then organizes its team members to manufacture it. The task box has a lock with unique key owned by a team leader. The lock is modelled as a boolean value, its original state is opened (lock=false).
In order to implement the condition synchronization that the getting operation is always behind the putting one, the manager only conducting putting operation when the bock lock is opened. In addition, after finishing the putting operation, he locks the box (lock=true), and informs the team leader through notify(). The team leader has an unique key, opening the lock (lock=false), and then getting the production types assigned by the manager. The process is repeated without stopping.
If the getting operation of the team leader is ahead of the putting operation of the manager, it is blocked through wait().
Your tasks:
1) write the class Factory which including void put(int pID) and int get();
2) write manager process and team leader process.

## 5. 项目结构

```
label-02361/
├── docker-compose.yml                     # Docker Compose 一键启动编排
├── backend/                              # 后端 Java 项目
│   ├── Dockerfile                         # 后端容器镜像构建文件
│   ├── .dockerignore                      # Docker 构建上下文忽略配置
│   ├── pom.xml                           # Maven 依赖配置
│   └── src/main/
│       ├── java/com/factory/
│       │   ├── Main.java                 # 程序入口
│       │   ├── model/
│       │   │   └── Factory.java          # 核心 Factory 类 (put/get)
│       │   └── process/
│       │       ├── ManagerProcess.java   # Manager 生产者进程
│       │       └── TeamLeaderProcess.java# Team Leader 消费者进程
│       └── resources/
│           └── logback.xml               # 日志配置
├── docs/
│   └── project_design.md                 # 设计文档
├── start.sh                              # Linux/macOS 启动脚本
├── start.bat                             # Windows 启动脚本
├── .gitignore                            # Git 忽略配置
├── README.md                             # 本文件
└── draft.md                              # 项目交付总结
```

## 6. 功能清单

- [x] **Factory 类**：包含 `synchronized void put(int pID)` 和 `synchronized int get()` 方法
- [x] **Boolean 锁机制**：`lock = false` (opened) / `lock = true` (locked)
- [x] **条件同步**：Manager 仅在 lock=false 时 put；Team Leader 仅在 lock=true 时 get
- [x] **wait/notify 协调**：put 后 notify() 通知 Team Leader；get 后 notify() 通知 Manager
- [x] **阻塞机制**：Team Leader 的 get 操作先于 Manager 的 put 时，通过 wait() 阻塞
- [x] **Manager 进程**：循环生产 pID (1..n)，放入任务盒
- [x] **Team Leader 进程**：循环获取 pID，组织团队制造
- [x] **无限循环**：两个进程持续运行，不停止
- [x] **优雅关闭**：Ctrl+C 触发 ShutdownHook，中断线程并等待退出
- [x] **SLF4J 日志**：关键操作有结构化日志输出
- [x] **命令行参数**：支持自定义生产类型数量 n
- [x] **一键启动脚本**：start.sh (Linux/macOS) + start.bat (Windows)
- [x] **Docker Compose 一键启动**：支持 `docker-compose up --build -d` 启动容器化服务

## 编码说明

本项目所有文件使用 UTF-8 编码，确保中文正常显示：
- 源代码：UTF-8 without BOM
- 日志输出：UTF-8（通过 logback.xml 配置）
- 启动脚本：UTF-8（start.sh 设置 LANG=zh_CN.UTF-8，start.bat 设置 chcp 65001）

## 技术栈

| 层级 | 技术 |
|------|------|
| 语言 | Java 17 |
| 构建工具 | Maven 3.6+ |
| 日志 | SLF4J 2.0.9 + Logback 1.4.14 |
| 并发模型 | synchronized + wait/notify |
