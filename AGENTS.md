# Repository Guidelines

## 项目背景与目标
本项目要实现一个电商 `AI Agent`（人工智能智能体）导购系统，核心路径是“意图理解-智能咨询-决策辅助”。系统需要理解商品属性、用户意图和营销规则，通过上传商品详情、售后政策、活动说明等非结构化文档构建专属知识库，再用 `RAG`（检索增强生成）提升回答的准确性和可解释性。

客户端目标是提供接近“豆包”的流式交互体验，支持文字、图片等多模态输入解析，并在回答过程中实时渲染商品卡片、价格、拼团信息和决策依据。项目还需要建设端到端评测闭环，围绕回答准确率、知识检索精度、多轮对话一致性和推荐合理性做定量评估，再反向优化 `Prompt`（提示词）策略和知识库内容。

## 参考项目
本仓库参考三个本地项目的能力，但不是简单合并代码：

- `N:\java_project\xiaoxiongagent`：参考智能体、文件上传、知识库、流式回答、多模态解析等能力。
- `N:\java_project\s-pay-mall-ddd-market`：参考商品下单、支付回调、订单查询、退款处理等交易链路。
- `N:\java_project\group-buy-market`：参考拼团试算、锁单、成团结算、退款补偿和活动规则。

## 项目推进与提交节奏
本项目要参考 `N:\java_project\group-buy-market`（拼团参考项目）的 `Git`（版本控制工具）提交历史节奏，按“小版本、小能力、小提交”循序渐进推进。

- 先做项目骨架，再做环境配置、运行入口、接口对象、错误码、日志、领域模型、仓储接口、业务流程，最后再接复杂能力。
- 不要一开始就铺完整 `MVP`（最小可用版本）或大而全方案；每次只实现当前阶段最小可验证能力。
- 提交说明可以沿用类似 `feat: 第1课,项目内容`（新增第一课项目内容）的格式，让后续能从提交历史看出项目是一步一步写起来的。

## 项目结构
根目录按前后端分开组织：

- `backend`：后端 `Maven`（构建工具）多模块工程，父级 `pom.xml` 聚合后端模块。
- `frontend`：前端工程，承接用户端导购页、运营端页面和后续复杂交互。
- `doc/design`：设计文档。

`backend` 下包含以下模块：

- `agent-group-api`：接口契约和对外模型。
- `agent-group-app`：应用编排、流程组织和运行入口。
- `agent-group-domain`：核心业务逻辑，包含导购、商品、知识库、拼团、交易和评测等领域。
- `agent-group-infrastructure`：数据库、外部服务、向量库和大模型接口等技术适配。
- `agent-group-trigger`：网页接口、任务、消息等入口适配。
- `agent-group-types`：通用枚举、常量、数据对象和值对象。

新增后端测试放在 `backend` 下对应模块的 `src/test/java` 目录。

## 构建与测试
- `cd backend && mvn clean compile`：编译全部后端模块。
- `cd backend && mvn test`：运行全部后端测试。
- `cd backend && mvn clean package`：打包全部后端模块。
- `cd backend && mvn -pl agent-group-domain -am test`：只测试指定模块及其依赖。

父级配置目标版本为 `Java 21`（二十一版本 Java），本地构建时请使用对应 `JDK`（Java 开发工具包）。

## 编码规范
Java 类名使用 `PascalCase`（大驼峰命名），方法和字段使用 `camelCase`（小驼峰命名），常量使用 `UPPER_SNAKE_CASE`（全大写下划线命名）。包名保持小写，优先放在 `com.linrun` 下。领域逻辑尽量留在 `agent-group-domain`，技术接入放在 `agent-group-infrastructure`。

## 提交与安全
当前目录没有可读取的 `Git`（版本控制工具）历史，提交信息建议使用简短祈使句，例如 `Add guide knowledge retrieval`（增加导购知识检索）。不要提交密码、令牌、`API Key`（接口密钥）或 `.env`（环境变量文件）；涉及大模型、支付和数据库配置时优先使用环境变量。
