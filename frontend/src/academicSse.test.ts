import { describe, expect, it } from "vitest";

import {
  academicSseData,
  isAcademicTerminalEvent,
  parseAcademicSseBlock,
  splitAcademicSseBlocks
} from "./academicSse";

describe("academic SSE parser", () => {
  it("splits completed blocks and keeps the trailing partial block", () => {
    expect(splitAcademicSseBlocks(
      "data: {\"event\":\"run_start\"}\n\n"
      + "data: {\"event\":\"tool_call\"}"
    )).toEqual({
      blocks: ["data: {\"event\":\"run_start\"}"],
      rest: "data: {\"event\":\"tool_call\"}"
    });
  });

  it("extracts JSON data while ignoring protocol metadata lines", () => {
    const block = [
      "event: message",
      "id: 42",
      "retry: 3000",
      "data: {\"event\":\"tool_result\",",
      "data: \"data\":{\"summary\":\"ok\"}}"
    ].join("\n");

    expect(academicSseData(block)).toBe("{\"event\":\"tool_result\",\"data\":{\"summary\":\"ok\"}}");
    expect(parseAcademicSseBlock(block)).toEqual({
      event: "tool_result",
      data: { summary: "ok" }
    });
  });

  it("returns null for empty invalid and done-marker blocks", () => {
    expect(parseAcademicSseBlock("")).toBeNull();
    expect(parseAcademicSseBlock("data: [DONE]")).toBeNull();
    expect(parseAcademicSseBlock("data: {bad json}")).toBeNull();
  });

  it("recognizes terminal academic events", () => {
    expect(isAcademicTerminalEvent({ event: "done" })).toBe(true);
    expect(isAcademicTerminalEvent({ event: "error" })).toBe(true);
    expect(isAcademicTerminalEvent({ event: "tool_result" })).toBe(false);
  });
});
