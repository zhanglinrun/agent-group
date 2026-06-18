import { describe, expect, it } from "vitest";
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";

import { MarkdownRenderer } from "./MarkdownRenderer";

describe("MarkdownRenderer", () => {
  it("renders the supported markdown blocks and safe inline links", () => {
    const html = renderToStaticMarkup(createElement(MarkdownRenderer, {
      content: [
        "# 标题",
        "",
        "正文 **重点** https://example.com/doc。",
        "",
        "| 列 | 值 |",
        "| --- | --- |",
        "| A | 1 |",
        "",
        "> 引用",
        "",
        "- 项目",
        "1. 步骤"
      ].join("\n")
    }));

    expect(html).toContain("<h3");
    expect(html).toContain("标题");
    expect(html).toContain("<strong");
    expect(html).toContain("重点");
    expect(html).toContain('href="https://example.com/doc"');
    expect(html).toContain("<table");
    expect(html).toContain("<blockquote");
    expect(html).toContain("<ul");
    expect(html).toContain("<ol");
  });
});
