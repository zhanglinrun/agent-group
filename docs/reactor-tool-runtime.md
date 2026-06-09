# reactor-tool 工具服务运行说明

当前项目已经把参考项目里的 `reactor-tool`（工具服务）纳入到 `tools/reactor-tool`（工具服务目录），用于承接代码解释器、数据分析、报告生成、网页抓取、图像生成、多模态检索、深度搜索、文件读写、脚本运行、表格检索和 `NL2SQL`（自然语言转 SQL）。

## 运行方式

先准备 `Python 3.11`（Python 运行环境）和 `uv`（Python 依赖管理工具）。

```powershell
cd E:\javaproject\agent-group\tools\reactor-tool
uv sync
Copy-Item .env_template .env
```

把 `.env`（环境变量文件）里的模型、搜索、向量库和对象存储配置改成自己的本地配置，不要提交这个文件。

首次启动前初始化本地文件库：

```powershell
cd E:\javaproject\agent-group\tools\reactor-tool
.\.venv\Scripts\python.exe -m reactor_tool.db.db_engine
```

启动工具服务：

```powershell
cd E:\javaproject\agent-group\tools\reactor-tool
.\start.ps1
```

默认监听 `http://127.0.0.1:1801`（本地工具服务地址）。

## 后端接入

后端默认保留降级模式，不强依赖工具服务。要启用完整工具端口，在启动后端前设置：

```powershell
$env:AGENT_GROUP_REACTOR_TOOL_ENABLED="true"
$env:AGENT_GROUP_REACTOR_TOOL_BASE_URL="http://127.0.0.1:1801"
```

然后启动后端：

```powershell
cd E:\javaproject\agent-group\backend
$env:SPRING_PROFILES_ACTIVE="dev"
mvn -pl agent-group-app -am spring-boot:run
```

启用后，`/agent/capabilities`（智能体能力接口）里的 `runtimeEnabledTools`（运行时已启用工具）应包含完整工具清单，图像、数据、`MRAG`（多模态检索增强生成）工作区会显示为可用。

## 安全约定

- `.env`（环境变量文件）、本地数据库、虚拟环境、运行产物和大体积可执行文件都不进入版本控制。
- 额度、订单、支付和拼团状态仍以当前后端交易系统为准，不能由 `reactor-tool`（工具服务）或模型自行判断到账、退款或成团。
- 工具服务只提供外部能力执行，任务是否可运行、额度是否足够、执行后是否扣减，仍由当前项目后端统一控制。
