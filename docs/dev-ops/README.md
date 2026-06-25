# 本地基础设施

本目录用于启动本项目需要的本地容器环境，支撑登录、额度、学术 `Agent`（智能体）、拼团、支付、退款、知识库和监控演示。

## 组件约定

| 组件 | 用途 |
| --- | --- |
| MySQL | 保存用户、额度包、拼团活动、订单、支付、退款和知识文档等业务数据。 |
| Redis | 保存会话状态、限流、锁单和缓存数据。 |
| PostgreSQL + pgvector | 保存知识片段向量，用于向量检索。 |
| MinIO | 保存上传的论文、文档和知识资料。 |
| RabbitMQ | 承接支付成功、成团、退款等交易事件。 |
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

微信公众号扫码登录、公众号回调和模板消息联调见 `natapp-wechat.md`（内网穿透和公众号联调说明）。

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
- `http://localhost:5173/admin`（运营端知识库、评测和交易监控）

建议按这个顺序演示：

1. 用户注册或登录后进入额度中心，查看余额、额度流水和额度包列表。
2. 分别点击直接购买和拼团购买，观察锁单、支付回调、成团状态、额度到账和退款边界。
3. 在前台运行长文档整理、文件问答、`PPT`（演示文稿）大纲或图表重建任务，观察流式回答和额度消耗。
4. 进入运营端点击“执行评测”，观察检索命中率、回答准确率、任务匹配率、多轮一致性和平均耗时。
5. 上传样本文档，验证文档入库、切片、向量检索和失败补偿闭环。
6. 打开 `Grafana`（指标看板工具）看板，观察工具调用速率、工具平均耗时、模型耗时和回退次数。

## 压测与故障演练

如果容器数据卷已经存在，`docker-entrypoint-initdb.d`（数据库初始化目录）下的脚本不会自动重复执行。需要刷新演示数据时，可以手动导入：

```bash
docker exec -i agent-group-mysql mysql --default-character-set=utf8mb4 -uroot -pagent_group_dev agent_group < docs/dev-ops/mysql/sql/00-schema-upgrade.sql
docker exec -i agent-group-mysql mysql --default-character-set=utf8mb4 -uroot -pagent_group_dev agent_group < docs/dev-ops/mysql/sql/01-agent-group.sql
docker exec -i agent-group-mysql mysql --default-character-set=utf8mb4 -uroot -pagent_group_dev agent_group < docs/dev-ops/mysql/sql/02-demo-data.sql
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

当前用户端已经接入登录注册、额度中心、学术 `Agent`（智能体）流式接口、文件上传、直接购买、拼团购买和支付宝支付。购买入口直接基于额度包创建订单，额度是否到账以后端支付状态和拼团成团状态为准。

交易侧已经拆出关闭未支付订单、超时退款、通知补偿三类任务；通知配置由 `NotifyConfig`（通知配置值对象）承载，再通过 `TradeNotifyPort`（外部交易通知端口）派发。知识库侧已经补充向量入库失败标记、定时补偿任务和手动补偿接口。工具侧保留查询路由、知识检索、额度包推荐、拼团试算、退款状态、`JSON`（结构化数据格式）修复和文档补偿能力。
