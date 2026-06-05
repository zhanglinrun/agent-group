type AcademicSseEvent = {
  event?: unknown;
  [key: string]: unknown;
};

export function splitAcademicSseBlocks(buffer: string): { blocks: string[]; rest: string } {
  const parts = String(buffer || "").split(/\r?\n\r?\n/);
  return {
    blocks: parts.slice(0, -1).filter((block) => block.trim()),
    rest: parts.at(-1) || ""
  };
}

export function academicSseData(block: string): string {
  const lines = String(block || "").split(/\r?\n/);
  const dataLines = lines.filter((line) => line.startsWith("data:"));
  return (dataLines.length ? dataLines : lines)
    .map((line) => line.replace(/^data:\s*/, "").trim())
    .filter((line) => line && !line.startsWith("event:") && !line.startsWith("id:") && !line.startsWith("retry:"))
    .join("");
}

export function parseAcademicSseBlock(block: string): AcademicSseEvent | null {
  const data = academicSseData(block);
  if (!data || data === "[DONE]") {
    return null;
  }
  try {
    const parsed = JSON.parse(data);
    return parsed && typeof parsed === "object" ? parsed as AcademicSseEvent : null;
  } catch {
    return null;
  }
}

export function isAcademicTerminalEvent(event: unknown): boolean {
  if (!event || typeof event !== "object") {
    return false;
  }
  const value = String((event as AcademicSseEvent).event || "").toLowerCase();
  return value === "done" || value === "error";
}
