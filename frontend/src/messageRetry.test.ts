import { describe, expect, it } from "vitest";
import { assistantMessageCanRetry, retryFilesFromUserMessage } from "./messageRetry";

describe("message retry helpers", () => {
  it("marks assistant transport failures as retryable", () => {
    expect(assistantMessageCanRetry({
      role: "assistant",
      content: "请求出错：HTTP/1.1 header parser received no bytes"
    })).toBe(true);
  });

  it("marks failed timeline items as retryable", () => {
    expect(assistantMessageCanRetry({
      role: "assistant",
      content: "",
      timeline: [{ type: "run", status: "FAILED", content: "模型调用失败" }]
    })).toBe(true);
  });

  it("normalizes retry files from the previous user message", () => {
    expect(retryFilesFromUserMessage({
      files: [
        { fileId: "F1", name: "paper.pdf", contentType: "application/pdf" },
        { name: "missing-id.txt" }
      ]
    })).toEqual([
      {
        fileId: "F1",
        clientId: "F1",
        name: "paper.pdf",
        contentType: "application/pdf",
        fileType: "application/pdf",
        status: "parsed"
      }
    ]);
  });
});
