import { describe, expect, it, vi, afterEach } from "vitest";

import { buildArtifactPreviewModel } from "./artifactPreview";

describe("artifact preview model", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("detects image artifacts from preview url", () => {
    const model = buildArtifactPreviewModel({
      title: "poster",
      previewUrl: "https://example.com/poster.png",
      contentType: "image/png"
    });

    expect(model.kind).toBe("image");
    expect(model.canPreview).toBe(true);
    expect(model.title).toBe("poster");
  });

  it("detects html and text artifacts by extension", () => {
    expect(buildArtifactPreviewModel({
      fileName: "report.html",
      downloadUrl: "https://example.com/report.html"
    }).kind).toBe("html");

    expect(buildArtifactPreviewModel({
      fileName: "summary.md",
      content: "# summary"
    })).toMatchObject({
      kind: "text",
      canPreview: true,
      inlineText: "# summary"
    });
  });

  it("normalizes tool fileInfo fields into preview metadata", () => {
    const model = buildArtifactPreviewModel({
      displayName: "analysis.md",
      domainUrl: "/tool/files/analysis.md",
      mimeType: "text/markdown",
      resourceKey: "analysis-resource"
    });

    expect(model).toMatchObject({
      kind: "text",
      canPreview: true,
      fileName: "analysis.md",
      title: "analysis.md",
      type: "text/markdown",
      url: "/tool/files/analysis.md",
      downloadUrl: "/tool/files/analysis.md"
    });
  });

  it("normalizes primary file fields into preview metadata", () => {
    const model = buildArtifactPreviewModel({
      primaryFileName: "summary.md",
      ossUrl: "/files/summary.md",
      mimeType: "text/markdown"
    });

    expect(model).toMatchObject({
      kind: "text",
      fileName: "summary.md",
      url: "/files/summary.md",
      downloadUrl: "/files/summary.md"
    });
  });

  it("rejects unsafe resource urls", () => {
    const model = buildArtifactPreviewModel({
      fileName: "bad.html",
      previewUrl: "javascript:alert(1)"
    });

    expect(model.url).toBe("");
    expect(model.kind).toBe("none");
  });

  it("rewrites local tool preview urls through browser proxy", () => {
    vi.stubGlobal("window", {
      location: {
        host: "localhost:5173",
        protocol: "http:"
      }
    });

    expect(buildArtifactPreviewModel({
      fileName: "demo.html",
      previewUrl: "http://127.0.0.1:1601/v1/file_tool/preview/req/demo.html"
    }).url).toBe("http://localhost:5173/tool/v1/file_tool/preview/req/demo.html");
  });
});
