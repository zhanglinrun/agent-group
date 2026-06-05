# MCP 管理配置

当前项目支持从 `agent.group.mcp`（智能体项目 MCP 配置）启动导入外部工具服务，并继续保留本地状态文件恢复能力。

```yaml
agent:
  group:
    mcp:
      admin-state-file: ${AGENT_GROUP_MCP_ADMIN_STATE_FILE:data/mcp-admin-state.json}
      persist-imported-state: true
      servers:
        - server-id: local-tool
          name: local tool
          transport: stdio
          enabled: true
          discover-on-startup: false
          cache-discovered-tools: true
          metadata:
            command: npx
            args:
              - -y
              - "@demo/mcp-server"
            env:
              DEMO_TOKEN: ${DEMO_TOKEN:}
          tools:
            - tool-name: demo_tool
              description: demo tool
              enabled: true
              input-schema:
                type: object
                properties:
                  query:
                    type: string
                required:
                  - query
            - tool-name: disabled_debug_tool
              description: disabled debug tool
              enabled: false
              input-schema:
                type: object
                properties: {}
                required: []
```

说明：
- `transport`（传输方式）支持 `streamable_http`（流式 HTTP）、`sse`（服务端事件）和 `stdio`（标准输入输出）。
- `stdio`（标准输入输出）服务可以不配置 `endpoint`（端点地址），系统会自动生成内部占位地址。
- `discover-on-startup`（启动时发现工具）为 `true` 时会启动后自动发现工具；`cache-discovered-tools`（缓存发现结果）控制是否把发现到的工具写入缓存。
- `tools.enabled=false`（工具禁用）会保留工具定义，但不会进入主 `Agent`（智能体）的可调用工具集合。
- 密钥只通过环境变量传入，不写入配置文件和 `Git`（版本控制工具）。
