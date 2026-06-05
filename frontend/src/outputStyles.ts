export type OutputStyleKey = "auto" | "brief" | "report" | "interview" | "trade-audit" | "html";

export type OutputStyleOption = {
  key: OutputStyleKey;
  label: string;
  description: string;
};

export const OUTPUT_STYLE_OPTIONS: OutputStyleOption[] = [
  { key: "auto", label: "自动", description: "保持当前工作区默认输出方式" },
  { key: "brief", label: "简洁", description: "优先给结论、依据和下一步" },
  { key: "report", label: "报告", description: "按结构化报告输出" },
  { key: "interview", label: "面试亮点", description: "突出架构、取舍和项目价值" },
  { key: "trade-audit", label: "交易审计", description: "严格区分支付、成团、到账和退款" },
  { key: "html", label: "HTML", description: "偏网页报告结构" }
];

const STYLE_KEYS = new Set(OUTPUT_STYLE_OPTIONS.map((item) => item.key));

export function normalizeOutputStyle(value: unknown): OutputStyleKey {
  const style = String(value ?? "").trim().toLowerCase() as OutputStyleKey;
  return STYLE_KEYS.has(style) ? style : "auto";
}

export function outputStylePayload(value: unknown): string {
  const style = normalizeOutputStyle(value);
  return style === "auto" ? "" : style;
}
