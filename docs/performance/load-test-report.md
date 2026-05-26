# `JMeter`（压测工具）压测报告

## 测试目标

验证导购、拼团锁单、支付回调三条核心链路在本地真实依赖下的稳定性，重点观察错误率、耗时、吞吐和交易一致性。

## 测试环境

| 项目 | 内容 |
| --- | --- |
| 后端 | `Java 21`（后端语言） + `Spring Boot 3`（后端框架） |
| 依赖 | `MySQL`（关系型数据库）、`Redis`（缓存数据库）、`RabbitMQ`（消息队列）、`pgvector`（向量库）、`MinIO`（对象存储） |
| 监控 | `Prometheus`（指标采集工具）、`Grafana`（指标看板工具）、`Actuator`（应用监控端点） |
| 压测工具 | `JMeter`（压测工具） |
| 机器配置 | `AMD Ryzen 5 9600X`（处理器），6 核 12 线程，47.1GB 内存，`Windows 11 Pro`（专业版） |
| 模型配置 | 配置模型为 `qwen3.6-plus`（通义千问模型），本次因本机密钥不可用触发兜底，支付使用 `MOCK_PAY`（模拟支付） |

## 压测场景

| 场景 | 接口链路 | 验证点 |
| --- | --- | --- |
| 导购流式 | `/api/v1/agent/guide/stream` | 返回 `done`（完成事件）和 `decisionId`（导购决策编号） |
| 拼团锁单 | 导购流式 -> `/api/v1/group/trade/lock` | 下单必须携带真实导购凭证，金额来自后端校验 |
| 支付回调 | 导购流式 -> 拼团锁单 -> `/api/v1/payment/create` -> `/api/v1/payment/webhook` | 支付成功、重复回调幂等返回 |

## 执行命令

```powershell
cd E:\javaproject\agent-group
$env:JMETER_HOME="C:\Users\zlr\AppData\Local\Programs\JMeter"
.\docs\performance\jmeter\run-jmeter.ps1 -HostName 127.0.0.1 -Port 18080 -GuideThreads 1 -LockThreads 1 -PaymentThreads 1 -RampSeconds 5 -Loops 1
.\docs\performance\jmeter\run-jmeter.ps1 -HostName 127.0.0.1 -Port 18080 -GuideThreads 10 -LockThreads 10 -PaymentThreads 10 -RampSeconds 30 -Loops 1
```

执行前修正了本地演示数据：`A10001`（拼团活动编号）已过期，压测前将活动时间延长到当前时间后 7 天，并把压测库存调整为 1000，避免压测被历史演示数据拦截。

## 结果记录

| 并发档位 | 场景 | 样本数 | 错误率 | 平均耗时 | `P95`（九十五分位耗时） | `P99`（九十九分位耗时） | 吞吐 |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 全链路冒烟 | 8 | 0% | 181ms | - | - | 10.2/s |
| 10 | 导购流式 | 10 | 0% | 173.6ms | 222ms | 222ms | 0.37/s |
| 10 | 拼团锁单 | 20 | 0% | 116.65ms | 210.5ms | 213ms | 0.74/s |
| 10 | 支付创建 | 10 | 0% | 38.9ms | 79ms | 79ms | 0.37/s |
| 10 | 支付回调 | 10 | 0% | 107.5ms | 166ms | 166ms | 0.37/s |
| 10 | 重复回调幂等 | 10 | 0% | 11.9ms | 27ms | 27ms | 0.37/s |
| 10 | 总计 | 80 | 0% | 113.6ms | 206.65ms | 222ms | 2.93/s |
| 30 | 导购流式 | 待填写 | 待填写 | 待填写 | 待填写 | 待填写 | 待填写 |
| 30 | 拼团锁单 | 待填写 | 待填写 | 待填写 | 待填写 | 待填写 | 待填写 |
| 30 | 支付回调 | 待填写 | 待填写 | 待填写 | 待填写 | 待填写 | 待填写 |
| 50 | 导购流式 | 待填写 | 待填写 | 待填写 | 待填写 | 待填写 | 待填写 |
| 50 | 拼团锁单 | 待填写 | 待填写 | 待填写 | 待填写 | 待填写 | 待填写 |
| 50 | 支付回调 | 待填写 | 待填写 | 待填写 | 待填写 | 待填写 | 待填写 |

## 结果文件

- `JTL`（压测原始结果）：`docs/performance/jmeter/results/agent-group-20260526230938.jtl`
- `HTML`（网页报告）：`docs/performance/jmeter/reports/agent-group-20260526230938/index.html`

## 监控指标

压测期间同步观察：

- `http_server_requests_seconds`（接口耗时）：确认是否有大量 500 错误。
- `agent_group_guide_total_latency`（导购总耗时）：确认导购链路端到端耗时。
- `agent_group_guide_fallback_total`（模型兜底次数）：确认真实模型是否稳定。
- `agent_group_tool_call_total`（工具调用次数）：确认知识检索、推荐、拼团试算是否被调用。
- `agent_group_group_buy_lock_total`（拼团锁单次数）：确认成功、重复、失败状态分布。
- `agent_group_payment_webhook_total`（支付回调次数）：确认成功和重复回调状态。

## 结论

本次 10 线程档位已经完成真实本地执行，`JMeter`（压测工具）侧错误率为 0%，未观察到锁单、支付创建、支付回调和重复回调幂等断言失败。

当前结论只能作为第一版本地基线：模型密钥不可用导致导购链路走兜底，`pgvector`（向量库）检索也触发失败回退；因此这次数据能证明交易链路和压测脚本可跑通，但还不能代表真实大模型调用下的线上性能。
