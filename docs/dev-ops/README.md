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

当前导购流式接口已经通过 MyBatis 从 MySQL 读取商品卡片和知识片段。上传知识文档时会写入 `MinIO`（对象存储）、解析文本、切片、生成向量，并优先写入 `pgvector`（向量检索）。如果真实向量库不可用，开发环境会回退到本地向量检索，方便演示不断链。`Redis`（缓存数据库）已用于会话和停止生成状态，`Prometheus`（指标采集工具）和 `Grafana`（指标看板工具）用于基础监控。
