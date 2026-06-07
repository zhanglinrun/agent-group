import { describe, expect, it } from "vitest";

import {
  activeWorkspaceNavigationItem,
  buildWorkspaceNavigation,
  resolveWorkspaceNavigationTarget
} from "./workspaceNavigation";

describe("workspace navigation view model", () => {
  it("marks active workspace from id or route path", () => {
    expect(buildWorkspaceNavigation("data", null, { includeInternal: true }).find((item) => item.active)?.id).toBe("data");
    expect(buildWorkspaceNavigation("/workspace/image/", null, { includeInternal: true }).find((item) => item.active)?.id).toBe("image");
    expect(activeWorkspaceNavigationItem("/missing").id).toBe("agent");
  });

  it("merges backend profile and runtime coverage into navigation items", () => {
    const capabilities = {
      workspaceProfiles: [
        {
          id: "image",
          runEndpoint: "/api/custom/image",
          historyEndpoint: "/api/custom/image/history",
          primaryTools: ["image_generation", "custom_render"],
          availableTools: ["image_generation"],
          missingTools: ["custom_render"],
          outputKinds: ["image"]
        }
      ],
      toolCatalog: {
        workspaceCoverage: [
          {
            workspace: "image",
            status: "degraded",
            runEndpoint: "/api/custom/image",
            historyEndpoint: "/api/custom/image/history",
            availableTools: ["image_generation"],
            missingTools: ["custom_render"]
          }
        ]
      }
    };
    const navigation = buildWorkspaceNavigation("image", capabilities, { includeInternal: true });

    const image = navigation.find((item) => item.id === "image");
    expect(image).toMatchObject({
      active: true,
      runEndpoint: "/api/custom/image",
      historyEndpoint: "/api/custom/image/history",
      runtimeStatus: "degraded",
      pageStatus: "partial",
      primaryActionLabel: "生成图像",
      inputKinds: ["prompt", "file", "image-options"],
      dedicatedRun: true,
      dedicatedHistory: true,
      availableTools: ["image_generation"],
      missingTools: ["custom_render"]
    });
  });

  it("exposes page actions for productized workspace switching", () => {
    const capabilities = {
      workspaceProfiles: [
        {
          id: "trade",
          availableTools: [],
          missingTools: [],
          runEndpoint: "",
          historyEndpoint: "/api/v1/trade/order/my"
        }
      ]
    };

    const navigation = buildWorkspaceNavigation("trade", capabilities, { includeInternal: true });

    const trade = activeWorkspaceNavigationItem("trade", capabilities, { includeInternal: true });
    const data = navigation.find((item) => item.id === "data");

    expect(trade).toMatchObject({
      id: "trade",
      pageStatus: "pending",
      primaryActionLabel: "",
      inputKinds: ["quota"],
      dedicatedRun: false
    });
    expect(data).toMatchObject({
      primaryActionLabel: "运行数据问答",
      inputKinds: ["prompt", "file", "data-catalog"],
      dedicatedRun: true
    });
  });

  it("resolves navigation target for workspace switching", () => {
    expect(resolveWorkspaceNavigationTarget("trade")).toEqual({
      workspaceId: "trade",
      path: "/workspace/trade",
      agentId: "data"
    });
    expect(resolveWorkspaceNavigationTarget("unknown")).toEqual({
      workspaceId: "agent",
      path: "/",
      agentId: "chat"
    });
  });
});
