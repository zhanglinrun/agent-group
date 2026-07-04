import { describe, expect, it, vi } from "vitest";
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";

import { UserMemoryPanel } from "./UserMemoryPanel";

describe("UserMemoryPanel", () => {
  it("renders enabled and disabled memories", () => {
    const html = renderToStaticMarkup(createElement(UserMemoryPanel, {
      memories: [
        { memoryType: "output_style", content: "先结论后证据", enabled: true, source: "auto", scope: "global" },
        { memoryType: "business_context", content: "多模式 Agent 工作台", enabled: false }
      ],
      onRefresh: () => {},
      onDisable: () => {}
    }));

    expect(html).toContain("长期记忆");
    expect(html).toContain("输出风格");
    expect(html).toContain("先结论后证据");
    expect(html).toContain("自动");
    expect(html).toContain("全局");
    expect(html).toContain("多模式 Agent 工作台");
    expect(html).toContain("停用");
    expect(html).toContain("启用");
    expect(html).toContain("删除");
  });

  it("renders login prompt when unauthenticated", () => {
    const html = renderToStaticMarkup(createElement(UserMemoryPanel, {
      authenticated: false,
      onLogin: () => {},
      onRefresh: () => {}
    }));

    expect(html).toContain("长期记忆");
    expect(html).toContain("登录后展示长期记忆");
    expect(html).toContain("登录");
  });

  it("calls memory action handlers with selected memory", () => {
    const onToggle = vi.fn();
    const onEnable = vi.fn();
    const onDisable = vi.fn();
    const onDelete = vi.fn();
    const memory = { memoryType: "preference", content: "完整交付", enabled: true };
    const element = UserMemoryPanel({
      memories: [memory],
      loading: false,
      onRefresh: () => {},
      onToggle,
      onEnable,
      onDisable,
      onDelete
    });
    const switchInput = element.props.children[0].props.children[1].props.children[0].props.children[0];
    const article = element.props.children[2].props.children[0];
    const actions = article.props.children[1];

    switchInput.props.onChange({ target: { checked: false } });
    actions.props.children[0].props.onClick();
    actions.props.children[1].props.onClick();

    expect(onToggle).toHaveBeenCalledWith(false);
    expect(onEnable).not.toHaveBeenCalled();
    expect(onDisable).toHaveBeenCalledWith(memory);
    expect(onDelete).toHaveBeenCalledWith(memory);
  });
});
