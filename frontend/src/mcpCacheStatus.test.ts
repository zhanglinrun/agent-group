import { describe, expect, it } from "vitest";

import { formatMcpCacheAge, mcpCacheStatusText } from "./mcpCacheStatus";

describe("mcp cache status display", () => {
  it("formats cache age into readable units", () => {
    expect(formatMcpCacheAge(0)).toBe("");
    expect(formatMcpCacheAge(42)).toBe("42 秒");
    expect(formatMcpCacheAge(180)).toBe("3 分钟");
    expect(formatMcpCacheAge(7200)).toBe("2 小时");
  });

  it("builds readable cache status text", () => {
    expect(mcpCacheStatusText(null)).toBe("未缓存");
    expect(mcpCacheStatusText({ cacheStatus: "fresh", cacheAgeSeconds: 125 })).toBe("缓存有效 · 2 分钟");
    expect(mcpCacheStatusText({ cacheStatus: "expired", cacheAgeSeconds: 3660 })).toBe("缓存过期 · 1 小时");
    expect(mcpCacheStatusText({ cacheStatus: "custom" })).toBe("custom");
  });
});
