import { describe, expect, it } from "vitest";

import {
  WORKSPACE_PROMPTS,
  WORKSPACES,
  workspaceAgentMode,
  workspaceFromPath,
  workspacePath
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

  it("maps workspace ids back to paths and agent modes", () => {
    expect(workspacePath("image")).toBe("/workspace/image");
    expect(workspacePath("data")).toBe("/workspace/data");
    expect(workspacePath("mrag")).toBe("/workspace/mrag");
    expect(workspacePath("unknown")).toBe("/");
    expect(workspaceAgentMode("image")).toBe("image");
    expect(workspaceAgentMode("data")).toBe("data");
    expect(workspaceAgentMode("mrag")).toBe("mrag");
    expect(workspaceAgentMode("trade")).toBe("trade-audit");
    expect(workspaceAgentMode("unknown")).toBe("chat");
  });

  it("keeps every workspace usable from navigation and prompt templates", () => {
    for (const workspace of WORKSPACES) {
      expect(workspace.name).toBeTruthy();
      expect(workspace.path.startsWith("/")).toBe(true);
      expect(WORKSPACE_PROMPTS[workspace.id].length).toBeGreaterThan(0);
      expect(WORKSPACE_PROMPTS[workspace.id].every((item) => item.prompt.length > 10)).toBe(true);
    }
  });
});
