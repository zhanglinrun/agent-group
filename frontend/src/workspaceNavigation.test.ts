import { describe, expect, it } from "vitest";

import {
  activeWorkspaceNavigationItem,
  buildWorkspaceNavigation,
  resolveWorkspaceNavigationTarget
} from "./workspaceNavigation";

describe("workspace navigation view model", () => {
  it("marks active workspace from id or route path", () => {
    expect(buildWorkspaceNavigation("data").find((item) => item.active)?.id).toBe("data");
    expect(buildWorkspaceNavigation("/workspace/image/").find((item) => item.active)?.id).toBe("image");
    expect(activeWorkspaceNavigationItem("/missing").id).toBe("agent");
  });

  it("merges backend profile and runtime coverage into navigation items", () => {
    const navigation = buildWorkspaceNavigation("image", {
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
    });

    const image = navigation.find((item) => item.id === "image");
    expect(image).toMatchObject({
      active: true,
      runEndpoint: "/api/custom/image",
      historyEndpoint: "/api/custom/image/history",
      runtimeStatus: "degraded",
      availableTools: ["image_generation"],
      missingTools: ["custom_render"]
    });
  });

  it("resolves navigation target for workspace switching", () => {
    expect(resolveWorkspaceNavigationTarget("trade")).toEqual({
      workspaceId: "trade",
      path: "/workspace/trade",
      agentId: "trade-audit"
    });
    expect(resolveWorkspaceNavigationTarget("unknown")).toEqual({
      workspaceId: "agent",
      path: "/",
      agentId: "chat"
    });
  });
});
