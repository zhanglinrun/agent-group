import { describe, expect, it } from "vitest";
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";

import { SessionMemoryPanel } from "./SessionMemoryPanel";

describe("SessionMemoryPanel", () => {
  it("renders memory stats and latest reusable artifact", () => {
    const html = renderToStaticMarkup(createElement(SessionMemoryPanel, {
      memory: {
        summary: "本轮已经生成实验报告草稿",
        runs: [{ runId: "run-1" }],
        toolObservations: [{ toolName: "report" }, { toolName: "search" }],
        reusableArtifacts: [{ title: "实验报告.md" }]
      }
    }));

    expect(html).toContain("会话记忆");
    expect(html).toContain("本轮已经生成实验报告草稿");
    expect(html).toContain("运行");
    expect(html).toContain("工具观察");
    expect(html).toContain("可复用产物");
    expect(html).toContain("实验报告.md");
  });

  it("renders nothing when memory is empty", () => {
    const html = renderToStaticMarkup(createElement(SessionMemoryPanel, {
      memory: {}
    }));

    expect(html).toBe("");
  });
});
