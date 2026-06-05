import { USER_WORKSPACES } from "./workspaces";

type UnknownMap = Record<string, unknown>;

export type AgentPlatformReadinessStatus = "ready" | "partial" | "missing";

export interface AgentPlatformReadinessMetric {
  key: string;
  label: string;
  value: string;
  tone?: "normal" | "good" | "warn";
}

export interface AgentPlatformReadinessMcpHealth {
  status: string;
  statusLabel: string;
  summary: string;
  tone: "normal" | "good" | "warn";
  serverCount: number;
  enabledServerCount: number;
  readyServerCount: number;
  degradedServerCount: number;
  toolCount: number;
  enabledToolCount: number;
  message?: string;
}

export interface AgentPlatformReadiness {
  status: AgentPlatformReadinessStatus;
  statusLabel: string;
  title: string;
  metrics: AgentPlatformReadinessMetric[];
  gaps: string[];
  actions: string[];
  missingTools: string[];
  mcpGaps: string[];
  tradeGuardrails: string[];
  mcpHealth: AgentPlatformReadinessMcpHealth | null;
}

const REQUIRED_FAMILIES = ["react", "plan-execute", "flow", "skill-sop"];
const REQUIRED_WORKSPACES = USER_WORKSPACES.map((workspace) => workspace.id);
const REQUIRED_RUNTIME_TOOLS = [
  "web_fetch",
  "data_analysis",
  "report_tool",
  "planning",
  "code_interpreter",
  "image_generation",
  "multimodal_agent",
  "deep_search",
  "file_tool",
  "script_runner",
  "table_rag",
  "nl2sql",
  "trade_audit"
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
  return unique(asArray(value).map((item) => text(item)).filter(Boolean));
}

function unique(values: string[]): string[] {
  return [...new Set(values.map((item) => item.trim()).filter(Boolean))];
}

function numberValue(value: unknown): number {
  const parsed = Number(value ?? 0);
  return Number.isFinite(parsed) ? parsed : 0;
}

function normalizeStatus(value: unknown, fallback: AgentPlatformReadinessStatus): AgentPlatformReadinessStatus {
  const normalized = text(value).toLowerCase();
  if (normalized === "ready" || normalized === "partial" || normalized === "missing") {
    return normalized;
  }
  return fallback;
}

function statusLabel(status: AgentPlatformReadinessStatus): string {
  return ({
    ready: "已就绪",
    partial: "待补齐",
    missing: "未接入"
  } as Record<AgentPlatformReadinessStatus, string>)[status];
}

function metric(key: string, label: string, value: string, tone: "normal" | "good" | "warn" = "normal") {
  return { key, label, value, tone };
}

function normalizeMetric(value: unknown): AgentPlatformReadinessMetric {
  const item = asObject(value);
  return {
    key: text(item.key) || text(item.label),
    label: text(item.label) || text(item.key),
    value: text(item.value),
    tone: text(item.tone) as "normal" | "good" | "warn" || "normal"
  };
}

function mcpStatusLabel(status: string): string {
  return ({
    ready: "已就绪",
    degraded: "需处理",
    missing: "未加载",
    empty: "未注册",
    disabled: "未启用",
    partial: "待补齐"
  } as Record<string, string>)[status.toLowerCase()] || status || "未知";
}

function normalizeMcpHealth(value: unknown): AgentPlatformReadinessMcpHealth | null {
  const item = asObject(value);
  if (Object.keys(item).length === 0) {
    return null;
  }
  const status = text(item.overallStatus) || text(item.status) || "missing";
  const serverCount = numberValue(item.serverCount);
  const enabledServerCount = numberValue(item.enabledServerCount);
  const readyServerCount = numberValue(item.readyServerCount);
  const degradedServerCount = numberValue(item.degradedServerCount);
  const toolCount = numberValue(item.toolCount);
  const enabledToolCount = numberValue(item.enabledToolCount);
  const statusLabelText = mcpStatusLabel(status);
  const serverBase = enabledServerCount || serverCount;
  const summary = `${statusLabelText} · 服务 ${readyServerCount}/${serverBase} · 工具 ${enabledToolCount}/${toolCount}`;
  return {
    status,
    statusLabel: statusLabelText,
    summary,
    tone: status.toLowerCase() === "ready" ? "good" : "warn",
    serverCount,
    enabledServerCount,
    readyServerCount,
    degradedServerCount,
    toolCount,
    enabledToolCount,
    message: text(item.message) || text(item.error) || undefined
  };
}

function mcpGapsFromHealth(health: AgentPlatformReadinessMcpHealth | null): string[] {
  if (!health) {
    return [];
  }
  const status = health.status.toLowerCase();
  return unique([
    status === "missing" ? "MCP 管理器未加载" : "",
    health.serverCount === 0 ? "还没有注册 MCP 服务" : "",
    health.serverCount > 0 && health.enabledServerCount === 0 ? "没有启用 MCP 服务" : "",
    health.enabledServerCount > 0 && health.enabledToolCount === 0 ? "当前没有可供 Agent 使用的 MCP 工具" : "",
    status && status !== "ready" && status !== "missing" ? `MCP 服务健康状态为 ${health.status}` : ""
  ]);
}

function compact(values: string[], limit = 3): string {
  const visible = values.slice(0, Math.max(1, limit));
  const more = Math.max(0, values.length - visible.length);
  return `${visible.join("、")}${more ? ` 等 ${more} 项` : ""}`;
}

function readinessFromBackend(readiness: UnknownMap, mcpAdminHealth?: unknown): AgentPlatformReadiness {
  const status = normalizeStatus(readiness.status, "partial");
  return {
    status,
    statusLabel: text(readiness.statusLabel) || statusLabel(status),
    title: text(readiness.title) || "Agent + 拼团交易系统就绪度",
    metrics: asArray(readiness.metrics).map(normalizeMetric).filter((item) => item.key && item.label),
    gaps: stringList(readiness.gaps),
    actions: stringList(readiness.actions),
    missingTools: stringList(readiness.missingTools),
    mcpGaps: stringList(readiness.mcpGaps),
    tradeGuardrails: stringList(readiness.tradeGuardrails),
    mcpHealth: normalizeMcpHealth(readiness.mcpHealth) || normalizeMcpHealth(mcpAdminHealth)
  };
}

function capabilityByKey(capabilityMatrix: unknown[], key: string): UnknownMap {
  return capabilityMatrix
    .map(asObject)
    .find((item) => text(item.key) === key) || {};
}

function workspaceEntryReady(profile: UnknownMap): boolean {
  const id = text(profile.id);
  return Boolean(text(profile.runEndpoint)) || (id === "agent" && text(profile.path) === "/");
}

function deriveReadiness(capabilities: UnknownMap): AgentPlatformReadiness {
  const executionModes = asArray(capabilities.agentExecutionModes).map(asObject);
  const toolReadiness = asArray(capabilities.toolRuntimeReadiness).map(asObject);
  const workspaceProfiles = asArray(capabilities.workspaceProfiles).map(asObject);
  const capabilityMatrix = asArray(capabilities.capabilityMatrix);

  const coveredFamilies = unique(executionModes.map((mode) => text(mode.family)).filter((family) => REQUIRED_FAMILIES.includes(family)));
  const missingFamilies = REQUIRED_FAMILIES.filter((family) => !coveredFamilies.includes(family));
  const replanCount = executionModes.filter((mode) => bool(mode.replanEnabled) || stringList(mode.replanEvidence).length > 0).length;

  const missingTools = toolReadiness.length === 0
    ? REQUIRED_RUNTIME_TOOLS
    : unique(toolReadiness
      .filter((item) => text(item.status) !== "ready")
      .map((item) => text(item.name))
      .filter(Boolean));
  const readyToolCount = toolReadiness.length === 0
    ? 0
    : toolReadiness.filter((item) => text(item.status) === "ready").length;

  const coveredWorkspaces = unique(workspaceProfiles
    .filter(workspaceEntryReady)
    .map((profile) => text(profile.id))
    .filter((id) => REQUIRED_WORKSPACES.includes(id)));
  const missingWorkspaces = REQUIRED_WORKSPACES.filter((workspace) => !coveredWorkspaces.includes(workspace));

  const mcpCapability = capabilityByKey(capabilityMatrix, "mcp");
  const hasMcpCapability = Object.keys(mcpCapability).length > 0;
  const mcpHealth = normalizeMcpHealth(capabilities.mcpAdminHealth);
  const mcpGaps = unique([
    ...stringList(mcpCapability.gaps),
    ...(mcpHealth ? mcpGapsFromHealth(mcpHealth) : hasMcpCapability ? [] : ["MCP 管理能力未上报"])
  ]);
  const tradeCapability = capabilityByKey(capabilityMatrix, "trade-quota");
  const settlementRuleCount = asArray(tradeCapability.settlementRules).length;
  const blockedSettlementRuleCount = asArray(tradeCapability.settlementRules)
    .map(asObject)
    .filter((rule) => !bool(rule.quotaGrantAllowed))
    .length;
  const tradeGuardrails = stringList(tradeCapability.guardrails);

  const gaps = unique([
    missingFamilies.length ? `缺少执行族：${missingFamilies.join("、")}` : "",
    replanCount === 0 ? "缺少动态重规划证据" : "",
    missingTools.length ? `工具运行时未全部就绪：${compact(missingTools, 4)}` : "",
    missingWorkspaces.length ? `工作区入口未完整：${missingWorkspaces.join("、")}` : "",
    ...mcpGaps,
    settlementRuleCount === 0 ? "缺少拼团额度发放规则" : ""
  ]);
  const ready = gaps.length === 0 && blockedSettlementRuleCount > 0;
  const status = executionModes.length === 0 ? "missing" : ready ? "ready" : "partial";
  const actions = unique([
    missingTools.length ? `启动或配置工具运行时：${compact(missingTools)}` : "",
    mcpGaps.length ? "注册、发现并缓存 MCP 工具" : "",
    missingFamilies.length || replanCount === 0 ? "补齐多智能体执行模式与重规划证据" : "",
    settlementRuleCount === 0 ? "补齐拼团额度发放规则" : "",
    ready ? "Agent 与拼团交易闭环已具备完整演示面" : ""
  ]);

  return {
    status,
    statusLabel: statusLabel(status),
    title: "Agent + 拼团交易系统就绪度",
    metrics: [
      metric("families", "执行族", `${coveredFamilies.length}/${REQUIRED_FAMILIES.length}`, missingFamilies.length ? "warn" : "good"),
      metric("replan", "重规划", String(replanCount), replanCount > 0 ? "good" : "warn"),
      metric("tools", "工具", `${readyToolCount}/${REQUIRED_RUNTIME_TOOLS.length}`, missingTools.length ? "warn" : "good"),
      metric("workspaces", "工作区", `${coveredWorkspaces.length}/${REQUIRED_WORKSPACES.length}`, missingWorkspaces.length ? "warn" : "good"),
      metric("tradeRules", "交易规则", String(settlementRuleCount), settlementRuleCount > 0 ? "good" : "warn")
    ],
    gaps,
    actions,
    missingTools,
    mcpGaps,
    tradeGuardrails,
    mcpHealth
  };
}

export function buildAgentPlatformReadiness(value: unknown): AgentPlatformReadiness | null {
  const payload = asObject(value);
  if (Object.keys(payload).length === 0) {
    return null;
  }
  const nested = asObject(payload.agentPlatformReadiness);
  if (Object.keys(nested).length > 0) {
    return readinessFromBackend(nested, payload.mcpAdminHealth);
  }
  if (text(payload.status) && asArray(payload.metrics).length > 0) {
    return readinessFromBackend(payload, payload.mcpAdminHealth);
  }
  return deriveReadiness(payload);
}
