import { describe, expect, it } from "vitest";

import { buildMcpRuntimeSummary, resolveMcpToolAvailability } from "./mcpRuntimeSummary";

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

  it("uses registry summary when health omits server detail", () => {
    const summary = buildMcpRuntimeSummary({
      health: {
        registrySummary: {
          serverCount: 2,
          enabledServerCount: 2,
          registeredToolCount: 1,
          enabledToolCount: 1,
          cachedServerCount: 1,
          emptyCacheServerCount: 1,
          enabledServersWithoutCachedTools: ["local"],
          transportCounts: {
            streamable_http: 1,
            stdio: 1
          }
        }
      }
    });

    expect(summary.status).toBe("degraded");
    expect(summary.alerts).toEqual(["local 尚未缓存工具"]);
    expect(summary.actions).toContain("重新发现并缓存工具");
    expect(summary.metrics.find((item) => item.key === "servers")).toMatchObject({
      value: "2/2"
    });
    expect(summary.metrics.find((item) => item.key === "tools")).toMatchObject({
      value: "1/1"
    });
    expect(summary.metrics.find((item) => item.key === "cache")).toMatchObject({
      value: "1 已缓存 / 1 未缓存",
      tone: "warn"
    });
    expect(summary.metrics.find((item) => item.key === "transport")).toMatchObject({
      value: "streamable_http 1 / stdio 1"
    });
  });

  it("marks tools on expired cache servers as not callable", () => {
    const availability = resolveMcpToolAvailability(
      { serverId: "research", qualifiedName: "research.search", enabled: true },
      [{ serverId: "research", enabled: true, cacheStatus: "expired" }]
    );

    expect(availability).toMatchObject({
      callable: false,
      state: "cache-expired",
      className: "expired"
    });
  });

  it("marks tools as callable when server and tool are enabled with fresh cache", () => {
    const availability = resolveMcpToolAvailability(
      { serverId: "research", qualifiedName: "research.search", enabled: true },
      [{ serverId: "research", enabled: true, cacheStatus: "fresh" }]
    );

    expect(availability).toMatchObject({
      callable: true,
      state: "callable",
      className: "enabled"
    });
  });

  it("marks disabled servers and tools as not callable", () => {
    expect(resolveMcpToolAvailability(
      { serverId: "research", qualifiedName: "research.search", enabled: true },
      [{ serverId: "research", enabled: false, cacheStatus: "fresh" }]
    )).toMatchObject({
      callable: false,
      state: "server-disabled",
      className: "disabled"
    });

    expect(resolveMcpToolAvailability(
      { serverId: "research", qualifiedName: "research.search", enabled: false },
      [{ serverId: "research", enabled: true, cacheStatus: "fresh" }]
    )).toMatchObject({
      callable: false,
      state: "tool-disabled",
      className: "disabled"
    });
  });
});
