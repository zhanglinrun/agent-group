# 本地基础设施

本目录用于启动本项目需要的本地容器环境，支撑登录、额度、Agent（智能体）对话、拼团、支付、退款和监控演示。

## 组件约定

| 组件 | 用途 |
| --- | --- |
| MySQL | 保存用户、额度包、拼团活动、订单、支付、退款和 Agent 会话等业务数据。 |
| Redis | 保存会话状态、限流、锁单和缓存数据。 |
| PostgreSQL + pgvector | 保存会话附件向量（`vector_file_info`），用于文件模式 RAG。 |
| MinIO | 保存用户上传的附件和生成物。 |
| RabbitMQ | 承接支付成功、成团、退款等交易事件。 |
| XXL-JOB | 调度交易补偿任务，接管 Outbox 投递、业务通知重试、超时退款和支付查单补偿。 |
| Prometheus | 采集后端 `Actuator`（应用监控端点）指标。 |
| Grafana | 展示 `Prometheus`（指标采集工具）里的运行指标。 |

## 启动命令

```bash
cd docs/dev-ops
docker compose -f docker-compose-environment.yml up -d
```

如果需要按完整应用方式启动，可以先打包后端并启动应用容器：

```powershell
.\start.ps1
```

停止应用和本地依赖：

```powershell
.\stop.ps1
```

管理页面会默认一起启动，方便查看 MySQL 和 Redis 数据。

## 最小演示路径

启动基础设施后，在项目根目录打开一个新的终端运行后端：

```powershell
cd backend
mvn -pl agent-group-app -am spring-boot:run
```

后端启动后，在项目根目录打开新的终端运行前端：

```powershell
cd frontend
npm install
npm run dev
```

浏览器打开：

- `http://localhost:5173/`（用户端 `Agent`（智能体）工作台）
- `http://localhost:5173/admin`（运营端：模型配置、拼团、交易监控）

建议按这个顺序演示：

1. 用户注册或登录后进入额度中心，查看余额、额度流水和额度包列表。
2. 分别点击直接购买和拼团购买，观察锁单、支付回调、成团状态、额度到账和退款边界。
3. 在工作台切换 chat / deep / ppt 等模式，或上传附件做文件问答，观察 SSE 流式输出和额度消耗。
4. 运行 deep 复杂任务，观察能力计划、能力调用、产物区、失败原因和重新执行入口。
5. 点击右上角“记忆”，查看长期记忆，验证启用、停用、删除和任务结束后的自动沉淀。
6. 进入运营端查看拼团活动、交易订单与一致性核查。
7. 打开 XXL-JOB Admin 查看交易补偿任务，必要时手动触发指定订单或通知任务。
8. 打开 Grafana 看板，观察工具调用速率、模型耗时和错误率。

## 交易补偿调度

本项目的交易补偿类任务由 `XXL-JOB`（分布式任务调度平台）接管，不再保留本地 `@Scheduled`（本地定时任务）入口。调度中心使用独立数据库 `xxl_job`，复用本地 MySQL 容器。

访问地址：

- `http://localhost:18081/xxl-job-admin`
- 默认账号：`admin`
- 默认密码：`123456`

后端执行器默认配置：

- `agent.group.xxl-job.enabled=true`
- `agent.group.xxl-job.admin-addresses=http://127.0.0.1:18081/xxl-job-admin`
- `agent.group.xxl-job.app-name=agent-group-trade-job`
- `agent.group.xxl-job.port=9999`

已初始化的任务：

| 任务 | Handler | 参数 |
| --- | --- | --- |
| 交易事件 Outbox 投递 | `tradeEventOutboxDispatchJobHandler` | 默认批量执行 |
| 拼团业务通知重试 | `groupBuyNotifyTaskJobHandler` | 可传 `uuid=N10001` 或 `teamId=T10001` |
| 超时未成团退款补偿 | `timeoutGroupRefundJobHandler` | 默认批量执行 |
| 支付查单与未支付关单补偿 | `paymentQueryCompensationJobHandler` | 可传 `orderId=O10001` |

概念口径：

- 支付回调：支付宝/微信等支付网关调用本项目，带验签、防重放和主动查单补偿。
- 业务通知：本项目在成团或退款后通知下游订阅方，走 `MQ`（消息队列）或 `HTTP`（超文本传输协议）派发，失败后由补偿任务重试。
- 额度履约：直接购买支付成功发额；拼团购买必须成团或交易完成后发额；退款后按额度流水回滚。

## 压测与故障演练

交易压测脚本在 `docs/dev-ops/loadtest`。仓库只保留 `JMeter`（压测工具）脚本和结果记录模板，不保留历史压测报告；本地生成的 `report/` 和 `result*.jtl` 已被忽略。对外引用 QPS、P99 前，重新压一轮并把环境、参数、结果写入压测说明里的表格。

如果容器数据卷已经存在，`docker-entrypoint-initdb.d`（数据库初始化目录）下的脚本不会自动重复执行。需要刷新演示数据时，可以手动导入：

```bash
docker exec -i agent-group-mysql mysql --default-character-set=utf8mb4 -uroot -pagent_group_dev agent_group < docs/dev-ops/mysql/sql/00-schema-upgrade.sql
docker exec -i agent-group-mysql mysql --default-character-set=utf8mb4 -uroot -pagent_group_dev agent_group < docs/dev-ops/mysql/sql/01-agent-group.sql
docker exec -i agent-group-mysql mysql --default-character-set=utf8mb4 -uroot -pagent_group_dev agent_group < docs/dev-ops/mysql/sql/02-demo-data.sql
docker exec -i agent-group-mysql mysql --default-character-set=utf8mb4 -uroot -pagent_group_dev < docs/dev-ops/mysql/sql/03-xxl-job.sql
```

## 默认端口

| 服务 | 端口 |
| --- | --- |
| MySQL | 13306 |
| Redis | 16379 |
| PostgreSQL + pgvector | 15432 |
| MinIO API | 9000 |
| MinIO Console | 9001 |
| RabbitMQ | 5672 |
| RabbitMQ Console | 15672 |
| XXL-JOB Admin | 18081 |
| phpMyAdmin | 8899 |
| Redis Admin | 8081 |
| Prometheus | 19090 |
| Grafana | 13000 |
| Nginx | 18080 |
| Elasticsearch | 19200 |
| Logstash TCP | 15044 |
| Kibana | 15601 |

如果本机 `13306`（数据库端口）已经被其他项目占用，可以临时改成本项目专用端口：

```powershell
$env:AGENT_GROUP_MYSQL_PORT="13316"
docker compose -f docker-compose-environment.yml up -d mysql
```

后端同时指定：

```powershell
$env:AGENT_GROUP_MYSQL_URL="jdbc:mysql://127.0.0.1:13316/agent_group?useUnicode=true&characterEncoding=utf8&autoReconnect=true&zeroDateTimeBehavior=convertToNull&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
```

## 当前接入状态

当前用户端已经接入登录注册、额度中心、Agent（智能体）流式对话、任务产物区、长期记忆入口、文件上传、直接购买、拼团购买和支付宝支付。购买入口直接基于额度包创建订单，额度是否到账以后端支付状态和拼团成团状态为准。

Agent 侧已支持 deep 任务执行台、能力调用事件、产物沉淀和三层记忆；会话附件经解析、切片后写入 pgvector，供 file 模式语义检索。交易侧已经拆出关闭未支付订单、超时退款、业务通知补偿和 Outbox 投递四类任务，并由 `XXL-JOB`（分布式任务调度平台）统一调度；通知配置由 `NotifyConfig`（通知配置值对象）承载，再通过 `TradeNotifyPort`（外部交易通知端口）派发。
