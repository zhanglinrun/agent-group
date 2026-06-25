import { afterEach, describe, expect, it, vi } from "vitest";

import {
  apiSucceeded,
  attachReplayTimeline,
  hasAssistantPayload,
  isImageArtifact,
  latestAssistantWithPayload,
  preferredFrontendPayChannel,
  safeExternalUrl,
  safeResourceUrl,
  toUiReference,
  workspaceDataToolResultEvent,
  workspaceImageToolResultEvent
} from "./appRuntime";

describe("app runtime helpers", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it("recognizes backend success code variants", () => {
    expect(apiSucceeded({ code: "0000" })).toBe(true);
    expect(apiSucceeded({ code: 200 })).toBe(true);
    expect(apiSucceeded({ code: "200" })).toBe(true);
    expect(apiSucceeded({ code: "500" })).toBe(false);
  });

  it("keeps only safe http resource urls", () => {
    expect(safeExternalUrl("https://example.com/a.png")).toBe("https://example.com/a.png");
    expect(safeExternalUrl("javascript:alert(1)")).toBe("");
    expect(safeResourceUrl("/api/files/report.md")).toBe("/api/files/report.md");
    expect(safeResourceUrl("//example.com/report.md")).toBe("");
  });

  it("detects assistant messages that should stay visible", () => {
    expect(hasAssistantPayload({ content: "  " })).toBe(false);
    expect(hasAssistantPayload({ artifacts: [{ fileName: "report.md" }] })).toBe(true);

    const latest = latestAssistantWithPayload([
      { role: "assistant", content: "" },
      { role: "user", content: "next" },
      { role: "assistant", timeline: [{ id: "t1" }] }
    ]);
    expect(latest?.timeline).toHaveLength(1);
  });

  it("attaches replay data to the latest assistant message", () => {
    const messages = [
      { role: "assistant", content: "old", artifacts: [] },
      { role: "user", content: "go" },
      { role: "assistant", content: "new", artifacts: [] }
    ];

    const next = attachReplayTimeline(
      messages,
      [{ id: "event-1" }],
      [{ id: "artifact-1", fileName: "report.md" }],
      [{ id: "panel-1", kind: "report" }]
    );

    expect(next[0].timeline).toBeUndefined();
    expect(next[2]).toMatchObject({
      showTimeline: false,
      timeline: [{ id: "event-1" }],
      artifacts: [{ id: "artifact-1", fileName: "report.md" }],
      resultPanels: [{ id: "panel-1", kind: "report" }]
    });
  });

  it("normalizes references and image artifacts", () => {
    expect(toUiReference({ fileId: "file-1", snippet: "excerpt" })).toEqual({
      title: "file-1",
      url: "",
      text: "excerpt"
    });
    expect(isImageArtifact({ contentType: "image/png" })).toBe(true);
    expect(isImageArtifact({ fileName: "chart.webp?download=1" })).toBe(true);
    expect(isImageArtifact({ fileName: "report.pdf" })).toBe(false);
  });

  it("uses explicit or local payment channel defaults", () => {
    expect(preferredFrontendPayChannel("alipay")).toBe("ALIPAY");

    vi.stubGlobal("window", {
      location: {
        hostname: "localhost"
      }
    });
    expect(preferredFrontendPayChannel()).toBe("ALIPAY");
  });

  it("builds workspace tool result events consistently", () => {
    vi.spyOn(Date, "now").mockReturnValue(1234);

    expect(workspaceDataToolResultEvent({ title: "dataset", content: "ok" })).toMatchObject({
      event: "tool_result",
      data: {
        invocationId: "data_1234",
        toolName: "data_analysis",
        structuredOutput: {
          title: "dataset",
          content: "ok"
        }
      }
    });

    expect(workspaceImageToolResultEvent({
      title: "cover",
      fileRefs: [{ fileName: "cover.png" }]
    }, "image-1")).toMatchObject({
      event: "tool_result",
      data: {
        invocationId: "image-1",
        toolName: "image_generation",
        structuredOutput: {
          title: "cover",
          fileRefs: [{ fileName: "cover.png" }]
        }
      }
    });
  });
});
