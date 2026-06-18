import { describe, expect, it } from "vitest";

import {
  artifactMetaLabel,
  assistantReasoningMeta,
  buildDataChartPreview,
  formatFileSize,
  formatPanelValue,
  formatTradeNumber,
  hostFromUrl,
  normalizeRecommendItems,
  tradeOrderAmount
} from "./appFormatters";

describe("app formatters", () => {
  it("formats trade numbers and order amounts", () => {
    expect(formatTradeNumber(12)).toBe("12.00");
    expect(tradeOrderAmount({ payAmount: 19.9 })).toBe("19.90");
    expect(tradeOrderAmount({ totalAmount: 8 })).toBe("8.00");
  });

  it("formats file and panel values", () => {
    expect(formatFileSize(0)).toBe("-");
    expect(formatFileSize(512)).toBe("512 B");
    expect(formatFileSize(1536)).toBe("1.5 KB");
    expect(formatPanelValue({ status: "ok" })).toBe('{"status":"ok"}');
    expect(formatPanelValue("")).toBe("-");
  });

  it("normalizes artifact and host labels", () => {
    expect(artifactMetaLabel({ fileSize: 2048, toolName: "report_tool" })).toBe("2.0 KB · 来源 报告工具");
    expect(hostFromUrl("https://www.example.com/path")).toBe("example.com");
    expect(hostFromUrl("docs.example.com/path")).toBe("docs.example.com");
  });

  it("builds simple data chart previews", () => {
    expect(buildDataChartPreview({
      columns: ["name", "score"],
      rows: [
        { name: "A", score: "10" },
        { name: "B", score: "20" }
      ]
    })).toMatchObject({
      dimension: "name",
      measure: "score",
      maxValue: 20,
      points: [
        { label: "A", value: 10 },
        { label: "B", value: 20 }
      ]
    });
  });

  it("summarizes reasoning metadata", () => {
    expect(assistantReasoningMeta({
      timeline: [
        { type: "tool", invocationId: "tool-1", latencyMillis: 500 },
        { type: "diagnosis", metrics: { elapsedMs: 1500 } }
      ],
      artifacts: [{ id: "a1" }]
    }, [{ version: 1 }])).toBe("用时 1.5 秒 · 1 次工具 · 1 版计划");
  });

  it("normalizes recommendation items from common shapes", () => {
    expect(normalizeRecommendItems('["问题一", "问题二"]')).toEqual(["问题一", "问题二"]);
    expect(normalizeRecommendItems({ items: [{ question: "下一步做什么" }] })).toEqual(["下一步做什么"]);
  });
});
