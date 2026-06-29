# 多模式 Agent 工作台与拼团式额度交易平台

面向内容生成与营销交易场景的综合项目，主线是 **多模式 Agent 工作台 + 拼团式额度交易平台**。本项目不是普通聊天机器人，也不是单纯支付项目，而是把 Agent 执行能力和拼团交易能力放在同一业务闭环里：用户通过直接购买或拼团营销购买获得额度，再在 Agent 工作台中消耗额度完成对话、文件理解、深度任务、PPT 生成、生图和技能编排。

项目表达上按 **Agent 40% + 拼团交易 40% + 其他支撑能力 20%** 来讲。Agent 侧重点是多模式路由、重规划、反思、追踪和诊断；拼团交易侧重点是营销活动、试算、锁单、组队、成团结算、额度发放、退款补偿和状态一致性。

---

## 项目定位

| 子系统 | 做什么 |
| --- | --- |
| **Agent 工作台** | 流式对话、多模式执行、会话文件 RAG（检索增强生成）、项目工作区、请求追踪与运行诊断 |
| **拼团交易平台** | 额度包、直接购买、拼团营销活动、优惠试算、锁单参团、支付回调、成团结算、业务通知、退款、状态事件投递与补偿 |

**Maven 多模块 DDD 分层**：`agent-group-app` 单进程启动，依赖方向 `trigger → domain ← infrastructure`。

**简历口径**：项目名称优先写成 **多模式 Agent 工作台与拼团式额度交易平台**。对外表达时明确两条主线：Agent 工作台负责智能执行，拼团交易平台负责营销转化和额度资金闭环。当前工程是 Maven 多模块单体启动，可以讲 DDD 分层、事件补偿、锁单幂等和可拆分服务边界，不要直接写成已经落地的微服务集群。

---

## 核心能力

### 线上 Agent（用户对话主链路）

HTTP 入口在 `AcademicAgentController`，由 `AcademicAgentHandler` 调用 `UnifiedAgentOrchestrator` 选择模式，再进入 `AcademicAgentNativeService` 和 `trigger/agent/agent/` 下的执行体，经 SSE（流式输出）推送到前端工作台。

用户端主工作台可选模式（`/` 工作台，默认 `auto` 智能调度）：

| 模式 | 前端标签 | 执行范式 | 说明 |
| --- | --- | --- | --- |
| `auto` | 智能调度 | 自动选择 | 规则推断任务类型，路由到 deep / ppt / chat / file / manual-skills 等 |
| `chat` | 对话助手 | ReAct（思考-行动循环） | 通用问答、轻量工具调用 |
| `ppt` | PPT 生成 | PPT Workflow（PPT 工作流） | 需求澄清 → 大纲 → 搜索 → 模板 → 渲染 |
| `deep` | 深度任务 | Plan-Execute（规划-执行） | 计划拆解、分步执行、失败重规划、反思评估 |
| `image` | 图像生成 | ReAct + 图像工具 | 图像生成、图生图 |
| `manual-skills` | Skill | Skill Orchestration（技能编排） | 手动选择技能并执行编排 |

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

### Agent 引擎（domain 策略 + 线上执行体）

`domain/academic/runtime` 提供模式选择、计划编排、智能重规划、反思评估、异常诊断等**策略能力**，经 `UnifiedAgentOrchestrator`、`PlanExecuteDomainBridge` 接入 `trigger/agent/agent/` 执行体（不再维护独立离线 harness）。

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
│   │   ├── academic/             # 账本、项目工作区、runtime 策略层（代码包名 legacy）
│   │   ├── account/              # 账号、额度
│   │   ├── agent/                # 会话、文件 RAG 端口
│   │   ├── groupbuy/             # 拼团
│   │   ├── trade/                # 交易、支付、Outbox
│   │   └── support/              # 追踪、锁、动态配置
│   ├── agent-group-infrastructure/  # DB、缓存、MQ、Spring AI 适配
│   ├── agent-group-trigger/      # HTTP、SSE、定时任务、线上 Agent 执行体
│   └── agent-group-types/        # 通用响应与枚举
├── frontend/                     # 用户工作台 + /admin 运营端
├── docs/                         # 运维、压测、复盘文档
└── study/                        # 架构图与面试口述材料
```

会话文件的技术接入以端口形式定义在 `domain.agent.file`，由 `infrastructure` 实现，trigger 面向端口编程。

更细的架构图见 `study/01-系统整体架构图.md`、`study/03-Agent执行引擎框架图.md`、`study/04-额度交易系统架构图.md`。

---

## 本地启动

### 1. 启动依赖

```bash
cd docs/dev-ops
docker compose -f docker-compose-environment.yml up -d
```

### 2. 启动后端

```bash
cd backend
# Windows PowerShell:
# $env:SPRING_PROFILES_ACTIVE="dev"
export SPRING_PROFILES_ACTIVE=dev   # Linux / macOS
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

---

## 推荐演示路径

1. **注册登录** → 查看额度余额、额度流水和可购买额度包。
2. **拼团交易** → 演示活动试算、锁单参团、支付回调、等待成团、成团到账、超时退款等边界，明确区分「支付成功，等待成团」与「额度已到账」。
3. **直接购买** → 演示标准额度包购买，说明它与拼团购买在发额条件上的差异。
4. **运行 Agent** → 默认智能调度，或切换对话助手、深度任务、PPT 生成、图像生成、Skill，观察 SSE 流式输出、模式选择与额度扣减。
5. **文件问答** → 上传附件后提问，验证会话文件 RAG。
6. **运营端** → 查看总览、拼团活动、渠道与库存、模型配置、Skills / MCP、订单核查和退款。
7. **引擎单测**（可选）→ `mvn -pl agent-group-app -am test -Dtest=AgentEngineRoutingTest`，验证 auto 路由与 domain 重规划。
8. **交易压测**（可选）→ `docs/dev-ops/loadtest/`，跑完再把 QPS、P99 记入结果表；未实测的数字不要写进对外材料。

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

**开发与复盘**

- `docs/dev-challenges-and-improvements.md` — 开发问题与改进记录
- `docs/trade-high-concurrency.md` — 交易高并发设计
- `docs/autumn-recruit-evidence.md` — 秋招项目证据材料

**运维与环境**

- `docs/dev-ops/README.md` — 本地 Docker 环境与演示说明
- `docs/dev-ops/loadtest/README.md` — 交易压测脚本与结果记录约定
- `docs/dev-ops/payment-sandbox.md` — 支付宝沙箱检查

**学习与面试**

- `study/01-系统整体架构图.md` — 全局架构（30 秒口述版）
- `study/02-后端DDD分层框架图.md`
- `study/03-Agent执行引擎框架图.md`
- `study/04-额度交易系统架构图.md`
- `study/05-核心业务流程图.md`
- `study/agent-group-interview-questions-200.md`

---

## 简历表述参考

**项目名称备选**

- 多模式 Agent 工作台与拼团式额度交易平台
- Agent 智能执行引擎与拼团营销交易平台
- 面向内容生成场景的 Agent 工作台与额度拼团交易系统

**项目描述（三段式）**

本项目面向内容生成与营销交易场景，以提升 Agent 复杂任务执行稳定性和额度包购买转化为目标；一方面通过 Spring AI 多模式 Agent 工作台提供自动路由、ReAct、Plan-Execute、重规划、反思评估和 SSE 过程可视化能力，另一方面通过拼团式额度交易平台提供活动试算、锁单参团、支付回调、成团结算、退款补偿和额度发放一致性；最终形成“拼团 / 直接购买额度 → 运行 Agent → 扣减额度 → 交易排障”的完整业务闭环。

**核心方案**

- **Agent 执行引擎**：`auto` 智能调度根据任务类型选择 chat / deep / ppt / image / manual-skills 等模式；deep 模式使用 Plan-Execute 拆解任务，并接入失败重规划、反思评估和运行诊断。
- **推理过程可视化**：通过 SSE 推送任务分析、模式选择、执行应用、步骤进度和诊断事件，让复杂任务从“只等结果”变成“可观察过程”。
- **会话文件 RAG**：附件解析后写入 pgvector，用户提问时按会话和文件检索相关片段，再交给大模型生成回答。
- **拼团营销交易**：参考成熟拼团营销模型，将活动、试算、锁单、组队、结算流程改造成“优惠购买 Agent 额度”的业务链路，既能讲营销转化，也能讲交易一致性。
- **额度交易闭环**：直接购买按支付成功发额；拼团购买必须等成团或交易完成后发额；通过订单流水、额度流水、支付幂等键、Outbox 状态事件和 XXL-JOB 补偿任务保证最终一致。
- **并发与可靠性**：拼团侧重点讲锁单幂等、库存占位、队伍名额控制、Redis 分布式锁、失败恢复、超时关闭和退款补偿，避免只把拼团描述成页面玩法。

**完整版（按实际做过的能力裁剪，数字须有评测或压测出处）**

基于 Java 21、Spring Boot 3 与 Spring AI 实现多模式 Agent 工作台与拼团式额度交易平台。Agent 侧支持 `auto` 智能调度及 ReAct、Plan-Execute 等主执行范式，PPT Workflow、Skill Orchestration 等业务编排；deep 模式接入 domain 重规划与反思；全模式 run 结束自动诊断；会话文件 RAG 写入 pgvector；请求级 `requestId` 与运行诊断辅助排障。拼团交易侧围绕额度包购买设计活动试算、锁单参团、支付回调、组队成团、额度到账、退款和状态事件补偿：直接购买在支付成功后发额，拼团购买在成团或交易完成后发额，避免“支付成功但未成团”导致额度提前到账。并通过下单幂等、Redis 分布式锁、库存与队伍名额占位、支付回调防重放、支付单状态幂等、订单流水、额度流水、TradeEventOutbox、RabbitMQ 和 XXL-JOB 补偿任务保证交易状态与额度余额最终一致。引擎路由单测 `AgentEngineRoutingTest` 可复现；交易性能以 `docs/dev-ops/loadtest` 实测为准，未实测的 QPS / P99 不写入简历。

**简洁版**

多模式 Agent 工作台 + 拼团式额度交易平台，覆盖 Agent 智能执行、拼团营销购买、支付、成团结算、退款、额度发放、Outbox 状态事件投递与补偿，适合后端 / AI 应用方向秋招项目表达。

---

**更新时间**：2026-06-27
