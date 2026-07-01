import { describe, expect, it } from "vitest";

import {
  agentSseData,
  isAgentTerminalEvent,
  parseAgentSseBlock,
  splitAgentSseBlocks
} from "./agentSse";

describe("agent SSE parser", () => {
  it("splits completed blocks and keeps the trailing partial block", () => {
    expect(splitAgentSseBlocks(
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

    expect(agentSseData(block)).toBe("{\"event\":\"tool_result\",\"data\":{\"summary\":\"ok\"}}");
    expect(parseAgentSseBlock(block)).toEqual({
      event: "tool_result",
      data: { summary: "ok" }
    });
  });

  it("returns null for empty invalid and done-marker blocks", () => {
    expect(parseAgentSseBlock("")).toBeNull();
    expect(parseAgentSseBlock("data: [DONE]")).toBeNull();
    expect(parseAgentSseBlock("data: {bad json}")).toBeNull();
  });

  it("recognizes terminal agent events", () => {
    expect(isAgentTerminalEvent({ event: "done" })).toBe(true);
    expect(isAgentTerminalEvent({ event: "error" })).toBe(true);
    expect(isAgentTerminalEvent({ event: "tool_result" })).toBe(false);
  });
});
