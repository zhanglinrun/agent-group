# 拼团交易运行说明

本文件只补 `dev-ops`（运维部署）视角，用于演示和排障，不放学习笔记。

## 关键链路

1. 支付成功后，后端记录 `GROUP_LOCK_PAID`（拼团名额已支付）流水，并写入 `Outbox`（本地消息表）。
2. 队伍达到成团人数后，结算规则链记录 `GROUP_SETTLED`（成团结算完成），创建 `trade_settlement`（交易结算通知）任务。
3. 退款入口先走退款规则链，再路由到退款策略；支付退款成功后释放拼团名额，并创建 `trade_refund`（交易退款通知）任务。
4. `TradeEventOutboxDispatchJob`（交易事件投递任务）负责把本地事件投递到 `RabbitMQ`（消息队列）。
5. `GroupBuyNotifyTaskJob`（通知补偿任务）负责重试 `HTTP`（网页回调）或 `MQ`（消息队列）通知。

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
