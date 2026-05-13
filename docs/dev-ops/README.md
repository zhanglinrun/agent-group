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

## 启动命令

```bash
cd docs/dev-ops
docker compose -f docker-compose-environment.yml up -d
```

管理页面会默认一起启动，方便查看 MySQL 和 Redis 数据。

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

## 当前接入状态

当前导购流式接口已经通过 MyBatis 从 MySQL 读取商品卡片和知识片段。`Redis`、`pgvector`、`MinIO` 和 `RabbitMQ` 先完成容器与配置预留，后续分别接入会话缓存、向量检索、文档上传和交易事件。
