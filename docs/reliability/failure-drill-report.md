# 故障演练报告

## 演练目标

验证导购、知识检索、拼团锁单、支付回调和交易补偿在关键依赖异常时是否能给出明确响应，并通过监控指标定位问题。

## 演练清单

| 编号 | 故障场景 | 注入方式 | 预期结果 | 监控指标 |
| --- | --- | --- | --- | --- |
| F01 | 大模型不可用 | 临时设置错误的 `AGENT_GROUP_LLM_API_KEY`（模型接口密钥）或断开模型网络 | 导购返回基于资料的兜底回答，不编造价格、库存、订单状态 | `agent_group_guide_fallback_total`（模型兜底次数）增加 |
| F02 | `pgvector`（向量库）不可用 | 停止 `agent-group-pgvector` 容器 | 向量召回为空，系统继续使用关键词和商品数据组织上下文 | 向量检索异常指标增加，导购不应整体崩溃 |
| F03 | `Redis`（缓存数据库）不可用 | 停止 `agent-group-redis` 容器 | 会话和停止生成能力受影响，支付防重放应暴露明确错误或走保护逻辑 | `http_server_requests_seconds`（接口耗时）和错误码变化 |
| F04 | `RabbitMQ`（消息队列）不可用 | 停止 `agent-group-rabbitmq` 容器 | 交易事件进入 `Outbox`（事务消息表）待重试，主交易状态不应丢失 | 交易事件待发送数、死信数、重试数 |
| F05 | 支付回调重复 | 对同一订单连续发送两次 `/api/v1/payment/webhook` | 第二次按幂等结果返回，不重复推进订单、不重复扣款 | `agent_group_payment_webhook_total{status="duplicate_completed"}` 增加 |
| F06 | 拼团库存并发冲突 | 用 `JMeter`（压测工具）高并发锁同一活动 | 不超卖，库存不足或队伍满员返回业务错误 | `agent_group_group_buy_lock_total` 按状态分布变化 |

## 操作记录

### F01 大模型不可用

- 操作：待填写。
- 系统响应：待填写。
- 监控现象：待填写。
- 恢复方式：恢复正确环境变量并重启后端。
- 结论：待填写。

### F02 `pgvector`（向量库）不可用

- 操作：

```powershell
docker stop agent-group-pgvector
```

- 系统响应：待填写。
- 监控现象：待填写。
- 恢复方式：

```powershell
docker start agent-group-pgvector
```

- 结论：待填写。

### F03 `Redis`（缓存数据库）不可用

- 操作：

```powershell
docker stop agent-group-redis
```

- 系统响应：待填写。
- 监控现象：待填写。
- 恢复方式：

```powershell
docker start agent-group-redis
```

- 结论：待填写。

### F04 `RabbitMQ`（消息队列）不可用

- 操作：

```powershell
docker stop agent-group-rabbitmq
```

- 系统响应：待填写。
- 监控现象：待填写。
- 恢复方式：

```powershell
docker start agent-group-rabbitmq
```

- 结论：待填写。

### F05 支付回调重复

- 操作：使用 `JMeter`（压测工具）第三个线程组，或手动对同一 `orderId`（订单编号）和 `payOrderId`（支付单编号）发送两次回调。
- 系统响应：待填写。
- 监控现象：待填写。
- 恢复方式：无需恢复，属于幂等验证。
- 结论：待填写。

### F06 拼团库存并发冲突

- 操作：提高 `LockThreads`（锁单线程数），观察同一活动库存消耗和失败状态。
- 系统响应：待填写。
- 监控现象：待填写。
- 恢复方式：重置演示数据。
- 结论：待填写。

## 面试讲法

这个项目不是只演示一次导购问答，而是把故障场景拆成模型、向量库、缓存、消息、支付和库存 6 类。每类都能说明“故障如何注入、系统如何响应、监控如何定位、恢复后如何验证数据一致”。
