import { describe, expect, it } from "vitest";

import { buildAgentPlatformReadiness } from "./agentPlatformReadiness";

const READY_TOOLS = [
  "web_fetch",
  "data_analysis",
  "report_tool",
  "planning",
  "code_interpreter",
  "image_generation",
  "multimodal_agent",
  "deep_search",
  "file_tool",
  "script_runner",
  "table_rag",
  "nl2sql"
];

function executionModes() {
  return [
    { agentId: "chat", family: "react", executionMode: "ReAct" },
    {
      agentId: "deep",
      family: "plan-execute",
      executionMode: "Plan-Execute",
      replanEnabled: true,
      replanEvidence: ["flow_delta:REPLANNED"]
    },
    { agentId: "ppt", family: "ppt-workflow", executionMode: "PPT Workflow" },
    { agentId: "skills", family: "skill-orchestration", executionMode: "Skill Orchestration" }
  ];
}

function workspaceProfiles() {
  return ["agent", "image", "data", "trade"].map((id) => ({
    id,
    path: id === "agent" ? "/" : `/workspace/${id}`,
    runEndpoint: id === "agent" ? "" : `/api/v1/agent/workspace/${id}/run`
  }));
}

function tradeCapability() {
  return {
    key: "trade-quota",
    gaps: [],
    guardrails: ["拼团支付成功不等于额度到账"],
    settlementRules: [
      { key: "direct-pay-success", quotaGrantAllowed: true },
      { key: "group-pay-success", quotaGrantAllowed: false },
      { key: "refund-success", quotaGrantAllowed: false }
    ]
  };
}

describe("agent platform readiness", () => {
  it("normalizes backend readiness payload", () => {
    const summary = buildAgentPlatformReadiness({
      mcpAdminHealth: {
        overallStatus: "ready",
        serverCount: 2,
        enabledServerCount: 1,
        readyServerCount: 1,
        degradedServerCount: 0,
        toolCount: 3,
        enabledToolCount: 2
      },
      agentPlatformReadiness: {
        status: "partial",
        statusLabel: "待补齐",
        title: "Agent + 拼团交易系统就绪度",
        metrics: [{ key: "tools", label: "工具", value: "8/13", tone: "warn" }],
        gaps: ["当前未发现或未缓存外部 MCP 工具"],
        actions: ["注册、发现并缓存 MCP 工具"],
        missingTools: ["code_interpreter"],
        mcpGaps: ["当前未发现或未缓存外部 MCP 工具"],
        tradeGuardrails: ["拼团支付成功不等于额度到账"]
      }
    });

    expect(summary).toMatchObject({
      status: "partial",
      statusLabel: "待补齐",
      title: "Agent + 拼团交易系统就绪度",
      missingTools: ["code_interpreter"],
      mcpGaps: ["当前未发现或未缓存外部 MCP 工具"]
    });
    expect(summary?.metrics).toEqual([{ key: "tools", label: "工具", value: "8/13", tone: "warn" }]);
    expect(summary?.actions).toEqual(["注册、发现并缓存 MCP 工具"]);
    expect(summary?.mcpHealth).toMatchObject({
      status: "ready",
      statusLabel: "已就绪",
      summary: "已就绪 · 服务 1/1 · 工具 2/3",
      tone: "good"
    });
  });

  it("derives partial readiness from raw capability fields", () => {
    const summary = buildAgentPlatformReadiness({
      agentExecutionModes: executionModes(),
      toolRuntimeReadiness: READY_TOOLS.map((name) => ({
        name,
        status: name === "code_interpreter" ? "missing" : "ready"
      })),
      workspaceProfiles: workspaceProfiles(),
      capabilityMatrix: [
        { key: "mcp", gaps: ["当前未发现或未缓存外部 MCP 工具"] },
        tradeCapability()
      ]
    });

    expect(summary?.status).toBe("partial");
    expect(summary?.metrics.find((item) => item.key === "families")).toMatchObject({
      value: "4/4",
      tone: "good"
    });
    expect(summary?.metrics.find((item) => item.key === "tools")).toMatchObject({
      value: "11/12",
      tone: "warn"
    });
    expect(summary?.missingTools).toEqual(["code_interpreter"]);
    expect(summary?.actions).toContain("注册、发现并缓存 MCP 工具");
  });

  it("derives mcp gaps from admin health when capability matrix is missing", () => {
    const summary = buildAgentPlatformReadiness({
      agentExecutionModes: executionModes(),
      toolRuntimeReadiness: READY_TOOLS.map((name) => ({ name, status: "ready" })),
      workspaceProfiles: workspaceProfiles(),
      mcpAdminHealth: {
        overallStatus: "missing",
        serverCount: 0,
        enabledServerCount: 0,
        readyServerCount: 0,
        degradedServerCount: 0,
        toolCount: 0,
        enabledToolCount: 0,
        message: "MCP admin handler is not available"
      },
      capabilityMatrix: [
        tradeCapability()
      ]
    });

    expect(summary?.status).toBe("partial");
    expect(summary?.mcpGaps).toEqual(["MCP 管理器未加载", "还没有注册 MCP 服务"]);
    expect(summary?.actions).toContain("注册、发现并缓存 MCP 工具");
    expect(summary?.mcpHealth).toMatchObject({
      status: "missing",
      statusLabel: "未加载",
      summary: "未加载 · 服务 0/0 · 工具 0/0",
      tone: "warn",
      message: "MCP admin handler is not available"
    });
  });

  it("marks platform ready when agent, tools, mcp, workspaces and trade rules are complete", () => {
    const summary = buildAgentPlatformReadiness({
      agentExecutionModes: executionModes(),
      toolRuntimeReadiness: READY_TOOLS.map((name) => ({ name, status: "ready" })),
      workspaceProfiles: workspaceProfiles(),
      capabilityMatrix: [
        { key: "mcp", gaps: [] },
        tradeCapability()
      ]
    });

    expect(summary?.status).toBe("ready");
    expect(summary?.statusLabel).toBe("已就绪");
    expect(summary?.gaps).toEqual([]);
    expect(summary?.actions).toEqual(["Agent 与拼团交易闭环已具备完整演示面"]);
    expect(summary?.metrics.map((item) => item.value)).toEqual(["4/4", "1", "12/12", "2/2", "3"]);
  });

  it("returns null before capabilities are loaded", () => {
    expect(buildAgentPlatformReadiness(null)).toBeNull();
    expect(buildAgentPlatformReadiness({})).toBeNull();
  });
});
