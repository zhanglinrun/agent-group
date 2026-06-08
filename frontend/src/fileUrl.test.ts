import { afterEach, describe, expect, it, vi } from "vitest";

import {
  normalizeFileUrlForBrowser,
  normalizeToolBaseUrlForBrowser
} from "./fileUrl";

describe("file url normalization", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("rewrites local reactor-tool file urls to the current tool proxy", () => {
    vi.stubGlobal("window", {
      location: {
        host: "localhost:5173",
        hostname: "localhost",
        protocol: "http:"
      }
    });

    expect(
      normalizeFileUrlForBrowser("http://127.0.0.1:1601/v1/file_tool/preview/req/demo.html")
    ).toBe("http://localhost:5173/tool/v1/file_tool/preview/req/demo.html");
  });

  it("keeps ordinary external urls unchanged", () => {
    vi.stubGlobal("window", {
      location: {
        host: "localhost:5173",
        hostname: "localhost",
        protocol: "http:"
      }
    });

    expect(normalizeFileUrlForBrowser("https://example.com/demo.png")).toBe(
      "https://example.com/demo.png"
    );
  });

  it("keeps safe inline image data urls for local fallback previews", () => {
    expect(normalizeFileUrlForBrowser("data:image/png;base64,iVBORw0KGgo=")).toBe(
      "data:image/png;base64,iVBORw0KGgo="
    );
  });

  it("returns the current tool base url when no runtime url is configured", () => {
    vi.stubGlobal("window", {
      location: {
        host: "localhost:5173",
        hostname: "localhost",
        protocol: "http:"
      }
    });

    expect(normalizeToolBaseUrlForBrowser()).toBe("http://localhost:5173/tool");
  });
});
