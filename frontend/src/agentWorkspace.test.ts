import { describe, expect, it } from "vitest";

import { buildAgentWorkspace } from "./agentWorkspace";

describe("agent workspace model", () => {
  it("summarizes project context files and pending patches", () => {
    const model = buildAgentWorkspace({
      projectId: "AP1001",
      title: "AMR Paper",
      researchQuestion: "Open-set recognition",
      targetVenue: "TWC",
      writingStatus: "REVISING",
      files: [
        { fileId: "F1", folderType: "draftManuscripts" },
        { fileId: "F2", folderType: "coreReferences" }
      ],
      patches: [
        { patchId: "P1", status: "PENDING" },
        { patchId: "P2", status: "APPLIED" }
      ]
    });

    expect(model.title).toBe("AMR Paper");
    expect(model.statusLabel).toBe("修改中");
    expect(model.fileCount).toBe(2);
    expect(model.pendingPatchCount).toBe(1);
    expect(model.draftFiles).toHaveLength(1);
    expect(model.referenceFiles).toHaveLength(1);
    expect(model.contextSummary).toContain("Open-set recognition");
  });

  it("returns empty-state labels without selected project", () => {
    const model = buildAgentWorkspace(null);

    expect(model.title).toBe("未选择工作区");
    expect(model.contextSummary).toBe("当前还没有工作区上下文");
  });
});
