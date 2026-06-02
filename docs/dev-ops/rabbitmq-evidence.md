# RabbitMQ 联调和压测证据

这组脚本用于验证 `trade_event_outbox`（事务消息表）到 `RabbitMQ`（消息队列）再到 `trade_event_consume_record`（消费记录表）的真实链路。

## 前置条件

- 已启动 `MySQL`（关系型数据库）、`RabbitMQ`（消息队列）和后端服务。
- 后端开启 `agent.group.rabbit.enabled=true`（启用 RabbitMQ）。
- 默认使用 `docs/dev-ops/docker-compose-environment.yml`（本地依赖编排）里的演示账号。

## 单条联调

```powershell
cd E:\javaproject\agent-group
.\docs\dev-ops\scripts\rabbitmq-smoke.ps1
```

脚本会插入一条 `PAY_SUCCESS`（支付成功）事件，调用 `/api/v1/trade/event/outbox/exec_job`（事务消息投递任务接口），再确认：

- `trade_event_outbox.send_status = 1`（已投递）。
- `trade_event_consume_record.consume_status = 1`（已消费）。
- `RabbitMQ`（消息队列）队列没有异常堆积。

## 批量压测

```powershell
cd E:\javaproject\agent-group
.\docs\dev-ops\scripts\rabbitmq-load.ps1 -Count 200
```

脚本会输出吞吐、平均延迟、最大延迟、死信数和未完成数。结果会保存到：

```text
docs\dev-ops\reports
```

生成的 `json`（结构化结果）和 `md`（可读报告）可以直接作为联调和压测证据。

## 使用本机 mysql 命令

默认脚本通过 `docker exec`（进入容器执行命令）访问 `agent-group-mysql`。如果本机安装了 `mysql`（数据库命令行），可以改为：

```powershell
.\docs\dev-ops\scripts\rabbitmq-smoke.ps1 -UseLocalMysql
.\docs\dev-ops\scripts\rabbitmq-load.ps1 -UseLocalMysql -Count 200
```
