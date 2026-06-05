import { describe, expect, it } from "vitest";

import { createToolProxyConfig } from "./toolProxy.js";

describe("createToolProxyConfig", () => {
  it("proxies tool requests to the default local runtime", () => {
    const proxy = createToolProxyConfig();

    expect(proxy.target).toBe("http://127.0.0.1:1601");
    expect(proxy.changeOrigin).toBe(true);
    expect(proxy.rewrite("/tool/v1/file_tool/preview/req/demo.html")).toBe(
      "/v1/file_tool/preview/req/demo.html"
    );
  });

  it("preserves a configured base path", () => {
    const proxy = createToolProxyConfig("https://example.com/tool");

    expect(proxy.target).toBe("https://example.com");
    expect(proxy.rewrite("/tool/v1/image_generation/preview/demo.png")).toBe(
      "/tool/v1/image_generation/preview/demo.png"
    );
  });
});
