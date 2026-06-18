import { describe, expect, it } from "vitest";
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";

import { ImageWorkspacePanel } from "./ImageWorkspacePanel";

describe("ImageWorkspacePanel", () => {
  it("renders selected image options from the draft", () => {
    const html = renderToStaticMarkup(createElement(ImageWorkspacePanel, {
      draft: {
        model: "gpt-image-2",
        quality: "high",
        ratioPreset: "16:9-4k",
        aspectRatio: "16:9",
        size: "3840x2160",
        batchCount: 3
      },
      onChange: () => {},
      hasReference: true,
      compact: true
    }));

    expect(html).toContain("composer-image-settings");
    expect(html).toContain("已有参考图");
    expect(html).toContain('value="gpt-image-2"');
    expect(html).toContain("3840x2160");
    expect(html).toContain("16:9 4K");
    expect(html).toContain("3 张");
  });
});
