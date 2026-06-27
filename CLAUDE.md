# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

仓库根目录还有一份 `AGENTS.md`，里面是交流方式、文风和项目表达口径的详细约定，本文件只补充开发时最常用的信息，两份要一起遵守。

## 项目定位

通用多模式 `Agent`（智能体）平台，核心是 **Agent 智能执行引擎 + 额度交易系统**，不是普通聊天机器人。后端是 `Java 21` + `Spring Boot 3` + `Spring AI` 的 `Maven`（构建工具）多模块工程，前端是 `React 19` + `Vite`。

## 常用命令

后端（都在 `backend` 目录下执行）：

```bash
mvn -pl agent-group-app -am test     # 默认验证命令：测试应用模块及其依赖
mvn test                             # 全量测试
mvn clean compile                    # 全量编译
mvn clean package                    # 打包
mvn -pl agent-group-app -am test -Dtest=TradeOrderServiceTest          # 跑单个测试类
mvn -pl agent-group-app -am test -Dtest=TradeOrderServiceTest#方法名   # 跑单个测试方法
mvn -pl agent-group-app -am test -Dtest=AgentEngineRoutingTest   # Agent 引擎路由与 domain 重规划单测
```

所有后端测试都放在 `agent-group-app` 模块的 `src/test` 下，所以单测也要带 `-pl agent-group-app -am`。交易接口的压测脚本和结果记录约定在 `docs/dev-ops/loadtest` 下。

前端（都在 `frontend` 目录下执行）：

```bash
npm run dev      # 开发服务器，地址 http://localhost:5173/
npm run test     # vitest 单测
npm run lint     # eslint 检查
npm run build    # 构建
```

本地运行环境依赖（MySQL、Redis、pgvector、RabbitMQ、MinIO 等）：

```bash
cd docs/dev-ops
docker compose -f docker-compose-environment.yml up -d
```

后端本地启动：在 `backend` 目录下设置 `SPRING_PROFILES_ACTIVE=dev` 后执行 `mvn -pl agent-group-app -am spring-boot:run`。

## 后端模块结构

DDD 分层，依赖方向是 trigger → domain ← infrastructure，app 负责组装启动：

- `agent-group-api`：接口契约和对外模型
- `agent-group-app`：启动入口、配置，以及全部测试
- `agent-group-domain`：核心业务逻辑，按业务分包：`academic`（Agent 运行时、账本、项目工作区，代码包名 legacy）、`account`（账号与额度）、`agent`（智能体定义与会话文件端口）、`groupbuy`（拼团）、`trade`（交易支付）、`support`（链路追踪等支撑）
- `agent-group-infrastructure`：数据库、缓存、消息、对象存储、Spring AI 和大模型适配
- `agent-group-trigger`：网页接口、定时任务、消息监听、`SSE`（流式输出）
- `agent-group-types`：通用响应、异常和枚举

包名统一在 `com.linrun` 下。领域逻辑放 domain，技术接入放 infrastructure，不要混。

## Agent 执行引擎（domain/academic/runtime）

这是项目的核心特色，理解结构再动手：

- `mode`：执行策略选择——当前主 `Agent`（智能体）架构是 `ReAct`（思考-行动循环）和 `Plan-Execute`（规划-执行）；`PPT Workflow`（PPT 工作流）和 `Skill Orchestration`（技能编排）是业务编排策略，不作为标准 `Agent`（智能体）架构模式包装
- `reasoning`：任务分析、智能重规划（失败时分析原因、复用已完成步骤）、反思评估（质量低于阈值触发重规划）
- `orchestration` / `executor`：计划编排与步骤执行
- `tool`：工具调用
- `diagnosis`：异常诊断（执行耗时、工具失败、额度异常、频繁重规划等）
- `support/trace`：基于 `TraceId`/`SpanId` 的全链路追踪

注意：当前线上对话的实际执行体在 `trigger/agent/agent/` 下，由 `AcademicAgentNativeService` 按 `executionAgentType` 调用。`auto` 模式经 `UnifiedAgentOrchestrator` 自动选择并推送 `execution_applied`。`domain/academic/runtime` 提供模式选择、重规划、反思、诊断策略，deep 模式经 `PlanExecuteDomainBridge` 接入；全模式 run 结束推送诊断。详细说明见 `.trellis/spec/backend/directory-structure.md`。

大模型主链路优先使用 Spring AI 的 `ChatClient`、`EmbeddingModel`、`VectorStore`（写入 pgvector）；原有手写 OpenAPI 客户端和本地向量是回退链路，避免没有密钥或向量库不可用时演示中断，不要删。

## 交易与额度规则（业务红线）

- 额度只能由后端交易状态发放，前端和 Agent 不能直接决定额度到账。
- 直接购买：支付状态变为 `PAY_SUCCESS`（支付成功）后才发额度。
- 拼团购买：支付成功只表示名额已支付，必须等订单状态变为 `GROUP_SETTLED`（拼团已成团）或 `DEAL_DONE`（交易完成）后才给同团用户发额度；未成团不能发。
- 退款或误发要记录额度流水并回滚余额。
- 一致性靠订单流水、额度流水、支付幂等键，可靠性靠本地消息表（`TradeEventOutbox`）+ 定时补偿。

## 工作方式约定

- 每次只实现当前阶段最小可验证的能力，不要一上来铺大而全的方案。
- 提交信息用类似 `feat: 运行基线整理` 的格式（中文描述）。
- 历史上多次出现 UTF-8 编码问题（见提交记录），编辑含中文的文件时注意保持 UTF-8 编码。
- 大模型、支付、数据库配置优先用环境变量，不要提交 `.env` 和任何密钥。
