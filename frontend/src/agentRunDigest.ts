import { isTimelineAttentionItem, timelineItemStatus, type TimelineItem } from "./agentTimeline";

type UnknownMap = Record<string, unknown>;

export type AgentRunDigestStatus = "idle" | "running" | "completed" | "attention";

export type AgentRunDigestMetric = {
  key: string;
  label: string;
  value: string;
  tone?: "normal" | "good" | "warn";
};

export type AgentRunDigest = {
  visible: boolean;
  status: AgentRunDigestStatus;
  statusLabel: string;
  metrics: AgentRunDigestMetric[];
  highlights: string[];
};

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

function numberValue(value: unknown): number {
  const parsed = Number(value ?? 0);
  return Number.isFinite(parsed) ? parsed : 0;
}

function plural(count: number, unit: string): string {
  return `${count} ${unit}`;
}

function uniqueToolNames(timeline: TimelineItem[]): string[] {
  const seen = new Set<string>();
  const result: string[] = [];
  for (const item of timeline) {
    if (item.type !== "tool") continue;
    const name = text(item.toolName);
    if (!name || seen.has(name)) continue;
    seen.add(name);
    result.push(name);
  }
  return result;
}

function latestPlanTitle(timeline: TimelineItem[]): string {
  const plans = timeline.filter((item) => item.type === "plan");
  const last = plans[plans.length - 1];
  return text(last?.title);
}

function replanCount(timeline: TimelineItem[]): number {
  const planReplans = timeline.filter((item) => item.type === "plan" && text(item.changeType).toLowerCase() === "replan").length;
  const flowReplans = timeline.filter((item) => item.type === "flow" && timelineItemStatus(item) === "replanned").length;
  return Math.max(planReplans, flowReplans);
}

function latestReplanReason(timeline: TimelineItem[]): string {
  const replanned = [...timeline].reverse().find((item) => {
    if (item.type === "plan" && text(item.changeType).toLowerCase() === "replan") {
      return text(item.replanReason);
    }
    if (item.type === "flow" && timelineItemStatus(item) === "replanned") {
      return text(item.message);
    }
    return false;
  });
  return text(replanned?.replanReason) || text(replanned?.message);
}

function latestTimelineDetail(timeline: TimelineItem[]): string {
  const last = [...timeline].reverse().find((item) => {
    if (item.type === "thinking") return false;
    return text(item.detail) || text(item.message) || text(item.content) || text(item.title);
  });
  return text(last?.detail) || text(last?.message) || text(last?.content) || text(last?.title);
}

function artifactTitle(value: unknown): string {
  const item = asObject(value);
  return text(item.title) || text(item.fileName) || text(item.name);
}

export function buildAgentRunDigest(message: unknown): AgentRunDigest {
  const msg = asObject(message);
  const timeline = asArray(msg.timeline) as TimelineItem[];
  const artifacts = asArray(msg.artifacts);
  const references = asArray(msg.reference);
  const resultPanels = asArray(msg.resultPanels);
  const planCount = timeline.filter((item) => item.type === "plan").length;
  const flowCount = timeline.filter((item) => item.type === "flow").length;
  const replans = replanCount(timeline);
  const toolItems = timeline.filter((item) => item.type === "tool");
  const failedTools = toolItems.filter(isTimelineAttentionItem).length;
  const runningTools = toolItems.filter((item) => timelineItemStatus(item) === "running").length;
  const completedTools = toolItems.filter((item) => timelineItemStatus(item) === "completed").length;
  const llmItems = timeline.filter((item) => item.type === "llm");
  const totalTokens = llmItems.reduce((sum, item) => sum + numberValue(item.tokens), 0);
  const hasAttention = failedTools > 0 || timeline.some(isTimelineAttentionItem);
  const isRunning = runningTools > 0 || timeline.some((item) => timelineItemStatus(item) === "running");
  const visible = timeline.length > 0 || artifacts.length > 0 || references.length > 0 || resultPanels.length > 0;
  const status: AgentRunDigestStatus = !visible
    ? "idle"
    : hasAttention
      ? "attention"
      : isRunning
        ? "running"
        : "completed";

  const metrics: AgentRunDigestMetric[] = [];
  if (planCount > 0) {
    metrics.push({ key: "plans", label: "计划", value: plural(planCount, "版") });
  }
  if (replans > 0) {
    metrics.push({ key: "replans", label: "重规划", value: plural(replans, "次"), tone: "warn" });
  }
  if (flowCount > 0) {
    metrics.push({ key: "flows", label: "推进", value: plural(flowCount, "次") });
  }
  if (toolItems.length > 0) {
    metrics.push({
      key: "tools",
      label: "工具",
      value: failedTools > 0 ? `${completedTools} 成功 / ${failedTools} 异常` : plural(toolItems.length, "次"),
      tone: failedTools > 0 ? "warn" : "good"
    });
  }
  if (artifacts.length > 0) {
    metrics.push({ key: "artifacts", label: "产物", value: plural(artifacts.length, "个"), tone: "good" });
  }
  if (references.length > 0) {
    metrics.push({ key: "references", label: "来源", value: plural(references.length, "条") });
  }
  if (resultPanels.length > 0) {
    metrics.push({ key: "panels", label: "结果", value: plural(resultPanels.length, "组") });
  }
  if (totalTokens > 0) {
    metrics.push({ key: "tokens", label: "模型", value: `${totalTokens} tokens` });
  }

  const toolNames = uniqueToolNames(timeline);
  const replanReason = latestReplanReason(timeline);
  const highlights = [
    latestPlanTitle(timeline) ? `计划：${latestPlanTitle(timeline)}` : "",
    replanReason ? `重规划：${replanReason}` : "",
    toolNames.length ? `工具：${toolNames.slice(0, 4).join("、")}` : "",
    artifacts.length ? `产物：${artifactTitle(artifacts[0]) || plural(artifacts.length, "个文件")}` : "",
    latestTimelineDetail(timeline) ? `最近：${latestTimelineDetail(timeline)}` : ""
  ].filter(Boolean).slice(0, 4);

  return {
    visible,
    status,
    statusLabel: status === "attention" ? "需关注" : status === "running" ? "执行中" : status === "completed" ? "已生成" : "未开始",
    metrics,
    highlights
  };
}
