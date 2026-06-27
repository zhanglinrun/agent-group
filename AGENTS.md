# AGENTS.md

## 交流原则
- 默认用简短、自然、能直接看懂的中文表达。
- 不要堆砌函数名、代码行号、文件路径等底层细节，也不要用晦涩黑话。
- 文件路径只写正常可读的文本，不要做成可点击链接。
- 如果需要引用依据，尽量写出原文内容。

## 非中文内容写法
- 所有非中文内容出现时，一律写成：`英文内容`（中文解释）。
- 不要拆成两行去解释。
- 例如：`RAG`（检索增强生成）、`Prompt`（提示词）、`Java`（后端语言）。

## 默认工作方式
- 默认一次性完成当前任务，再汇报结果。
- 除非用户明确要求分步确认、暂停、只讨论方案，或者任务本身有关键不确定点，否则不要把任务拆成"先做一点，再问要不要继续"。
- 完成后直接汇报结果，不要加"如果你愿意，我下一步可以……"这类收尾句。

## 输出方式
- 默认先给一句结论，再补最多 3 个短点。
- 每次只回答用户当前这个问题，不主动扩展到下一个问题。
- 除非用户明确说"展开"，否则不要写成长篇方案树。
- 如果回答超过 150 字，先压缩再输出。
- 当用户说"一个一个来"时，后续每次只讨论一个决策点。

## 文风要求
- 默认写成温和、自然、像协作说明的中文。
- 少用明显的自动生成式开头，比如"值得注意的是""总而言之"。
- 写 `README`（说明文档）、说明文档、汇报文档时，先参考同目录下用户自己写过的文档语气。
- 默认优先用朴素词，比如"读取、接收、整理、使用、生成、更新、传入、显示、保存"。

## 先问清楚的情况
- 如果对任务内容或需求有关键不清楚的地方，要先停下来问清楚，再继续。

## 安全要求
- 不要把密码、`API Key`（接口密钥）、令牌等敏感信息提交到 `Git`（版本控制工具）。
- 提交前确认没有把秘密信息带进去。
- 不要提交 `.env`（环境变量文件）。
- 不要硬编码凭据，优先使用环境变量。

---

# Repository Guidelines

## 项目背景与目标
面向通用对话与内容生成场景的 `Agent`（智能体）平台，核心定位是 **Agent 智能执行引擎 + 额度交易系统**。

不是简单的聊天机器人，重点在 `Agent`（智能体）的智能化能力：
- 执行策略选择：当前主 `Agent`（智能体）架构是 `ReAct`（思考-行动循环）和 `Plan-Execute`（规划-执行）；`PPT Workflow`（PPT 工作流）和 `Skill Orchestration`（技能编排）是业务执行策略，不包装成标准 `Agent`（智能体）架构模式
- 智能重规划：步骤失败时分析原因并生成新计划，复用已完成步骤
- 反思评估：自动评估执行质量并触发优化
- 推理可视化：类似 `OpenAI o1`（o1 模型）的思考链展示
- 全链路追踪：记录从用户请求到工具调用的完整执行过程
- 异常诊断：自动检测执行耗时、工具失败、额度异常等问题

额度交易系统是支撑 `Agent`（智能体）运行的基础设施，通过支付、拼团、退款和额度发放闭环，保证交易状态与额度余额一致性。用户购买额度后，可以调用对话问答、文件理解、深度任务、`PPT`（演示文稿）生成、图像生成和技能编排等能力。

秋招表达时优先聚焦 `Agent`（智能体）的智能化能力（多模式、重规划、反思、追踪、诊断），不要泛化成普通聊天机器人或单纯支付项目。

## 当前技术定位

- 项目当前定位为：Java 后端工程能力 + Spring AI + **Agent 智能执行引擎**。
- 核心特色：不是简单聊天机器人，而是**多模式执行引擎 + 智能重规划 + 反思评估 + 全链路追踪**。
- 大模型主链路优先使用 Spring AI ChatClient、EmbeddingModel 和 VectorStore。
- 原有手写 OpenAPI 客户端保留为回退链路，避免没有模型密钥或向量库不可用时演示中断。
- 会话文件向量优先走 Spring AI VectorStore 写入 pgvector（表 `vector_file_info`），同时保留本地向量回退。

### 项目亮点

优先表达为：
1. **执行策略选择**：默认 `auto` 智能调度；支持 ReAct、Plan-Execute 两类主 Agent 执行范式，PPT Workflow、Skill Orchestration 承载业务编排；路由单测 23 条用例断言准确率 ≥ 90%
2. **智能重规划策略**：deep 模式步骤失败时经 `PlanExecuteDomainBridge` 走 domain 策略分析原因并重试，另含 LLM 多轮 replan
3. **反思与评估机制**：deep 模式 domain 规则反思（`source=domain_rule`）+ LLM critique；质量低触发继续重规划
4. **推理过程可视化**：SSE 推送 `task_analysis`、`mode_selection`、`execution_applied` 及思考链 Timeline
5. **全链路追踪**：基于 TraceId/SpanId 记录完整调用链，支持 Span 嵌套和标签传播
6. **异常诊断服务**：每次 run 结束推送 `diagnosis_delta`；检测慢执行、工具失败、额度异常、频繁重规划、执行异常
7. **交易状态一致性**：通过订单流水、额度流水、支付幂等键保证额度、订单、支付、拼团状态一致性

> 亮点中的百分比、成功率等数字须有单测或压测出处；路由准确率来自 `AgentEngineRoutingTest`，不要包装成线上 LLM 指标。

### 秋招项目表达

- 优先聚焦"**Agent 智能执行引擎 + 额度交易系统**"
- 强调 Agent 的**智能化能力**：多模式、重规划、反思、追踪、诊断
- 不要泛化成普通聊天机器人或单纯支付项目
- 用户对话 Agent 主要服务通用问答、文件理解、内容生成和多模式任务编排

## 交易与额度规则
- 额度只能由后端交易状态发放，前端和 `Agent`（智能体）不能直接决定额度到账。
- 直接购买：支付状态变为 `PAY_SUCCESS`（支付成功）后，可以给用户发放对应额度。
- 拼团购买：支付成功只表示名额已支付，必须等订单状态变为 `GROUP_SETTLED`（拼团已成团）或 `DEAL_DONE`（交易完成）后，才能给同团用户发放额度。
- 未成团拼团单不能发放额度；若发生退款或误发，需要记录额度流水并回滚余额。
- 前端提示要区分"支付成功，等待成团"和"额度已到账"，不能把拼团支付成功直接展示成到账。
- 涉及订单、支付、拼团、退款、额度流水的判断，优先以后台交易记录和管理端排障数据为准，不让用户对话 `Agent`（智能体）替代后台查账。
- 支付宝官方沙箱是否就绪，以支付网关状态接口和沙箱检查脚本结果为准；密钥、公网回调和缺失项都要作为检查证据。

## 项目结构
- `backend`（后端目录）：`Maven`（构建工具）多模块工程。
- `frontend`（前端目录）：用户端 `Agent`（智能体）工作台、额度购买页、运营端页面和演示交互。
- `docs`（文档目录）：运行环境、监控和项目复盘材料。

后端模块职责：
- `agent-group-api`（接口模块）：接口契约和对外模型。
- `agent-group-app`（应用模块）：启动入口、配置和测试承载。
- `agent-group-domain`（领域模块）：`Agent`（智能体）、账号、额度、拼团、交易、评测等核心业务逻辑。
- `agent-group-infrastructure`（基础设施模块）：数据库、缓存、消息、对象存储、`Spring AI`（Spring 人工智能框架）和大模型适配。
- `agent-group-trigger`（入口模块）：网页接口、任务、消息监听和流式输出。
- `agent-group-types`（通用模块）：通用响应、异常和枚举。

## 构建与测试
- 默认后端验证命令：`cd backend && mvn -pl agent-group-app -am test`（测试应用模块及依赖模块）。
- 全量编译命令：`cd backend && mvn clean compile`（编译全部后端模块）。
- 全量测试命令：`cd backend && mvn test`（运行全部后端测试）。
- 打包命令：`cd backend && mvn clean package`（打包全部后端模块）。
- 父级配置目标版本为 `Java 21`（二十一版 Java），本地构建时使用对应 `JDK`（Java 开发工具包）。

## 编码规范
- Java 类名使用 `PascalCase`（大驼峰命名）。
- 方法和字段使用 `camelCase`（小驼峰命名）。
- 常量使用 `UPPER_SNAKE_CASE`（全大写下划线命名）。
- 包名保持小写，优先放在 `com.linrun` 下。
- 领域逻辑尽量留在 `agent-group-domain`（领域模块），技术接入放在 `agent-group-infrastructure`（基础设施模块）。

## 提交与安全
- 提交信息建议使用类似 `feat: 运行基线整理`（新增运行基线整理）的格式。
- 不要一开始就铺完整 `MVP`（最小可用版本）或大而全方案；每次只实现当前阶段最小可验证能力。
- 不要提交密码、令牌、`API Key`（接口密钥）或 `.env`（环境变量文件）。
- 涉及大模型、支付和数据库配置时优先使用环境变量。

<!-- TRELLIS:START -->
# Trellis Instructions

These instructions are for AI assistants working in this project.

This project is managed by Trellis. The working knowledge you need lives under `.trellis/`:

- `.trellis/workflow.md` — development phases, when to create tasks, skill routing
- `.trellis/spec/` — package- and layer-scoped coding guidelines (read before writing code in a given layer)
- `.trellis/workspace/` — per-developer journals and session traces
- `.trellis/tasks/` — active and archived tasks (PRDs, research, jsonl context)

If a Trellis command is available on your platform (e.g. `/trellis:finish-work`, `/trellis:continue`), prefer it over manual steps. Not every platform exposes every command.

If you're using Codex or another agent-capable tool, additional project-scoped helpers may live in:
- `.agents/skills/` — reusable Trellis skills
- `.codex/agents/` — optional custom subagents

Managed by Trellis. Edits outside this block are preserved; edits inside may be overwritten by a future `trellis update`.

<!-- TRELLIS:END -->
