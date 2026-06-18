type UnknownMap = Record<string, unknown>;

export type AgentExecutionSummaryStatus = "ready" | "partial" | "missing";

export interface AgentExecutionFamilyCoverage {
  key: string;
  label: string;
  count: number;
  covered: boolean;
}

export interface AgentExecutionSummaryMetric {
  key: string;
  label: string;
  value: string;
  tone?: "normal" | "good" | "warn";
}

export interface AgentExecutionSummary {
  status: AgentExecutionSummaryStatus;
  modeCount: number;
  requiredFamilyCount: number;
  coveredFamilyCount: number;
  coveredFamilies: AgentExecutionFamilyCoverage[];
  missingFamilies: AgentExecutionFamilyCoverage[];
  replanModeIds: string[];
  replanEvidence: string[];
  metrics: AgentExecutionSummaryMetric[];
  actions: string[];
}

const REQUIRED_FAMILIES = [
  { key: "react", label: "ReAct" },
  { key: "plan-execute", label: "Plan-Execute" },
  { key: "ppt-workflow", label: "PPT Workflow" },
  { key: "skill-orchestration", label: "Skill Orchestration" }
];

function asObject(value: unknown): UnknownMap {
  return value && typeof value === "object" && !Array.isArray(value)
    ? value as UnknownMap
    : {};
}

function asArray(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

function text(value: unknown): string {
  return String(value ?? "").trim();
}

function bool(value: unknown): boolean {
  if (typeof value === "boolean") return value;
  const normalized = text(value).toLowerCase();
  return normalized === "true" || normalized === "1" || normalized === "yes";
}

function stringList(value: unknown): string[] {
  return asArray(value).map((item) => text(item)).filter(Boolean);
}

function unique(values: string[]): string[] {
  return [...new Set(values.map((item) => item.trim()).filter(Boolean))];
}

function modeFamily(mode: UnknownMap): string {
  return text(mode.family) || text(mode.executionFamily) || "unknown";
}

function modeId(mode: UnknownMap, index: number): string {
  return text(mode.agentId) || text(mode.id) || `mode-${index}`;
}

export function buildAgentExecutionSummary(modes: unknown[] = []): AgentExecutionSummary {
  const normalizedModes = modes.map(asObject);
  const familyCounts = new Map<string, number>();
  const replanModeIds: string[] = [];
  const replanEvidence: string[] = [];

  normalizedModes.forEach((mode, index) => {
    const family = modeFamily(mode);
    familyCounts.set(family, (familyCounts.get(family) || 0) + 1);
    const evidence = stringList(mode.replanEvidence);
    if (bool(mode.replanEnabled) || evidence.length > 0) {
      replanModeIds.push(modeId(mode, index));
      replanEvidence.push(...evidence);
    }
  });

  const familyCoverage = REQUIRED_FAMILIES.map((family) => {
    const count = familyCounts.get(family.key) || 0;
    return {
      ...family,
      count,
      covered: count > 0
    };
  });
  const coveredFamilies = familyCoverage.filter((family) => family.covered);
  const missingFamilies = familyCoverage.filter((family) => !family.covered);
  const replanIds = unique(replanModeIds);
  const status: AgentExecutionSummaryStatus = normalizedModes.length === 0
    ? "missing"
    : missingFamilies.length === 0 && replanIds.length > 0
      ? "ready"
      : "partial";

  const actions = unique([
    missingFamilies.length > 0
      ? `补齐 ${missingFamilies.map((family) => family.label).join("、")} 执行策略`
      : "",
    normalizedModes.length > 0 && replanIds.length === 0 ? "接入动态重规划证据" : "",
    status === "ready" ? "执行策略覆盖完整" : ""
  ]);

  return {
    status,
    modeCount: normalizedModes.length,
    requiredFamilyCount: REQUIRED_FAMILIES.length,
    coveredFamilyCount: coveredFamilies.length,
    coveredFamilies,
    missingFamilies,
    replanModeIds: replanIds,
    replanEvidence: unique(replanEvidence),
    metrics: [
      {
        key: "families",
        label: "执行族",
        value: `${coveredFamilies.length}/${REQUIRED_FAMILIES.length}`,
        tone: missingFamilies.length === 0 ? "good" : "warn"
      },
      {
        key: "modes",
        label: "模式",
        value: String(normalizedModes.length),
        tone: normalizedModes.length > 0 ? "good" : "warn"
      },
      {
        key: "replan",
        label: "重规划",
        value: String(replanIds.length),
        tone: replanIds.length > 0 ? "good" : "warn"
      }
    ],
    actions
  };
}
