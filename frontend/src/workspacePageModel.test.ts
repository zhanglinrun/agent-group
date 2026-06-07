import { describe, expect, it } from "vitest";

import { buildWorkspacePageModel } from "./workspacePageModel";

describe("workspace page model", () => {
  it("describes the image generation workspace as a dedicated page", () => {
    const model = buildWorkspacePageModel("image", {
      workspaceProfiles: [
        {
          id: "image",
          availableTools: ["image_generation", "multimodal_agent", "file_tool"],
          missingTools: [],
          runEndpoint: "/api/v1/academic/workspace/image/generate",
          historyEndpoint: "/api/v1/academic/workspace/image/history"
        }
      ]
    });

    expect(model.workspace.id).toBe("image");
    expect(model.status).toBe("ready");
    expect(model.acceptsFiles).toBe(true);
    expect(model.supportsHistory).toBe(true);
    expect(model.dedicatedRun).toBe(true);
    expect(model.dedicatedHistory).toBe(true);
    expect(model.inputKinds).toEqual(["prompt", "file", "image-options"]);
    expect(model.actions.find((action) => action.key === "run")).toMatchObject({
      label: "生成图像",
      enabled: true,
      endpoint: "/api/v1/academic/workspace/image/generate"
    });
  });

  it("keeps data workspace catalog and tool readiness visible", () => {
    const model = buildWorkspacePageModel("data", {
      workspaceProfiles: [
        {
          id: "data",
          availableTools: ["data_analysis", "table_rag"],
          missingTools: ["nl2sql", "report_tool"]
        }
      ]
    });

    expect(model.status).toBe("partial");
    expect(model.inputKinds).toEqual(["prompt", "file", "data-catalog"]);
    expect(model.toolReadiness.readyTools).toEqual(["data_analysis", "table_rag"]);
    expect(model.toolReadiness.missingTools).toEqual(["nl2sql", "report_tool"]);
    expect(model.actions.map((action) => action.key)).toEqual(["run", "history"]);
  });

  it("models MRAG and trade workspaces with different input surfaces", () => {
    expect(buildWorkspacePageModel("mrag", null)).toMatchObject({
      status: "pending",
      acceptsFiles: true,
      supportsHistory: true,
      inputKinds: ["prompt", "file", "knowledge-base"]
    });

    const trade = buildWorkspacePageModel("trade", {
      workspaceProfiles: [
        {
          id: "trade",
          availableTools: [],
          missingTools: []
        }
      ]
    });

    expect(trade.acceptsFiles).toBe(false);
    expect(trade.inputKinds).toEqual(["quota"]);
    expect(trade.actions.map((action) => action.key)).toEqual(["history", "recharge"]);
    expect(trade.actions.find((action) => action.key === "recharge")).toMatchObject({
      label: "额度购买",
      enabled: true
    });
  });

  it("falls back to the agent workspace for unknown routes", () => {
    const model = buildWorkspacePageModel("missing", {});

    expect(model.workspace.id).toBe("agent");
    expect(model.profile.taskType).toBe("chat");
    expect(model.prompts.length).toBeGreaterThan(0);
    expect(model.actions).toEqual([
      {
        key: "run",
        label: "开始对话",
        enabled: true,
        endpoint: "/api/v1/academic/stream"
      }
    ]);
  });
});
