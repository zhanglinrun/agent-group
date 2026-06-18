import { TOOL_LABELS } from "./workspaces";

export function formatTradeNumber(value) {
  return Number(value || 0).toFixed(2);
}

export function tradeOrderAmount(order = {}) {
  return formatTradeNumber(order.payAmount || order.totalAmount || order.amount || order.lockAmount);
}

export function formatPanelValue(value) {
  if (value === null || value === undefined || value === "") return "-";
  if (typeof value === "object") return JSON.stringify(value);
  return String(value);
}

export function formatFileSize(size = 0) {
  if (!size) return "-";
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}

export function artifactSourceLabel(file = {}) {
  const source = file.toolName || file.sourceName || file.toolInvocationId || file.invocationId || file.toolCallId;
  if (!source) return "";
  return TOOL_LABELS[source] || String(source);
}

export function artifactMetaLabel(file = {}) {
  const typeLabel = file.fileSize ? formatFileSize(file.fileSize) : file.type || file.contentType || "文件";
  const sourceLabel = artifactSourceLabel(file);
  return sourceLabel ? `${typeLabel} · 来源 ${sourceLabel}` : typeLabel;
}

export function hostFromUrl(url = "") {
  const value = String(url || "").trim();
  if (!value) return "";
  try {
    return new URL(value.startsWith("http") ? value : `https://${value}`).hostname.replace(/^www\./, "");
  } catch {
    return value.replace(/^https?:\/\//, "").split("/")[0] || value;
  }
}

export function numericValue(value) {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "string" && value.trim() !== "") {
    const parsed = Number(value.replace(/,/g, ""));
    return Number.isFinite(parsed) ? parsed : null;
  }
  return null;
}

export function buildDataChartPreview(panel = {}) {
  const rows = Array.isArray(panel.rows) ? panel.rows : [];
  const columns = Array.isArray(panel.columns) ? panel.columns : [];
  if (!rows.length || !columns.length) return null;
  const measure = columns.find((column) => rows.some((row) => numericValue(row?.[column]) !== null));
  if (!measure) return null;
  const dimension = columns.find((column) => column !== measure && rows.some((row) => numericValue(row?.[column]) === null))
    || columns.find((column) => column !== measure)
    || measure;
  const points = rows
    .slice(0, 6)
    .map((row, index) => ({
      label: formatPanelValue(row?.[dimension] ?? `#${index + 1}`),
      value: numericValue(row?.[measure]) ?? 0
    }));
  const maxValue = Math.max(...points.map((item) => Math.abs(item.value)), 0);
  if (!maxValue) return null;
  return { dimension, measure, points, maxValue };
}

function numberFrom(value, fallback = 0) {
  const parsed = Number(value ?? fallback);
  return Number.isFinite(parsed) ? parsed : fallback;
}

export function formatReasoningDuration(ms) {
  const value = numberFrom(ms, 0);
  if (value <= 0) return "";
  if (value < 1000) return `${Math.max(1, Math.round(value))} ms`;
  const seconds = value / 1000;
  if (seconds < 10) return `${seconds.toFixed(1).replace(/\.0$/, "")} 秒`;
  if (seconds < 60) return `${Math.round(seconds)} 秒`;
  const minutes = Math.floor(seconds / 60);
  const rest = Math.round(seconds % 60);
  return rest > 0 ? `${minutes} 分 ${rest} 秒` : `${minutes} 分`;
}

export function reasoningElapsedMs(timeline = []) {
  const diagnosis = [...timeline].reverse().find((item) => item?.type === "diagnosis" && numberFrom(item?.metrics?.elapsedMs, 0) > 0);
  if (diagnosis) return numberFrom(diagnosis.metrics.elapsedMs, 0);
  return timeline.reduce((sum, item) => sum + numberFrom(item?.latencyMillis, 0), 0);
}

export function reasoningToolCount(timeline = []) {
  const seen = new Set();
  let anonymous = 0;
  timeline.forEach((item) => {
    if (item?.type !== "tool") return;
    const key = String(item.invocationId || item.toolCallId || "").trim();
    if (key) {
      seen.add(key);
      return;
    }
    if (String(item.status || "").toLowerCase() !== "running") {
      anonymous += 1;
    }
  });
  return seen.size || anonymous || timeline.filter((item) => item?.type === "tool").length;
}

export function assistantReasoningMeta(message = {}, plannerHistory = []) {
  const timeline = message.timeline || [];
  const pieces = [];
  const elapsed = formatReasoningDuration(reasoningElapsedMs(timeline));
  const tools = reasoningToolCount(timeline);
  if (elapsed) pieces.push(`用时 ${elapsed}`);
  if (tools > 0) pieces.push(`${tools} 次工具`);
  if (plannerHistory.length > 0) pieces.push(`${plannerHistory.length} 版计划`);
  if ((message.artifacts || []).length > 0) pieces.push(`${message.artifacts.length} 个产物`);
  return pieces.slice(0, 3).join(" · ");
}

export function normalizeRecommendItems(value) {
  let raw = value;
  if (typeof raw === "string") {
    try {
      raw = JSON.parse(raw);
    } catch {
      raw = [raw];
    }
  }
  if (raw && !Array.isArray(raw)) {
    raw = raw.items || raw.questions || raw.recommends || [raw];
  }
  return (Array.isArray(raw) ? raw : [])
    .map((item) => {
      if (typeof item === "string") return item;
      if (!item || typeof item !== "object") return "";
      return item.question || item.content || item.title || item.text || item.name || "";
    })
    .map((item) => String(item || "").trim())
    .filter(Boolean)
    .slice(0, 6);
}
