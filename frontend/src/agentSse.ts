type AgentSseEvent = {
  event?: unknown;
  [key: string]: unknown;
};

export function splitAgentSseBlocks(buffer: string): { blocks: string[]; rest: string } {
  const parts = String(buffer || "").split(/\r?\n\r?\n/);
  return {
    blocks: parts.slice(0, -1).filter((block) => block.trim()),
    rest: parts.at(-1) || ""
  };
}

export function agentSseData(block: string): string {
  const lines = String(block || "").split(/\r?\n/);
  const dataLines = lines.filter((line) => line.startsWith("data:"));
  return (dataLines.length ? dataLines : lines)
    .map((line) => line.replace(/^data:\s*/, "").trim())
    .filter((line) => line && !line.startsWith("event:") && !line.startsWith("id:") && !line.startsWith("retry:"))
    .join("");
}

export function parseAgentSseBlock(block: string): AgentSseEvent | null {
  const data = agentSseData(block);
  if (!data || data === "[DONE]") {
    return null;
  }
  try {
    const parsed = JSON.parse(data);
    return parsed && typeof parsed === "object" ? parsed as AgentSseEvent : null;
  } catch {
    return null;
  }
}

export function isAgentTerminalEvent(event: unknown): boolean {
  if (!event || typeof event !== "object") {
    return false;
  }
  const value = String((event as AgentSseEvent).event || "").toLowerCase();
  return value === "done" || value === "error";
}
