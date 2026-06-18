import { describe, expect, it } from "vitest";
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";

import { AcademicProjectPanel } from "./AcademicProjectPanel";

describe("AcademicProjectPanel", () => {
  it("renders project metrics, files, references, and pending patches", () => {
    const html = renderToStaticMarkup(createElement(AcademicProjectPanel, {
      projects: [
        { projectId: "project-1", title: "论文复现实验" }
      ],
      model: {
        title: "论文复现实验",
        subtitle: "当前项目上下文",
        contextSummary: "",
        statusLabel: "进行中",
        fileCount: 2,
        pendingPatchCount: 1,
        draftFiles: [
          { fileId: "draft-1", fileName: "实验记录.md", summary: "训练日志", fileType: "md" }
        ],
        referenceFiles: [
          { fileId: "ref-1", fileName: "论文.pdf", summary: "参考论文", fileType: "pdf" }
        ],
        pendingPatches: [
          { patchId: "patch-1", title: "补充实验结论", reason: "需要确认后写入报告" }
        ]
      },
      activeProjectId: "project-1",
      loading: false,
      error: "",
      onRefresh: () => {},
      onCreate: () => {},
      onSelect: () => {},
      onApplyPatch: () => {}
    }));

    expect(html).toContain("工作上下文");
    expect(html).toContain("论文复现实验");
    expect(html).toContain("进行中");
    expect(html).toContain("实验记录.md");
    expect(html).toContain("论文.pdf");
    expect(html).toContain("补充实验结论");
    expect(html).toContain("确认");
  });
});
