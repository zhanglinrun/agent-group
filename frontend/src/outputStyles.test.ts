import { describe, expect, it } from "vitest";

import { normalizeOutputStyle, outputStylePayload } from "./outputStyles";

describe("output styles", () => {
  it("normalizes unknown style to auto", () => {
    expect(normalizeOutputStyle("unknown")).toBe("auto");
    expect(normalizeOutputStyle("REPORT")).toBe("report");
  });

  it("does not send auto style to backend", () => {
    expect(outputStylePayload("auto")).toBe("");
    expect(outputStylePayload("trade-audit")).toBe("trade-audit");
  });
});
