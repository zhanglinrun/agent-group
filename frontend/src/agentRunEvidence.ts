type UnknownMap = Record<string, unknown>;

export type AgentRunEvidenceMetric = {
  key: string;
  label: string;
  value: string;
  tone?: "normal" | "good" | "warn";
};

export type AgentRunEvidenceSummary = {
  visible: boolean;
  metrics: AgentRunEvidenceMetric[];
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

function evidenceFrom(detail: unknown): UnknownMap {
  const data = asObject(detail);
  const nested = asObject(data.evidence);
  return Object.keys(nested).length ? nested : data;
}

export function buildAgentRunEvidenceSummary(detail: unknown): AgentRunEvidenceSummary {
  const evidence = evidenceFrom(detail);
  if (!Object.keys(evidence).length) {
    return { visible: false, metrics: [], highlights: [] };
  }

  const mode = asObject(evidence.mode);
  const plan = asObject(evidence.plan);
  const failedTools = asArray(evidence.failedTools).map(asObject);
  const replanReasons = asArray(evidence.replanReasons).map(text).filter(Boolean);
  const toolCallCount = numberValue(evidence.toolCallCount);
  const failedToolCount = numberValue(evidence.failedToolCount);
  const replanCount = numberValue(evidence.replanCount);
  const artifactCount = numberValue(evidence.artifactCount);
  const llmCallCount = numberValue(evidence.llmCallCount);
  const quotaConsumed = numberValue(evidence.quotaConsumed);
  const toolSuccessRate = numberValue(evidence.toolSuccessRate);
  const planSteps = asArray(plan.steps);

  const metrics: AgentRunEvidenceMetric[] = [];
  if (text(mode.executionMode)) {
    metrics.push({ key: "mode", label: "模式", value: text(mode.executionMode), tone: "good" });
  }
  if (text(plan.title) || planSteps.length) {
    metrics.push({
      key: "plan",
      label: "计划",
      value: `${numberValue(plan.revisionCount) || 1} 版 / ${planSteps.length} 步`
    });
  }
  if (toolCallCount > 0) {
    metrics.push({
      key: "tools",
      label: "工具",
      value: failedToolCount > 0 ? `${toolCallCount - failedToolCount} 成功 / ${failedToolCount} 异常` : `${toolCallCount} 次`,
      tone: failedToolCount > 0 ? "warn" : "good"
    });
  }
  if (replanCount > 0) {
    metrics.push({ key: "replan", label: "重规划", value: `${replanCount} 次`, tone: "warn" });
  }
  if (llmCallCount > 0) {
    metrics.push({ key: "llm", label: "模型", value: `${llmCallCount} 次` });
  }
  if (artifactCount > 0) {
    metrics.push({ key: "artifacts", label: "产物", value: `${artifactCount} 个`, tone: "good" });
  }
  if (quotaConsumed > 0) {
    metrics.push({ key: "quota", label: "额度", value: `${quotaConsumed}` });
  }
  if (toolSuccessRate > 0 && toolCallCount > 0) {
    metrics.push({ key: "successRate", label: "工具成功率", value: `${Math.round(toolSuccessRate * 100)}%` });
  }
  if (text(evidence.diagnosisLevel)) {
    metrics.push({ key: "diagnosis", label: "诊断", value: text(evidence.diagnosisLevel), tone: failedToolCount > 0 ? "warn" : "normal" });
  }

  const firstFailure = failedTools[0] || {};
  const highlights = [
    text(mode.reason) ? `模式：${text(mode.reason)}` : "",
    text(plan.title) ? `计划：${text(plan.title)}` : "",
    replanReasons[0] ? `重规划：${replanReasons[0]}` : "",
    text(firstFailure.toolName) ? `失败工具：${text(firstFailure.toolName)}${text(firstFailure.errorMessage) ? `，${text(firstFailure.errorMessage)}` : ""}` : "",
    text(evidence.diagnosisSummary) ? `诊断：${text(evidence.diagnosisSummary)}` : ""
  ].filter(Boolean).slice(0, 4);

  return {
    visible: metrics.length > 0 || highlights.length > 0,
    metrics,
    highlights
  };
}
