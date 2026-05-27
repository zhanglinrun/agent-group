# 本地基础设施

本目录用于启动第一阶段需要的本地容器环境，组件选择参考了 `xiaoxiongagent`、`s-pay-mall-ddd-market` 和 `group-buy-market` 三个项目。

## 组件约定

| 组件 | 用途 |
| --- | --- |
| MySQL | 保存商品、活动、订单、知识文档等业务数据。 |
| Redis | 保存会话状态、限流、锁单和缓存数据。 |
| PostgreSQL + pgvector | 保存知识片段向量，后续用于向量检索。 |
| MinIO | 保存上传的商品文档、售后文档和图片。 |
| RabbitMQ | 承接支付成功、成团、退款等交易事件。 |
| Prometheus | 采集后端 `Actuator`（应用监控端点）指标。 |
| Grafana | 展示 `Prometheus`（指标采集工具）里的运行指标。 |

## 启动命令

```bash
cd docs/dev-ops
docker compose -f docker-compose-environment.yml up -d
```

管理页面会默认一起启动，方便查看 MySQL 和 Redis 数据。

## 最小演示路径

启动基础设施后，在项目根目录打开一个新的终端运行后端：

```powershell
$env:AGENT_GROUP_EVALUATE_CASE_FILE="E:\javaproject\agent-group\docs\sample-knowledge\evaluation-cases.json"
cd backend
mvn -pl agent-group-app -am spring-boot:run
```

后端启动后，直接用浏览器打开：

- `frontend/index.html`（用户端导购演示）
- `frontend/admin.html`（运营端知识库、评测和交易监控）

建议按这个顺序演示：

1. 用户端点击“运行示例”，观察流式回答、检索依据和商品卡片。
2. 点击商品卡片里的“拼团购买”，观察导购报价凭证校验、锁单、创建支付单、支付回调演示和订单状态流水。
3. 进入运营端点击“执行评测”，观察 50 条用例下的检索命中率、回答准确率、推荐合理率、多轮一致性和平均耗时。
4. 上传 `docs/sample-knowledge` 里的样本文档，验证文档入库、切片和向量检索闭环。
5. 打开 `Grafana`（指标看板工具）的“基于 `Spring AI`（Spring 人工智能框架）的电商 `Agent`（智能体）导购与拼团交易系统运行观测”看板，观察工具调用速率、工具平均耗时、导购端到端耗时和回退次数。

## 压测与故障演练

项目已经补充 `JMeter`（压测工具）压测计划和报告模板：

- `docs/performance/jmeter/README.md`（压测运行说明）
- `docs/performance/load-test-report.md`（压测报告）
- `docs/reliability/failure-drill-report.md`（故障演练报告）
- `docs/validation/real-run-report.md`（真实调用效果报告）

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

当前导购流式接口已经通过 MyBatis 从 MySQL 读取商品卡片和知识片段。上传知识文档时会写入 `MinIO`（对象存储）、解析文本、切片，并通过 `Spring AI VectorStore`（向量存储接口）写入 `pgvector`（向量库）。如果向量库不可用，向量召回返回空结果，导购仍会使用 MyBatis 关键词召回和商品数据组织上下文；如果大模型不可用，只返回基于已检索资料的兜底回答，不再保留手写模型客户端或旧检索实现。`Redis`（缓存数据库）已用于会话和停止生成状态，`Prometheus`（指标采集工具）和 `Grafana`（指标看板工具）用于基础监控。
