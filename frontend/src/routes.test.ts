import { describe, expect, it } from "vitest";

import { INTERNAL_WORKSPACE_ROUTES, WORKSPACE_ROUTES, isAdminRoute } from "./routes";

describe("app routes", () => {
  it("detects admin routes", () => {
    expect(isAdminRoute("/admin")).toBe(true);
    expect(isAdminRoute("/admin.html")).toBe(false);
    expect(isAdminRoute("/workspace/image")).toBe(false);
  });

  it("keeps user workspace routes focused", () => {
    expect(WORKSPACE_ROUTES).toEqual(["/", "/workspace/image"]);
  });

  it("keeps internal workspace routes registered", () => {
    expect(WORKSPACE_ROUTES).toContain("/workspace/image");
    expect(INTERNAL_WORKSPACE_ROUTES).toContain("/workspace/data");
    expect(INTERNAL_WORKSPACE_ROUTES).toContain("/workspace/mrag");
    expect(INTERNAL_WORKSPACE_ROUTES).toContain("/workspace/trade");
  });
});
