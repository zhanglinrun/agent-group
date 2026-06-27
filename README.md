# 基于 Spring AI 的 Agent 智能执行引擎与额度交易系统

通用多模式 Agent 工作台（对话、文件、深度任务、PPT、生图、技能编排）+ **额度交易闭环**。核心不是普通聊天机器人，而是 **Agent 智能执行引擎 + 额度交易系统**，适合在简历中表述为：**Spring AI 多模式 Agent 执行引擎 + 额度交易系统**。

---

## 项目定位

| 子系统 | 做什么 |
| --- | --- |
| **Agent 子系统** | 流式对话、多模式执行、会话文件 RAG（检索增强生成）、项目工作区、全链路追踪 |
| **交易子系统** | 账号与额度、直接购买、拼团、支付回调、退款、本地消息表补偿 |

**Maven 多模块 DDD 分层**：`agent-group-app` 单进程启动，依赖方向 `trigger → domain ← infrastructure`。

---

## 核心能力

### 线上 Agent（用户对话主链路）

入口在 `AcademicAgentNativeService`，执行体在 `trigger/agent/agent/`，经 SSE（流式输出）推送到前端工作台。

用户端可选模式（`/` 工作台，默认 `auto` 智能调度）：

| 模式 | 执行范式 | 说明 |
| --- | --- | --- |
| `auto` | 自动选择 | 规则推断任务类型，路由到 deep / ppt / chat / file / manual-skills 等 |
| `chat` | ReAct（思考-行动循环） | 通用问答、轻量工具调用 |
| `deep` | Plan-Execute（规划-执行） | 计划拆解、分步执行、失败重规划、反思评估 |
| `ppt` | PPT Workflow（PPT 工作流） | 需求澄清 → 大纲 → 搜索 → 模板 → 渲染 |
| `image` | ReAct + 图像工具 | 图像生成、图生图 |
| `manual-skills` | Skill Orchestration（技能编排） | 手动选择技能并执行编排 |

另有 `file`（文件问答）、`data`（表格 RAG / NL2SQL）、`skills` 等模式，主要通过工作区或附件场景触发，不在主工作台模式栏默认展示。

**会话文件 RAG**：用户上传附件后解析、切片，经 `EmbeddingPort` 写入 pgvector 表 `vector_file_info`，供 file 模式语义检索。独立运营知识库文档管理已移除，不再提供知识库 CRUD 后台。

**横切能力**：TraceId / SpanId 全链路追踪；对话前额度预检、执行后扣减流水；每次 run 结束推送诊断（`diagnosis_delta`）。

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

### 额度交易系统

- **直接购买**：支付成功（`PAY_SUCCESS`）后发放额度。
- **拼团购买**：支付成功只表示名额已支付；须等订单变为 `GROUP_SETTLED`（拼团已成团）或 `DEAL_DONE`（交易完成）后才发额；未成团不发额。
- **一致性**：订单流水、额度流水、支付幂等键；`TradeEventOutbox` + RabbitMQ + 定时补偿处理回调、成团、退款。
- **拼团**：活动试算、锁单占位、队伍名额、Redis 分布式锁、成团结算、未成团退款。
- **Agent 扣额**：仅后端根据交易状态发额；前端与 Agent 不能直接改余额。

---

## 技术栈

**后端**：Java 21、Spring Boot 3.5、Spring AI、MyBatis、DDD 分层

**前端**：React 19、Vite、React Router 7

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
| http://localhost:5173/admin | 运营端（模型、拼团、交易监控） |
| http://localhost:8080 | 后端 API（dev 默认） |
| Grafana | 见 `docs/dev-ops` 中 docker-compose 端口映射 |

---

## 推荐演示路径

1. **注册登录** → 查看额度余额与流水。
2. **购买额度** → 分别演示直接购买与拼团（区分「支付成功，等待成团」与「额度已到账」）。
3. **运行 Agent** → 默认 `auto` 或切换 chat / deep / ppt / image / manual-skills，观察 SSE 流式输出、模式选择与额度扣减。
4. **文件问答** → 上传附件后提问，验证会话文件 RAG。
5. **运营端** → 模型配置、拼团活动、订单一致性核查、Skills / MCP 管理。
6. **引擎单测**（可选）→ `mvn -pl agent-group-app -am test -Dtest=AgentEngineRoutingTest`，验证 auto 路由与 domain 重规划。
7. **交易压测**（可选）→ `docs/dev-ops/loadtest/`，跑完再把 QPS、P99 记入结果表；未实测的数字不要写进对外材料。

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

**完整版（按实际做过的能力裁剪，数字须有评测或压测出处）**

基于 Java 21、Spring Boot 3 与 Spring AI 实现多模式 Agent 执行引擎与额度交易系统。线上支持 `auto` 智能调度及 ReAct、Plan-Execute 等主执行范式，PPT Workflow、Skill Orchestration 等业务编排；deep 模式接入 domain 重规划与反思；全模式 run 结束自动诊断；会话文件 RAG 写入 pgvector；TraceId 全链路追踪。交易侧实现直接购买与拼团发额规则、支付回调幂等、TradeEventOutbox 异步补偿与拼团分布式锁控库存。引擎路由单测 `AgentEngineRoutingTest` 可复现；交易性能以 `docs/dev-ops/loadtest` 实测为准。

**简洁版**

Spring AI 多模式 Agent 执行 + 额度交易闭环（支付、拼团、退款、Outbox 补偿），DDD 分层工程，适合后端 / AI 应用方向秋招项目表达。

---

**更新时间**：2026-06-27
