import { describe, expect, it } from "vitest";
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";

import { WorkspacePanelHeader } from "./WorkspacePanelHeader";

describe("WorkspacePanelHeader", () => {
  it("renders title, subtitle, eyebrow, and trailing actions", () => {
    const html = renderToStaticMarkup(createElement(WorkspacePanelHeader, {
      className: "workspace-head",
      eyebrow: createElement("span", { className: "kicker" }, "工作区"),
      title: "数据上下文",
      subtitle: "随下一次请求提交",
      trailing: createElement("button", { type: "button" }, "刷新")
    }));

    expect(html).toContain("workspace-head");
    expect(html).toContain("工作区");
    expect(html).toContain("数据上下文");
    expect(html).toContain("随下一次请求提交");
    expect(html).toContain("刷新");
  });

  it("lets custom subtitle markup replace plain subtitle text", () => {
    const html = renderToStaticMarkup(createElement(WorkspacePanelHeader, {
      className: "workspace-head",
      title: "项目上下文",
      subtitle: "不会显示",
      subtitleElement: createElement("em", null, "当前项目")
    }));

    expect(html).toContain("<em>当前项目</em>");
    expect(html).not.toContain("不会显示");
  });
});
