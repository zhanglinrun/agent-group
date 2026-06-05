import { describe, expect, it } from "vitest";

import { isAdminRoute, WORKSPACE_ROUTES } from "./routes";

describe("app routes", () => {
  it("detects admin routes", () => {
    expect(isAdminRoute("/admin")).toBe(true);
    expect(isAdminRoute("/admin.html")).toBe(true);
    expect(isAdminRoute("/workspace/image")).toBe(false);
  });

  it("keeps workspace routes registered", () => {
    expect(WORKSPACE_ROUTES).toContain("/workspace/image");
    expect(WORKSPACE_ROUTES).toContain("/workspace/data");
    expect(WORKSPACE_ROUTES).toContain("/workspace/mrag");
    expect(WORKSPACE_ROUTES).toContain("/workspace/trade");
  });
});
