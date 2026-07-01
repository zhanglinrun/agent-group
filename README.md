# 多模式 Agent 工作台与拼团式额度交易平台

面向内容生成与营销交易场景的综合项目，主线是 **多模式 Agent 工作台 + 拼团式额度交易平台**。本项目不是普通聊天机器人，也不是单纯支付项目，而是把 Agent 执行能力和拼团交易能力放在同一业务闭环里：用户通过直接购买或拼团营销购买获得额度，再在 Agent 工作台中消耗额度完成对话、文件理解、深度任务、PPT 生成、生图和技能编排。

项目表达上按 **Agent 40% + 拼团交易 40% + 其他支撑能力 20%** 来讲。Agent 侧重点是多模式路由、重规划、反思、追踪和诊断；拼团交易侧重点是营销活动、试算、锁单、组队、成团结算、额度发放、退款补偿和状态一致性。

---

## 项目定位

| 子系统 | 做什么 |
| --- | --- |
| **Agent 工作台** | 流式对话、多模式执行、任务执行台、产物区、三层记忆、会话文件 RAG（检索增强生成）、请求追踪与运行诊断 |
| **拼团交易平台** | 额度包、直接购买、拼团营销活动、优惠试算、锁单参团、支付回调、成团结算、业务通知、退款、状态事件投递与补偿 |

**Maven 多模块 DDD 分层**：`agent-group-app` 单进程启动，依赖方向 `trigger → domain ← infrastructure`。

**来源融合**：`dodo-agent` 贡献多模式 Agent 执行、重规划、工具和记忆主线；`s-pay-mall-ddd-market` 贡献拼团营销、支付、成团结算和状态补偿主线；`group` 贡献原综合工程的页面、账号、额度和演示整合能力。本项目当前不是微服务，而是按这些边界整理后的模块化单体。

**简历口径**：项目名称优先写成 **多模式 Agent 工作台与拼团式额度交易平台**。对外表达时明确两条主线：Agent 工作台负责智能执行，拼团交易平台负责营销转化和额度资金闭环。当前工程是 Maven 多模块单体启动，可以讲 DDD 分层、事件补偿、锁单幂等和可拆分服务边界，不要直接写成已经落地的微服务集群。

---

## 核心能力

### 线上 Agent（用户对话主链路）

HTTP 入口在 `AgentController`，由 `AgentHandler` 调用 `UnifiedAgentOrchestrator` 选择模式，再进入 `AgentNativeService` 和 `trigger/agent/agent/` 下的执行体，经 SSE（流式输出）推送到前端工作台。

用户端主工作台可选模式（`/` 工作台，默认 `auto` 智能调度）：

| 模式 | 前端标签 | 执行范式 | 说明 |
| --- | --- | --- | --- |
| `auto` | 智能调度 | 自动选择 | 规则推断任务类型，路由到 deep / ppt / chat / file / manual-skills 等 |
| `chat` | 对话助手 | ReAct（思考-行动循环） | 通用问答、轻量工具调用 |
| `ppt` | PPT 生成 | 状态机生成链路 | 需求澄清 → 大纲 → 搜索 → 模板 → 渲染 |
| `deep` | 深度任务 | Plan-Execute（规划-执行） | 计划拆解、分步执行、失败重规划、反思评估 |
| `image` | 图像生成 | ReAct + 图像工具 | 图像生成、图生图 |
| `manual-skills` | Skill | 文件系统 Skill 加载 | 手动选择技能并读取项目内技能资源 |

前端当前用户可见工作区是 `/`（熊博士 Agent）和 `/workspace/image`（图像生成）。另有 `file`（文件问答）、`data`（表格 RAG / NL2SQL）、`skills`、`trade-diagnosis` 等能力，主要通过附件、隐藏工作区或内部路由触发，不在主工作台模式栏默认展示。

**会话文件 RAG**：用户上传附件后解析、切片，经 `EmbeddingPort` 写入 pgvector 表 `vector_file_info`，供 file 模式语义检索。独立运营知识库文档管理已移除，不再提供知识库 CRUD 后台。

**横切能力**：请求级 `requestId`（请求标识）与 domain `TraceContext`（追踪上下文）能力；对话前额度预检、执行后扣减流水；每次 run 结束推送诊断（`diagnosis_delta`）。

### Agent 智能化四件套（线上）

| 能力 | 覆盖范围 | 实现位置 |
| --- | --- | --- |
| **模式选择** | 全模式（含 `auto`） | `UnifiedAgentOrchestrator` + `AgentModeSelector`；SSE 推 `task_analysis` / `mode_selection` / `execution_applied` |
| **智能重规划** | 主要 `deep` | `PlanExecuteDomainBridge` → domain 策略；步骤失败重试 + LLM 多轮 replan |
| **反思评估** | 主要 `deep` | domain 规则反思（`source=domain_rule`）+ LLM critique；质量低触发继续重规划 |
| **异常诊断** | 全模式 | `AgentDiagnosisService`；检测慢执行、工具失败、额度异常、频繁重规划、执行异常 |

### deep 任务执行台、产物和记忆

`deep`（深度任务）模式已从“只输出回答”升级为任务执行台：运行时会识别文件理解、联网搜索、报告生成、PPT（演示文稿）、图片生成等能力，推送 `capability_plan`、`capability_called`、`memory_loaded`、`memory_saved` 等 SSE（流式事件），并把报告、PPT、图片、文件分析结果沉淀为可预览、下载、重新执行的产物。

记忆侧按三层表达：短期记忆服务当前会话，任务记忆记录计划、步骤、失败原因和产物摘要，长期记忆按 `userId`（用户编号）保存偏好、输出风格和业务背景。前端右上角“记忆”入口支持查看、刷新、启用、停用和删除长期记忆。

### Agent 引擎（domain 策略 + 线上执行体）

`domain/agent/runtime` 提供模式选择、计划编排、智能重规划、反思评估、异常诊断等**策略能力**，经 `UnifiedAgentOrchestrator`、`PlanExecuteDomainBridge` 接入 `trigger/agent/agent/` 执行体（不再维护独立离线 harness）。

路由与重规划单测：`AgentEngineRoutingTest`（23 条 auto 路由用例，断言准确率 ≥ 90% + domain 重规划桥接）。

### 拼团式额度交易平台

- **业务目标**：拼团交易不是附属小功能，而是项目第二主线。它把传统“优惠买商品”的营销玩法改造成“优惠买 Agent 调用额度”，用于提升额度包购买转化、增强用户分享传播，并把支付、成团、到账、退款和扣额串成闭环。
- **直接购买链路**：用户选择额度包后创建订单，支付成功（`PAY_SUCCESS`）即可发放额度，适合表达为标准额度包购买链路。
- **拼团营销链路**：围绕活动配置、优惠试算、锁单参团、组队状态、成团结算和超时退款组织流程。支付成功只表示名额已支付；必须等订单变为 `GROUP_SETTLED`（拼团已成团）或 `DEAL_DONE`（交易完成）后才发放额度，未成团不能提前到账。
- **营销规则与试算**：拼团侧可按活动、渠道、额度包、用户参与资格和优惠规则做试算，表达时重点讲“先试算、再锁单、后结算”的交易营销流程，而不是只讲支付接口。
- **锁单与并发控制**：使用下单幂等键、Redis 分布式锁、活动库存占位、队伍名额占位和失败库存恢复，降低重复点击、并发参团和网络重试带来的重复订单或库存异常风险。
- **交易一致性**：通过支付回调防重放、支付单状态幂等、订单流水、额度流水、`TradeEventOutbox` 状态事件和 `XXL-JOB` 交易补偿保证最终一致。直接购买按支付成功发额；拼团购买按成团 / 交易完成发额；退款或误发通过额度流水回滚。
- **Agent 扣额闭环**：额度只能由后端交易状态发放，并在 Agent 执行后扣减；前端和 Agent 不能直接决定到账或改余额。

---

## 技术栈

**后端**：Java 21、Spring Boot 3.5、Spring AI、MyBatis、DDD 分层

**前端**：React 19、Vite 8、React Router 7、Vitest、ESLint

**基础设施**（本地见 `docs/dev-ops`）：

| 组件 | 用途 |
| --- | --- |
| MySQL | 用户、订单、额度、拼团 |
| Redis / Redisson | 会话、缓存、分布式锁 |
| PostgreSQL + pgvector | 会话文件向量（`vector_file_info`） |
| MinIO | 附件与生成物对象存储 |
| RabbitMQ | 交易事件异步投递 |
| Prometheus + Grafana | 业务与 JVM 指标 |

**AI**：Spring AI `ChatClient`、`EmbeddingModel`、自定义 pgvector 适配；大模型与 embedding 默认走 DashScope 兼容接口，密钥通过环境变量配置。

---

## 代码结构

```
agent-group/
├── backend/                    # Maven 多模块后端
│   ├── agent-group-api/          # HTTP 契约与 DTO
│   ├── agent-group-app/          # 启动入口、配置、全部单测
│   ├── agent-group-domain/       # 领域逻辑
│   │   ├── account/              # 账号、登录会话、模型配置
│   │   ├── agent/                # 会话、智能体工作区、账本、runtime 策略层、文件 RAG 端口
│   │   ├── market/               # 营销活动、拼团试算、锁单参团、成团结算、人群标签
│   │   ├── quota/                # 额度账户、额度包、额度流水、额度发放和扣减规则
│   │   ├── trade/                # 订单、支付、退款、Outbox
│   │   └── support/              # 追踪、锁、动态配置
│   ├── agent-group-infrastructure/  # DB、缓存、MQ、Spring AI 适配
│   ├── agent-group-trigger/      # HTTP、SSE、定时任务、线上 Agent 执行体
│   └── agent-group-types/        # 通用响应与枚举
├── frontend/                     # 用户工作台 + /admin 运营端
├── docs/                         # 运维、压测、简历材料
├── skills/                       # 本地技能说明与资源
└── tools/                        # 辅助工具
```

会话文件的技术接入以端口形式定义在 `domain.agent.file`，由 `infrastructure` 实现，trigger 面向端口编程。

---

## 本地启动

### 1. 启动依赖

```bash
cd docs/dev-ops
docker compose -f docker-compose-environment.yml up -d
```

后端 `dev`（开发环境）启动依赖本地 MySQL、Redis、pgvector、MinIO 和 RabbitMQ。若后端日志出现 `Unable to connect to Redis server: 127.0.0.1:16379`，先确认 `Docker Desktop`（容器桌面程序）已启动，并重新执行上面的依赖启动命令。

### 2. 启动后端

```powershell
cd backend
$env:SPRING_PROFILES_ACTIVE="dev"
mvn -pl agent-group-app -am spring-boot:run
```

大模型、支付等敏感配置优先用环境变量，不要提交密钥。沙箱就绪检查见 `docs/dev-ops/payment-sandbox.md`。

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev
```

### 4. 访问地址

| 地址 | 说明 |
| --- | --- |
| http://localhost:5173/ | 用户端 Agent 工作台 |
| http://localhost:5173/admin | 运营端（总览、拼团活动、渠道与库存、模型配置、Skills、MCP、订单核查、退款） |
| http://localhost:8080 | 后端 API（dev 默认） |
| Grafana | 见 `docs/dev-ops` 中 docker-compose 端口映射 |

### 5. 关键接口边界

| 能力 | 接口 |
| --- | --- |
| Agent 流式执行 | `/api/v1/agent/stream` |
| Agent 工作区 | `/api/v1/agent/workspaces` |
| 图像工作区 | `/api/v1/agent/workspaces/image/*` |
| 数据工作区 | `/api/v1/agent/workspaces/data/*` |
| 营销交易 | `/api/v1/market/trade/*` |
| 支付 | `/api/v1/trade/payment/*` |

### 6. 当前运行自检

2026-07-01 已按 `dev`（开发环境）验证：依赖容器启动后，后端 `/actuator/health` 返回 `UP`；前端 `http://127.0.0.1:5173/` 返回 `HTTP 200`。若只启动前端，也可以先验证页面编译和首页响应，再启动后端依赖。

---

## 推荐演示路径

1. **注册登录** → 查看额度余额、额度流水和可购买额度包。
2. **拼团交易** → 演示活动试算、锁单参团、支付回调、等待成团、成团到账、超时退款等边界，明确区分「支付成功，等待成团」与「额度已到账」。
3. **直接购买** → 演示标准额度包购买，说明它与拼团购买在发额条件上的差异。
4. **运行 Agent** → 默认智能调度，或切换对话助手、深度任务、PPT 生成、图像生成、Skill，观察 SSE 流式输出、模式选择与额度扣减。
5. **deep 任务执行台** → 触发复杂任务，观察能力计划、能力调用事件、产物区、长期记忆读取和自动沉淀。
6. **文件问答** → 上传附件后提问，验证会话文件 RAG。
7. **运营端** → 查看总览、拼团活动、渠道与库存、模型配置、Skills / MCP、订单核查和退款。
8. **引擎单测**（可选）→ `mvn -pl agent-group-app -am test -Dtest=AgentEngineRoutingTest`，验证 auto 路由与 domain 重规划。
9. **交易压测**（可选）→ `docs/dev-ops/loadtest/`，跑完再把 QPS、P99 记入结果表；未实测的数字不要写进对外材料。

---

## 验证命令

```bash
# 后端默认验证（应用模块及依赖）
cd backend && mvn -pl agent-group-app -am test

# 全量编译 / 测试
mvn clean compile
mvn test

# 单个测试类
mvn -pl agent-group-app -am test -Dtest=GroupBuyLockOrderServiceTest

# Agent 引擎路由单测
mvn -pl agent-group-app -am test -Dtest=AgentEngineRoutingTest
```

```bash
# 前端
cd frontend && npm run test && npm run lint && npm run build
```

---

## 文档索引

**运维与环境**

- `docs/dev-ops/README.md` — 本地 Docker 环境与演示说明
- `docs/dev-ops/loadtest/README.md` — 交易压测脚本与结果记录约定
- `docs/dev-ops/payment-sandbox.md` — 支付宝沙箱检查

**简历与表达**

- `docs/resume-trade-project.md` — 拼团式额度交易平台简历材料
- `README.md` — 项目主线、演示路径和秋招表述

---

## 简历表述参考

完整简历材料维护在 `docs/resume-trade-project.md`。对外优先使用以下口径：

**项目名称**

多模式 Agent 工作台与拼团式额度交易平台

**技术栈**

Java 21、Spring Boot、Spring AI、MySQL、Redis、RabbitMQ、MyBatis、PostgreSQL、MinIO、XXL-JOB

**项目描述**

面向内容生成与额度交易场景开发的多模式 Agent 工作台，支持智能问答、文件理解、联网搜索、深度任务规划、PPT 生成和技能调用。系统通过统一 Agent Runtime 管理模式路由、上下文注入、记忆加载、工具调用、流式响应和运行追踪，并围绕 Agent 调用额度构建拼团购买、支付结算、额度发放、消息投递和补偿任务链路。

**负责功能结构**

- 4 条 Agent：Agent Runtime、多模式路由、记忆与文件理解、技能加载与重规划。
- 2 条交易：拼团交易链路、交易一致性与补偿。

**表达约束**

- 不写 Kafka，本项目实际使用 RabbitMQ。
- 不写 Spring WebFlux，当前代码是 Spring MVC + Reactor Flux 做流式响应。
- 不把 PPT 生成包装成标准 Agent 架构模式，可以说成 PPT 状态机生成链路。
- 不写“技能编排”的英文包装，代码里更准确的是文件系统 Skill 动态加载与工具注入。
- 未实测的 QPS、P99、召回率等数字先用 X 占位，不编造。

---

**更新时间**：2026-07-01
