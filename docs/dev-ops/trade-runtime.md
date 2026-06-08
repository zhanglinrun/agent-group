# 拼团交易运行说明

本文件只补 `dev-ops`（运维部署）视角，用于演示和排障，不放学习笔记。

## 关键链路

1. 支付成功后，后端记录 `GROUP_LOCK_PAID`（拼团名额已支付）流水，并写入 `Outbox`（本地消息表）。
2. 队伍达到成团人数后，结算规则链记录 `GROUP_SETTLED`（成团结算完成），创建 `trade_settlement`（交易结算通知）任务。
3. 退款入口先走退款规则链，再路由到退款策略；支付退款成功后释放拼团名额，并创建 `trade_refund`（交易退款通知）任务。
4. `TradeEventOutboxDispatchJob`（交易事件投递任务）负责把本地事件投递到 `RabbitMQ`（消息队列）。
5. `GroupBuyNotifyTaskJob`（通知补偿任务）负责重试 `HTTP`（网页回调）或 `MQ`（消息队列）通知。
6. `TimeoutRefundJob`（超时退款任务）独立扫描超时未成团订单，把关闭未支付订单和成团失败退款拆成两条补偿链路。

## 补偿任务

| 任务 | 默认开关 | 默认频率 | 作用 |
| --- | --- | --- | --- |
| `TradeTimeoutCompensationJob`（交易超时补偿任务） | `agent.group.trade.timeout-close.enabled`（交易超时关闭开关） | `agent.group.trade.timeout-close.cron`（交易超时关闭定时表达式） | 关闭超时未支付订单。 |
| `TimeoutRefundJob`（超时退款任务） | `agent.group.trade.timeout-refund.enabled`（超时退款开关） | `agent.group.trade.timeout-refund.cron`（超时退款定时表达式） | 处理已支付但超时未成团订单的退款补偿。 |
| `GroupBuyNotifyTaskJob`（通知补偿任务） | `agent.group.notify.job.enabled`（通知任务开关） | `agent.group.notify.job.cron`（通知任务定时表达式） | 批量重试待通知任务。 |
| `DocumentCompensationJob`（文档补偿任务） | `agent.group.knowledge.compensation.enabled`（知识补偿开关） | `agent.group.knowledge.compensation.embedding-cron`（向量补偿定时表达式） | 重试向量入库失败的知识文档。 |

## 消息队列

默认交换机：

```text
agent.group.trade.event.exchange
```

核心队列：

```text
agent.group.trade.event.queue
agent.group.trade.event.team-success.queue
agent.group.trade.event.refund-success.queue
agent.group.trade.event.dlq
```

核心路由：

```text
trade.event.group.group_settled
trade.event.refund.refund_success
agent.group.notify.group-settlement
agent.group.notify.group-refund
```

`TeamSuccessTopicListener`（成团成功监听）只接收成团成功主题；`RefundSuccessTopicListener`（退款成功监听）只接收退款成功主题。通用监听仍保留，用来记录消费流水和死信。

## 动态配置

以下配置写在 `dynamic_config`（动态配置表），演示环境默认 `HTTP`（网页回调）且回调地址为空，表示直接成功：

```text
groupSettlementNotifyType
groupSettlementNotifyUrl
groupSettlementNotifyMQ
groupRefundNotifyType
groupRefundNotifyUrl
groupRefundNotifyMQ
```

要改成消息通知，可把类型改为 `MQ`（消息队列）：

```sql
update dynamic_config
set config_value = 'MQ'
where config_key in ('groupSettlementNotifyType', 'groupRefundNotifyType');
```

代码侧通过 `NotifyConfig`（通知配置值对象）统一承载通知类型、`MQ`（消息队列）路由和 `HTTP`（网页回调）地址，再交给 `TradeNotifyPort`（外部交易通知端口）派发。这样面试里可以把它讲成“通知规则可配置、通知动作走端口隔离”，不是把通知逻辑硬编码在业务服务里。

## 指定任务执行

通知任务支持批量补偿、按队伍执行和按任务编号执行三种方式：

```text
execNotifyJob()
execNotifyJob(teamId)
execNotifyTask(uuid)
```

按 `uuid`（任务编号）执行时会先加分布式锁，适合演示“单任务重试”和交易通知闭环。

## Agent 检索进度

`Agent`（智能体）流式接口新增 `retrieval_progress`（检索进度）事件。一次知识检索会先返回 `route`（查询路由）阶段，再返回 `aggregate`（聚合排序）阶段：

```text
route -> query routed
aggregate -> references ranked
```

查询路由会把问题分到 `trade_system`（交易系统）、`market_system`（营销系统）、`knowledge_base`（知识库）或 `hybrid`（混合检索）。这样前端可以展示“为什么查订单走交易表、为什么查售后走知识库”。

## 知识补偿与工具化

文档上传时，如果 `Spring AI VectorStore`（向量存储接口）写入失败，文档会标记为 `EMBEDDING_FAILED`（向量入库失败），再由定时任务或接口重试：

```text
POST /api/v1/knowledge/vector/compensate-failed-embedding?limit=20
```

交易状态、退款状态、额度流水只作为后台排障和运营核对数据，不再作为用户对话 `Agent`（智能体）的工具暴露。

用户侧主要使用学术问答、文件理解、数据分析、图像生成和技能执行；交易相关信息在购买页、订单页和后台管理端查看。
## 排障检查

```powershell
docker ps --filter "name=agent-group"
docker logs agent-group-rabbitmq --tail 100
docker exec -i agent-group-mysql mysql --default-character-set=utf8mb4 -uroot -pagent_group_dev agent_group -e "select notify_category, notify_status, notify_count, uuid from notify_task order by id desc limit 20;"
docker exec -i agent-group-mysql mysql --default-character-set=utf8mb4 -uroot -pagent_group_dev agent_group -e "select biz_type, event_type, send_status, send_count from trade_event_outbox order by id desc limit 20;"
```

状态判断：

- `notify_status = 0` 表示待通知，`1` 表示成功，`2` 表示等待重试，`3` 表示失败，`4` 表示处理中。
- `send_status = 3` 或通知长期 `RETRY`（重试）时，优先检查 `RabbitMQ`（消息队列）连通性和动态配置。
- 成团后没有通知任务时，先查订单是否真的进入 `GROUP_SETTLED`（成团结算完成）状态。
