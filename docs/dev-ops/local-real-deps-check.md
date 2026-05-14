# 本地真实依赖验收说明

## 启动依赖

```powershell
cd N:\java_project\agent-group\docs\dev-ops
docker compose -f docker-compose-environment.yml up -d
```

## 设置大模型环境变量

测试密钥不要写进仓库，只在当前终端设置：

```powershell
$env:AGENT_GROUP_LLM_BASE_URL="https://dashscope.aliyuncs.com/compatible-mode/v1"
$env:AGENT_GROUP_LLM_CHAT_MODEL="qwen3.6-plus"
$env:AGENT_GROUP_LLM_EMBEDDING_MODEL="text-embedding-v3"
$env:AGENT_GROUP_LLM_VISION_MODEL="qwen3.6-plus"
$env:AGENT_GROUP_LLM_API_KEY="你的测试密钥"
$env:AGENT_GROUP_VECTOR_LOCAL_FALLBACK_ENABLED="false"
$env:AGENT_GROUP_VECTOR_KEYWORD_FALLBACK_ENABLED="false"
```

## 设置运营账号

知识库上传、向量维护和评测接口已经加了基础鉴权。开发环境默认账号可以直接用，也可以在启动前覆盖：

```powershell
$env:AGENT_GROUP_OPERATOR_USERNAME="operator"
$env:AGENT_GROUP_OPERATOR_PASSWORD="operator_dev"
$env:AGENT_GROUP_ADMIN_USERNAME="admin"
$env:AGENT_GROUP_ADMIN_PASSWORD="admin_dev"
```

## 启动后端

```powershell
cd N:\java_project\agent-group\backend
mvn -pl agent-group-app -am spring-boot:run -Dspring-boot.run.profiles=dev
```

## 网页端验收

打开：

```text
N:\java_project\agent-group\frontend\index.html
N:\java_project\agent-group\frontend\admin.html
```

建议先在用户端上传 `docs\sample-knowledge`（样本知识资料）下的三份文档，再提问：

如果需要手动刷新演示数据，请带上 `utf8mb4`（四字节 UTF-8 编码）：

```powershell
docker exec agent-group-mysql sh -c "mysql --default-character-set=utf8mb4 -uroot -pagent_group_dev agent_group < /docker-entrypoint-initdb.d/00-schema-upgrade.sql && mysql --default-character-set=utf8mb4 -uroot -pagent_group_dev agent_group < /docker-entrypoint-initdb.d/01-agent-group.sql && mysql --default-character-set=utf8mb4 -uroot -pagent_group_dev agent_group < /docker-entrypoint-initdb.d/02-demo-data.sql"
```

```text
我是学生，预算有限，主要写论文和看网课，哪款平板更合适？
```

验收时看四件事：

- 文档上传后，提示已进入对象存储和向量化。
- 回答区出现流式回答。
- 右侧出现知识依据和商品卡片。
- 点击直接购买或拼团购买后，购买状态时间线会更新。

## 监控验收

后端启动后，可以访问：

```text
http://localhost:8080/actuator/prometheus
http://localhost:19090
http://localhost:13000
```

其中 `19090`（监控采集端口）是 `Prometheus`（指标采集工具），`13000`（监控看板端口）是 `Grafana`（指标看板工具）。`Grafana` 默认账号来自 `AGENT_GROUP_GRAFANA_USER`（监控账号）和 `AGENT_GROUP_GRAFANA_PASSWORD`（监控密码），开发默认是 `admin/admin_dev`。
