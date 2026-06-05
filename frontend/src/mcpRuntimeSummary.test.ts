import { describe, expect, it } from "vitest";

import { buildMcpRuntimeSummary } from "./mcpRuntimeSummary";

describe("mcp runtime summary", () => {
  it("marks runtime as empty before any server is registered", () => {
    const summary = buildMcpRuntimeSummary({ servers: [], tools: [], health: { overallStatus: "empty" } });

    expect(summary).toMatchObject({
      status: "empty",
      statusLabel: "未配置",
      title: "还没有可用的 MCP 服务"
    });
    expect(summary.alerts).toContain("还没有注册 MCP 服务");
    expect(summary.actions).toContain("先注册一个 MCP 服务");
  });

  it("detects expired and empty caches for enabled servers", () => {
    const summary = buildMcpRuntimeSummary({
      servers: [
        {
          serverId: "research",
          name: "Research",
          enabled: true,
          status: "degraded",
          toolCount: 2,
          enabledToolCount: 2,
          cacheStatus: "expired",
          transport: "streamable_http"
        },
        {
          serverId: "local",
          name: "Local Tools",
          enabled: true,
          status: "degraded",
          toolCount: 0,
          enabledToolCount: 0,
          cacheStatus: "empty",
          transport: "stdio"
        }
      ],
      tools: [
        { serverId: "research", qualifiedName: "research.search", enabled: true },
        { serverId: "research", qualifiedName: "research.fetch", enabled: true }
      ]
    });

    expect(summary.status).toBe("degraded");
    expect(summary.alerts).toEqual([
      "Research 工具缓存已过期",
      "Local Tools 尚未缓存工具"
    ]);
    expect(summary.actions).toContain("重新发现并缓存工具");
    expect(summary.metrics.find((item) => item.key === "cache")).toMatchObject({
      value: "1 过期 / 1 未缓存",
      tone: "warn"
    });
  });

  it("uses health payload when available and marks ready runtime", () => {
    const summary = buildMcpRuntimeSummary({
      servers: [],
      tools: [],
      health: {
        overallStatus: "ready",
        serverCount: 1,
        enabledServerCount: 1,
        readyServerCount: 1,
        toolCount: 3,
        enabledToolCount: 3,
        servers: [
          {
            serverId: "research",
            enabled: true,
            status: "ready",
            toolCount: 3,
            enabledToolCount: 3,
            cacheStatus: "fresh",
            transport: "sse"
          }
        ]
      }
    });

    expect(summary).toMatchObject({
      status: "ready",
      statusLabel: "可用",
      title: "MCP 运行时已就绪"
    });
    expect(summary.alerts).toEqual([]);
    expect(summary.actions).toEqual(["可以试调用工具或交给 Agent 使用"]);
    expect(summary.metrics.map((item) => item.key)).toEqual([
      "servers",
      "tools",
      "ready",
      "cache",
      "transport"
    ]);
  });
});
