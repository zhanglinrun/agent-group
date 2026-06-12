# 交易链路压测说明

本目录提供拼团锁单和直接购买下单两条链路的 `JMeter`（压测工具）脚本，用来得到可以写进简历、面试也敢被追问的真实性能数字。

一条约定：**所有对外引用的 QPS、响应时间都必须来自实际压测，跑完后把数字和环境一起记录在下面的结果表里**，没有跑过就先不要写数字。

## 准备

1. 启动依赖环境：在 `docs/dev-ops` 目录执行 `docker compose -f docker-compose-environment.yml up -d`。
2. 启动后端：在 `backend` 目录设置 `SPRING_PROFILES_ACTIVE=dev` 后执行 `mvn -pl agent-group-app -am spring-boot:run`。
3. 准备测试数据：确认库里有可用的额度包（`GOODS_ID`）和进行中的拼团活动（`ACTIVITY_ID`），可用 `docs/dev-ops/mysql/sql` 里的初始化数据。
4. 获取登录令牌：调用 `POST /api/v1/auth/login` 拿到 `token`（登录令牌），压测请求都带 `Authorization: Bearer <token>`。
5. 安装 `JMeter 5.6+`，确认 `jmeter` 命令可用。

## 运行

```bash
jmeter -n -t trade-loadtest.jmx \
  -JHOST=localhost -JPORT=8080 \
  -JTOKEN=替换成登录令牌 \
  -JGOODS_ID=G10001 -JACTIVITY_ID=A10001 \
  -JTHREADS=50 -JRAMP=10 -JDURATION=60 \
  -l result.jtl -e -o report
```

- 默认只启用"拼团锁单"线程组；要压直接购买，在 `JMeter` 图形界面里把对应线程组改为启用。
- `report/index.html` 里有吞吐量、P90/P95/P99 响应时间和错误率，结果数字以这里为准。
- 压测同时可以打开 `Grafana`（监控面板，docker 环境默认 3000 端口）看"Agent 运行与大模型调用监控"和工具/支付面板，观察服务端视角的延迟和错误。

## 建议的压测路径

按下面顺序做，能得到一个完整的"优化故事"，面试时按"问题—方案—数据"讲：

1. **基线**：默认配置压一轮，记录 QPS 和 P99。
2. **定位瓶颈**：观察数据库连接池、Redis、分布式锁等待，结合 `Grafana` 面板和慢日志判断瓶颈在哪一层。
3. **逐项优化**：每改一个点（如调整连接池、缓存活动信息、放宽锁粒度）重新压一轮，记录数字变化。
4. **写结论**：把"优化前 → 优化后"的数字和原因记到下面的结果表，这就是简历上那一行数据的出处。

## 结果记录

每轮压测在下表追加一行（没有跑过的场景不要填数字）：

| 日期 | 场景 | 机器配置 | 并发线程 | 持续时间 | QPS | P99 (ms) | 错误率 | 配置/优化说明 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| | | | | | | | | |

## 注意事项

- 拼团锁单链路带 `DistributedLock`（分布式锁）和幂等键校验，脚本里每个请求都用随机幂等键，压的是新单创建路径；如果想压幂等命中路径，把 `idempotentKey` 固定即可。
- 锁单会真实写库存和订单表，压测后用测试库，不要对着有真实数据的库压。
- 支付回调（`/api/v1/payment/webhook`）涉及验签和防重放，不适合直接用本脚本压；如需评估，单独构造带验签的请求。
