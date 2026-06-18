import { describe, expect, it } from "vitest";
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";

import { WorkspaceEmptyState } from "./WorkspaceEmptyState";

describe("WorkspaceEmptyState", () => {
  it("renders runtime status, manual skills, and trade recharge action", () => {
    const html = renderToStaticMarkup(createElement(WorkspaceEmptyState, {
      workspace: {
        id: "trade",
        name: "交易工作区",
        icon: "￥"
      },
      profile: {
        summary: "核对额度、订单和流水",
        primaryTools: ["trade_diagnosis"]
      },
      capabilities: {
        ready: true,
        manualSkills: [
          { name: "trade-audit", scriptCount: 2 }
        ]
      },
      pageModel: {
        prompts: [
          { title: "核对订单", prompt: "核对最近订单", icon: "credit" }
        ],
        supportsHistory: true,
        dedicatedRun: true,
        toolReadiness: {
          status: "partial",
          statusLabel: "部分可用",
          readyTools: ["trade_diagnosis"],
          requiredTools: ["trade_diagnosis", "quota_audit"],
          missingTools: ["quota_audit"],
          actions: ["补充额度排障工具"]
        },
        runtimeCoverage: {
          status: "partial",
          statusLabel: "部分接入",
          runReady: true,
          historyReady: true,
          availableTools: ["trade_diagnosis"],
          missingTools: ["quota_audit"]
        }
      },
      onPrompt: () => {},
      onOpenRecharge: () => {}
    }));

    expect(html).toContain("交易工作区");
    expect(html).toContain("核对额度、订单和流水");
    expect(html).toContain("工具状态");
    expect(html).toContain("部分可用");
    expect(html).toContain("quota_audit");
    expect(html).toContain("trade-audit");
    expect(html).toContain("2 scripts");
    expect(html).toContain("核对订单");
    expect(html).toContain("额度购买");
  });
});
