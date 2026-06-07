import { describe, expect, it } from "vitest";

import {
  OUTPUT_KIND_LABELS,
  TOOL_LABELS,
  USER_WORKSPACES,
  WORKSPACE_PROMPTS,
  WORKSPACES,
  isUserWorkspace,
  workspaceAgentMode,
  workspaceFromPath,
  workspacePath,
  userWorkspaceFromPath
} from "./workspaces";

describe("workspace routing model", () => {
  it("maps workspace paths to stable workspace ids", () => {
    expect(workspaceFromPath("/")).toBe("agent");
    expect(workspaceFromPath("/workspace/image")).toBe("image");
    expect(workspaceFromPath("/workspace/image/")).toBe("image");
    expect(workspaceFromPath("/workspace/data")).toBe("data");
    expect(workspaceFromPath("/workspace/mrag")).toBe("mrag");
    expect(workspaceFromPath("/workspace/trade")).toBe("trade");
    expect(workspaceFromPath("/missing")).toBe("agent");
  });

  it("keeps only productized workspaces in the user-facing surface", () => {
    expect(USER_WORKSPACES.map((workspace) => workspace.id)).toEqual(["agent", "image"]);
    expect(isUserWorkspace("image")).toBe(true);
    expect(isUserWorkspace("data")).toBe(false);
    expect(isUserWorkspace("trade")).toBe(false);
    expect(userWorkspaceFromPath("/workspace/image")).toBe("image");
    expect(userWorkspaceFromPath("/workspace/data")).toBe("agent");
    expect(userWorkspaceFromPath("/workspace/trade")).toBe("agent");
  });

  it("maps workspace ids back to paths and agent modes", () => {
    expect(workspacePath("image")).toBe("/workspace/image");
    expect(workspacePath("data")).toBe("/workspace/data");
    expect(workspacePath("mrag")).toBe("/workspace/mrag");
    expect(workspacePath("unknown")).toBe("/");
    expect(workspaceAgentMode("image")).toBe("image");
    expect(workspaceAgentMode("data")).toBe("data");
    expect(workspaceAgentMode("mrag")).toBe("mrag");
    expect(workspaceAgentMode("trade")).toBe("data");
    expect(workspaceAgentMode("unknown")).toBe("chat");
  });

  it("keeps every workspace usable from navigation and prompt templates", () => {
    for (const workspace of WORKSPACES) {
      expect(workspace.name).toBeTruthy();
      expect(workspace.path.startsWith("/")).toBe(true);
      if (workspace.id === "trade") {
        expect(WORKSPACE_PROMPTS[workspace.id]).toEqual([]);
        continue;
      }
      expect(WORKSPACE_PROMPTS[workspace.id].length).toBeGreaterThan(0);
      expect(WORKSPACE_PROMPTS[workspace.id].every((item) => item.prompt.length > 10)).toBe(true);
    }
  });

  it("provides readable labels for workspace tools and output kinds", () => {
    expect(TOOL_LABELS.image_generation).toBe("图像生成");
    expect(TOOL_LABELS.nl2sql).toBe("自然语言转 SQL");
    expect(OUTPUT_KIND_LABELS.artifact).toBe("任务产物");
  });
});
