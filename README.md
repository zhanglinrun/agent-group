# Agent Group：电商可信导购 Agent

## 项目定位

本项目是一个面向电商交易链路的 `AI Agent`（人工智能智能体）导购系统，不只做问答，而是基于 `Spring AI`（Spring 人工智能框架）把“意图理解、知识检索、商品推荐、拼团试算、订单查询、支付回调、质量评测、运行监控”串成闭环。

适合在简历中定位为：`Java`（后端语言）后端 + `AI`（人工智能）应用工程项目。

## 核心亮点

1. 可信导购链路：回答必须基于知识片段、商品数据和拼团试算结果，价格、库存、订单状态不允许由模型编造。
2. 工具型 `Agent`（智能体）：后端维护工具白名单，支持知识检索、商品推荐、拼团试算、订单查询等工具，流式返回工具计划和执行过程。
3. 电商交易闭环：支持拼团锁单、支付单创建、模拟支付回调、订单状态流转、退款和补偿任务。
4. 知识库闭环：上传商品详情、活动规则、售后政策后，解析、切片、向量化并优先通过 `Spring AI VectorStore`（向量存储接口）写入 `pgvector`（向量库），文档原件保存到 `MinIO`（对象存储）。
5. 质量评测闭环：评测用例覆盖检索命中、回答准确、推荐合理、多轮一致、工具调用、工具参数和工具结果引用。
6. 可观测性：接入 `Prometheus`（指标采集工具）和 `Grafana`（指标看板工具），观察工具调用、工具耗时、模型耗时、`Spring AI` 调用回退次数和向量检索状态。

## 架构概览

```mermaid
flowchart LR
    U[用户端流式对话] --> C[导购流式控制器]
    C --> P[工具规划器]
    P --> T[工具白名单]
    T --> K[知识检索]
    T --> R[商品推荐]
    T --> G[拼团试算]
    T --> O[订单查询]
    K --> V[向量库]
    K --> M[对象存储]
    R --> D[关系库]
    G --> D
    O --> D
    D --> E[评测报告]
    C --> S[监控指标]
```

## 技术栈

- 后端：`Java 21`（后端语言）、`Spring Boot 3`（后端框架）、`Spring AI`（Spring 人工智能框架）、`MyBatis`（数据访问框架）
- 存储：`MySQL`（关系型数据库）、`Redis`（缓存数据库）、`pgvector`（向量库）、`MinIO`（对象存储）
- 消息与任务：`RabbitMQ`（消息队列）、定时补偿任务、交易事件 `Outbox`（事务消息表）
- 大模型链路：`Spring AI ChatClient`（聊天客户端）、`EmbeddingModel`（向量模型接口）、`VectorStore`（向量存储接口）、`RAG`（检索增强生成）、工具调用、流式输出、多模态图片解析
- 工程治理：`Actuator`（应用监控端点）、`Prometheus`（指标采集工具）、`Grafana`（指标看板工具）、批量评测

## 本地启动

```powershell
cd docs/dev-ops
docker compose -f docker-compose-environment.yml up -d
```

```powershell
$env:AGENT_GROUP_EVALUATE_CASE_FILE="E:\javaproject\agent-group\docs\sample-knowledge\evaluation-cases.json"
cd E:\javaproject\agent-group\backend
mvn -pl agent-group-app -am spring-boot:run
```

浏览器打开：

- `frontend/index.html`（用户端导购演示）
- `frontend/admin.html`（运营端知识库、评测和交易监控）
- `http://127.0.0.1:13000`（`Grafana` 指标看板）

## 推荐演示路径

1. 在用户端点击“运行示例”，观察流式回答、工具计划、知识片段和商品卡片。
2. 点击“拼团购买”，观察锁单、支付单、模拟回调和订单状态流转。
3. 在运营端上传 `docs/sample-knowledge` 下的知识文档，验证文档入库、切片和向量检索。
4. 执行评测，观察工具调用正确率、工具参数正确率、工具结果引用率和回答准确率。
5. 打开 `Grafana`（指标看板工具），查看工具调用速率、工具平均耗时、模型耗时和回退次数。

## 简历表述

基于 `Java 21`（后端语言）、`Spring Boot 3`（后端框架）和 `Spring AI`（Spring 人工智能框架）实现电商可信导购 `AI Agent`（人工智能智能体），将商品知识库、工具调用、拼团交易、支付回调和离线评测串成闭环；通过工具白名单、检索依据展示、评测指标和监控看板降低大模型幻觉风险。
