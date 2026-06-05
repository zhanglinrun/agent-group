import { describe, expect, it } from "vitest";

import { buildAgentRunDigest } from "./agentRunDigest";

describe("agent run digest", () => {
  it("hides digest when no execution evidence exists", () => {
    expect(buildAgentRunDigest({ content: "hello" })).toMatchObject({
      visible: false,
      status: "idle"
    });
  });

  it("summarizes plans, tools, artifacts and references", () => {
    const digest = buildAgentRunDigest({
      timeline: [
        { type: "plan", title: "调研计划", steps: [{ instruction: "搜索资料" }] },
        { type: "flow", status: "completed", stageIndex: 0, message: "资料搜索完成" },
        { type: "tool", status: "completed", toolName: "web_fetch", detail: "找到 3 条来源" },
        { type: "llm", status: "completed", modelName: "qwen", tokens: 1200 }
      ],
      artifacts: [{ title: "研究报告.html" }],
      reference: [{ title: "source" }],
      resultPanels: [{ kind: "search" }]
    });

    expect(digest.visible).toBe(true);
    expect(digest.status).toBe("completed");
    expect(digest.metrics.map((item) => item.key)).toEqual([
      "plans",
      "flows",
      "tools",
      "artifacts",
      "references",
      "panels",
      "tokens"
    ]);
    expect(digest.highlights.join("\n")).toContain("web_fetch");
  });

  it("marks digest as running when tools are still running", () => {
    const digest = buildAgentRunDigest({
      timeline: [
        { type: "tool", status: "running", toolName: "data_analysis" }
      ]
    });

    expect(digest.status).toBe("running");
    expect(digest.statusLabel).toContain("执行");
  });

  it("marks digest as attention when a tool fails", () => {
    const digest = buildAgentRunDigest({
      timeline: [
        { type: "tool", status: "completed", toolName: "web_fetch" },
        { type: "tool", status: "error", toolName: "report", detail: "生成失败" }
      ]
    });

    expect(digest.status).toBe("attention");
    expect(digest.metrics.find((item) => item.key === "tools")).toMatchObject({
      tone: "warn"
    });
  });
});
