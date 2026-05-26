# `JMeter`（压测工具）压测说明

## 前置条件

1. 启动本地依赖：

```powershell
cd E:\javaproject\agent-group\docs\dev-ops
docker compose -f docker-compose-environment.yml up -d
```

2. 启动后端：

```powershell
cd E:\javaproject\agent-group\backend
mvn -pl agent-group-app -am spring-boot:run -Dspring-boot.run.profiles=dev
```

3. 确认接口可用：

```text
http://127.0.0.1:8080/actuator/health
http://127.0.0.1:8080/actuator/prometheus
```

## 脚本内容

- `agent-group-load-test.jmx`（压测计划）：包含导购流式接口、拼团锁单接口、支付回调接口。
- `guide_questions.csv`（压测数据）：提供用户编号和导购问题。
- `run-jmeter.ps1`（运行脚本）：非图形模式运行并生成 `HTML Report`（网页报告）。

## 推荐运行方式

先跑 10 并发：

```powershell
cd E:\javaproject\agent-group\docs\performance\jmeter
.\run-jmeter.ps1 -GuideThreads 10 -LockThreads 10 -PaymentThreads 10 -RampSeconds 30 -Loops 1
```

再跑 30 和 50 并发：

```powershell
.\run-jmeter.ps1 -GuideThreads 30 -LockThreads 30 -PaymentThreads 30 -RampSeconds 60 -Loops 1
.\run-jmeter.ps1 -GuideThreads 50 -LockThreads 50 -PaymentThreads 50 -RampSeconds 90 -Loops 1
```

## 压测口径

- 导购流式接口会检查是否输出 `done`（完成事件）和 `decisionId`（导购决策编号）。
- 拼团锁单链路会先调用导购接口提取 `decisionId`（导购决策编号），再锁单，避免绕过后端校验。
- 支付链路会先完成导购和拼团锁单，再创建支付单、发送支付回调，并重复发送一次回调用于验证防重放。
- `JMeter`（压测工具）报告里的 `Throughput`（吞吐）、`Error %`（错误率）、`Average`（平均耗时）、`90% Line`（九十分位耗时）和 `99% Line`（九十九分位耗时）要同步整理到 `docs/performance/load-test-report.md`。

## 注意事项

- 拼团活动默认有用户限购约束，压测数据使用了不同用户编号，默认每档并发只跑 1 次循环。
- 如果要多次重跑压测，建议先重置演示数据，避免库存、限购和历史订单影响结果。
- 导购链路包含大模型调用，真实密钥不可提交到 `Git`（版本控制工具），只通过环境变量传入。
