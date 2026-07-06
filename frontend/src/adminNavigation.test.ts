import { describe, expect, it } from "vitest";

import { ADMIN_MENU_GROUPS, adminMenuItem, adminMenuKeysByOwner } from "./adminNavigation";

describe("admin navigation ownership", () => {
  it("routes model skills and mcp management to Reactor Agent backend", () => {
    expect(adminMenuKeysByOwner("reactor-agent")).toEqual(["llmConfig", "skills", "mcp"]);
    expect(adminMenuItem("llmConfig")?.label).toBe("模型配置");
    expect(adminMenuItem("skills")?.backendOwner).toBe("reactor-agent");
    expect(adminMenuItem("mcp")?.backendOwner).toBe("reactor-agent");
  });

  it("keeps group buy order refund and ops pages on this project's trade backend", () => {
    expect(adminMenuKeysByOwner("agent-group-trade")).toEqual([
      "activity",
      "channel",
      "order",
      "refund",
      "tradeOps",
      "rules"
    ]);
    expect(adminMenuItem("activity")?.backendOwner).toBe("agent-group-trade");
    expect(adminMenuItem("refund")?.backendOwner).toBe("agent-group-trade");
  });

  it("keeps every sidebar item addressable by key", () => {
    const keys = ADMIN_MENU_GROUPS.flatMap((group) => group.items.map((item) => item.key));
    expect(new Set(keys).size).toBe(keys.length);
    expect(adminMenuItem("missing")).toBeNull();
  });
});
