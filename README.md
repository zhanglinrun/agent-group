# 多模式 Agent 工作台与拼团式额度交易平台 (Agent Group)

## 项目简介

本项目是一个面向内容生成与营销交易场景的综合工程，核心是 **多模式 `Agent`（智能体）工作台** 与 **拼团式额度交易平台**。用户通过直接购买或拼团营销获得调用额度，再在工作台中消耗额度完成对话、文件理解、深度任务、`PPT`（演示文稿）生成、图像生成和技能编排。

系统采用 `DDD`（领域驱动设计）与 `Maven`（构建工具）多模块分层，在同一进程内组装 `Agent`（智能体）执行与拼团交易两条主线。整体强调执行策略可观测、额度发放可追溯，以及订单、支付、拼团、额度状态的最终一致性。

---

## 系统架构

### 架构特点

- **模块化单体**：由 `agent-group-app` 单进程启动，按业务边界拆分模块，便于后续演进
- **`DDD` 设计**：依赖方向为 `trigger → domain ← infrastructure`，领域规则与技术实现分离
- **双主线闭环**：`Agent` 执行消耗额度；拼团 / 直购负责额度发放与退款补偿
- **流式交互**：对话与任务过程通过 `SSE`（流式输出）推送模式选择、思考链、产物与诊断
- **最终一致性**：订单流水、额度流水、支付幂等、消费记录与 `XXL-JOB`（分布式任务调度）补偿协同

### 核心领域

系统按业务边界划分领域，主要如下：

| 领域 | 说明 |
| --- | --- |
| **Agent 运行时** | 模式路由、`ReAct`（思考-行动循环）、`Plan-Execute`（规划-执行）、重规划、反思、工具调用、诊断 |
| **会话与记忆** | 会话、短期 / 任务 / 长期记忆、附件与产物 |
| **额度（Quota）** | 额度包、余额、扣减与流水；仅后端交易状态可发额 |
| **拼团营销（Market）** | 活动配置、试算、锁单参团、组队、成团结算、退款策略 |
| **交易支付（Trade）** | 下单、支付回调、幂等、业务通知与补偿 |
| **账号（Account）** | 登录与用户侧模型配置等 |

---

## 模块结构

```
agent-group/
├── backend/                              # Maven 多模块后端（单进程）
│   ├── agent-group-api/                  # API 接口定义层
│   │   └── 对外接口定义、DTO 对象
│   ├── agent-group-app/                  # 应用服务层
│   │   └── Spring Boot 启动类、配置与测试
│   ├── agent-group-domain/               # 领域模型层
│   │   ├── agent/                        # Agent 运行时、会话、账本、工作区
│   │   ├── market/                       # 拼团营销
│   │   ├── trade/                        # 订单与支付
│   │   ├── quota/                        # 额度计费
│   │   ├── account/                      # 账号
│   │   └── support/                      # 链路追踪、动态配置等
│   ├── agent-group-infrastructure/       # 基础设施层
│   │   └── 持久化、缓存、MQ、支付、对象存储、大模型适配
│   ├── agent-group-trigger/              # 触发器模块
│   │   └── HTTP、SSE、定时任务、消息监听
│   └── agent-group-types/                # 类型定义
│       └── 通用响应、异常与枚举
├── frontend/                             # 用户工作台与运营端（React + Vite）
├── docs/                                 # 运维、压测、评测与约定说明
├── skills/                               # 本地技能资源
└── tools/                                # 辅助工具（如 reactor-tool）
```

---

## 核心功能

### 1. 多模式 Agent 工作台

| 模式 | 说明 |
| --- | --- |
| `auto`（智能调度） | 规则路由任务类型，落到 deep / ppt / chat / file 等执行路径 |
| `chat`（对话助手） | 通用问答与轻量工具调用（`ReAct`） |
| `deep`（深度任务） | 计划拆解、分步执行、失败重规划与反思（`Plan-Execute`） |
| `ppt`（PPT 生成） | 需求澄清 → 大纲 → 搜索 → 模板 → 渲染的状态机链路 |
| `image`（图像生成） | 文生图 / 图生图等 |
| `manual-skills`（Skill） | 手动选择并加载项目内技能资源 |

支持会话附件 `RAG`（检索增强生成）、任务产物预览下载、三层记忆与运行诊断（`diagnosis_delta`）。

### 2. 拼团式额度交易

- **活动与试算**：活动配置、优惠试算、参与资格校验
- **锁单参团**：幂等下单、库存 / 队伍名额占位、失败恢复
- **成团结算**：达到成团条件后结算；未成团不发额度
- **发额规则**：
  - 直接购买：支付成功（`PAY_SUCCESS`）后发额度
  - 拼团购买：须订单为 `GROUP_SETTLED`（拼团已成团）或 `DEAL_DONE`（交易完成）后发额度
- **退款补偿**：多场景退款策略；误发通过额度流水回滚

### 3. 交易一致性与补偿

- 支付回调防重放、支付单状态幂等
- 订单流水 + 额度流水可对账
- `XXL-JOB` 超时关单、退款、查单与业务通知重试
- 消费记录表保证消息处理幂等

### 4. 运营与可观测

- 运营端：活动、渠道库存、模型配置、订单核查、退款等
- `Prometheus` + `Grafana` 指标；`Actuator` 健康检查
- 请求级追踪与执行诊断

---

## 技术栈

### 后端技术

| 类别 | 技术 |
| --- | --- |
| 语言 / 框架 | `Java 21`、`Spring Boot 3.5`、`Spring AI` |
| 持久化 | `MyBatis`、`MySQL`；会话向量侧 `PostgreSQL` + `pgvector` |
| 缓存与锁 | `Redis`、`Redisson` |
| 消息 | `RabbitMQ` |
| 任务调度 | `XXL-JOB` |
| 对象存储 | `MinIO` |
| 支付 | 支付宝沙箱 `SDK` |

### 前端技术

`React 19`、`Vite 8`、`React Router 7`、`Vitest`、`ESLint`

### 开发与本地基础设施

- 构建：`Maven 3.9+`（或兼容版本）
- 前端：`Node.js`（建议 `LTS`）
- 本地依赖见 `docs/dev-ops`：`MySQL`、`Redis`、`pgvector`、`Qdrant`、`MinIO`、`RabbitMQ`、`XXL-JOB`、`Prometheus`、`Grafana` 等

大模型与 `Embedding`（向量嵌入）默认走兼容接口；敏感配置统一从运行环境读取，不在仓库中保存真实凭据。

---

## 技术亮点

### 1. DDD 分层与双主线边界

交易侧与 `Agent` 侧按领域分包；额度只能由后端交易状态发放，前端与 `Agent` 不能直接改余额。执行扣额与交易发额共用可追溯流水。

### 2. 多模式路由与可观测执行

默认 `auto` 走规则路由（非大模型调度），推送 `task_analysis`、`mode_selection`、`execution_applied` 等事件；`deep` 路径接入重规划与反思；全模式 run 结束可推送诊断。

### 3. 拼团营销与锁单并发控制

试算 → 锁单 → 支付 → 成团 / 超时的完整链路；下单幂等、分布式锁、库存与队伍名额占位、失败恢复，降低重复下单与超卖风险。

### 4. 支付 / 成团 / 发额最终一致

本地消息与定时补偿、分布式锁幂等抢占、失败重试，保证支付成功、拼团结算、业务通知与额度到账在异常下仍可收敛。

### 5. 退款多场景策略

已支付未成团、已支付已成团等场景用策略路由处理，并与额度回滚、库存恢复保持一致。

### 6. 大模型主链路与回退

主链路优先使用 `Spring AI` 的 `ChatClient`、`EmbeddingModel`、`VectorStore`；必要时保留兼容回退，降低外部服务不可用时的演示中断风险。

---

## 环境要求

- `JDK 21+`
- `Maven 3.9+`（或兼容版本）
- `Node.js`（前端，建议 `LTS`）
- `Docker` / `Docker Compose`（本地依赖）
- `MySQL 8+`、`Redis 5+`、`RabbitMQ 3.8+`（可由 compose 拉起）
- 可选：大模型与支付宝沙箱相关运行配置（通过环境变量注入）

---

## 快速开始

### 1. 环境准备

确保本机已安装 `JDK 21+`、`Maven`、`Node.js`，以及可用的 `Docker Desktop`。

### 2. 启动依赖

```bash
cd docs/dev-ops
docker compose -f docker-compose-environment.yml up -d
```

若后端提示无法连接 `Redis`（如 `127.0.0.1:16379`），先确认 `Docker Desktop` 已启动，再重新执行上述命令。

**可选：Agent 工具服务**

```powershell
cd docs/dev-ops
docker compose -f docker-compose-environment.yml -f docker-compose-app.yml up -d reactor-tool
```

或本地：

```powershell
cd tools/reactor-tool
copy .env_template .env
# 按模板补充本地运行配置，勿提交 .env
.\start.ps1
```

### 3. 数据库初始化

表结构与演示数据脚本位于 `docs/dev-ops/mysql/sql/`。compose 环境通常会自动初始化；手工部署时按该目录顺序执行。

### 4. 配置说明

敏感项统一通过环境变量传入，**不要**在文档、配置样例或仓库中填写真实密钥、令牌或证书内容。

- 开发默认配置：`backend/agent-group-app/src/main/resources/application-dev.yml`
- 支付沙箱检查：`docs/dev-ops/payment-sandbox.md`
- 工具服务模板：`tools/reactor-tool/.env_template`

按需设置数据库、`Redis`、`RabbitMQ`、大模型、支付等相关环境变量后启动。

### 5. 启动后端

```powershell
cd backend
$env:SPRING_PROFILES_ACTIVE="dev"
mvn -pl agent-group-app -am spring-boot:run
```

### 6. 启动前端

```bash
cd frontend
npm install
npm run dev
```

### 7. 访问地址

| 地址 | 说明 |
| --- | --- |
| http://localhost:5173/ | 用户 `Agent` 工作台 |
| http://localhost:5173/workspace/trade | 拼团购买与额度中心 |
| http://localhost:5173/admin | 运营端 |
| http://localhost:8080 | 后端 `API`（dev 默认） |
| http://localhost:1601 | `reactor-tool`（可选） |

### 8. 关键接口边界

| 能力 | 接口 |
| --- | --- |
| `Agent` 流式对话 | `POST /web/api/v1/gpt/queryAgentStreamIncr`（`SSE`） |
| 访客引导 | `GET /api/agent/visitor/bootstrap` |
| 图像生成 | `POST /api/agent/image-generation/generate` |
| 营销交易 | `/api/v1/market/trade/*` |
| 支付 | `/api/v1/trade/payment/*` |
| 账号 | `/api/v1/account/*` |

---

## 验证命令

```bash
# 后端：应用模块及依赖测试
cd backend && mvn -pl agent-group-app -am test

# 全量编译 / 测试
mvn clean compile
mvn test

# 示例：交易 / 引擎相关单测
mvn -pl agent-group-app -am test -Dtest=GroupBuyLockOrderServiceTest
mvn -pl agent-group-app -am test -Dtest=AgentEngineRoutingTest
```

```bash
# 前端
cd frontend && npm run test && npm run lint && npm run build
```

交易压测脚本与结果约定见 `docs/dev-ops/loadtest/`。

---

## 项目结构说明

### API 层（agent-group-api）

定义对外接口和 `DTO` 对象，提供统一的接口规范。

### 应用层（agent-group-app）

`Spring Boot` 启动类、全局配置与测试承载，负责组装领域能力与基础设施实现。

### 领域层（agent-group-domain）

核心业务逻辑，包含：

- `Agent`：模式路由、执行、重规划、反思、诊断
- 拼团营销：活动、试算、锁单、结算、退款
- 交易支付：下单、回调、幂等与通知
- 额度：余额、流水、发额与回滚规则

### 基础设施层（agent-group-infrastructure）

数据持久化、缓存、消息、对象存储、大模型与支付等技术适配。

### 触发器层（agent-group-trigger）

`HTTP` 接口、`SSE` 流式输出、定时任务与消息监听等入口。

### 前端（frontend）

用户 `Agent` 工作台、额度购买页与运营端页面。

---

## 部署说明

### Docker 部署

`docs/dev-ops` 提供依赖环境 compose、可选应用与工具容器、监控组件。启动与端口映射以该目录说明为准。

### 生产环境建议

- 使用环境变量注入数据库、`Redis`、`MQ`、大模型与支付配置，禁止把敏感凭据写入仓库
- 按并发调整连接池与 `Tomcat` 线程参数
- 配置 `JVM` 堆与 `GC`（垃圾回收）策略（按机器规格设定）
- 开启日志滚动与指标采集，配合补偿任务监控失败重试

---

## 监控与运维

### 日志与健康

- 使用 `Logback` 进行日志管理
- 后端 `Actuator` 健康检查（如 `/actuator/health`）

### 任务调度

- 使用 `XXL-JOB` 进行分布式任务调度
- 支持任务监控、失败重试与执行日志查看

### 性能监控

- `Prometheus` 采集，`Grafana` 展示
- 可关注 `JVM`、数据库、`Redis` 与业务指标

### 支付沙箱

检查步骤见 `docs/dev-ops/payment-sandbox.md`。

---

## 文档索引

| 文档 | 说明 |
| --- | --- |
| `docs/dev-ops/README.md` | 本地 Docker 环境与启动 |
| `docs/dev-ops/loadtest/README.md` | 交易压测脚本与结果记录 |
| `docs/dev-ops/payment-sandbox.md` | 支付宝沙箱检查 |
| `docs/agent-eval-report.md` | `Agent` 路由与重规划评测（本地产物，见 `docs/README.md`） |
| `docs/rag-eval-report.md` | `RAG` 检索评测（本地产物） |
| `docs/orm-layer-split.md` | `MyBatis` 分层说明 |
| `docs/hardcoded-constants.md` | 动态配置与常量约定 |

---

## 业务红线（额度）

1. 额度只能由后端交易状态发放，前端与 `Agent` 不能直接决定到账。
2. 直购：支付成功后发额。
3. 拼团：支付成功仅表示名额已付；成团 / 交易完成后再发额。
4. 退款或误发须记额度流水并回滚余额。

---

## 开发约定

- 领域逻辑放 `domain`，技术接入放 `infrastructure`，入口放 `trigger`
- 敏感配置走环境变量；不要提交 `.env` 或真实密钥
- 提交信息建议使用类似 `feat: 运行基线整理` 的格式
- 每次只实现当前阶段最小可验证能力

---

**更新时间**：2026-07-27
