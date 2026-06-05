import { normalizeFileUrlForBrowser } from "./fileUrl";

export type UiArtifact = {
  id: string;
  title: string;
  type: string;
  fileName: string;
  fileSize: number;
  content: string;
  downloadUrl: string;
  previewUrl?: string;
  contentType?: string;
  toolName?: string;
  toolInvocationId?: string;
  toolCallId?: string;
};

export type UiResultPanel = {
  id: string;
  kind: "data" | "sql" | "schema" | "summary" | "search" | "web" | "file" | "audit" | "code" | "image" | "multimodal" | "quota";
  toolName: string;
  title: string;
  summary: string;
  content: string;
  url: string;
  metadata: UnknownMap;
  columns: string[];
  rows: UnknownMap[];
  numericStats: UnknownMap;
  missingValues: UnknownMap;
  candidates: UnknownMap[];
  matches: UnknownMap[];
  sources: UiResultSource[];
  fileRefs: UiArtifact[];
  findings: UnknownMap[];
};

export type UiResultSource = {
  title: string;
  url: string;
  content: string;
  source: string;
  metaLabel: string;
};

type UnknownMap = Record<string, unknown>;

function asObject(value: unknown): UnknownMap {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as UnknownMap)
    : {};
}

function asArray(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

function text(value: unknown): string {
  return String(value ?? "").trim();
}

function numberValue(value: unknown): number {
  if (typeof value === "number" && Number.isFinite(value)) {
    return value;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function parseJsonObject(value: unknown): UnknownMap {
  if (value && typeof value === "object" && !Array.isArray(value)) {
    return value as UnknownMap;
  }
  if (typeof value !== "string" || !value.trim()) {
    return {};
  }
  try {
    return asObject(JSON.parse(value));
  } catch {
    return {};
  }
}

function firstObject(...values: unknown[]): UnknownMap {
  for (const value of values) {
    const object = parseJsonObject(value);
    if (Object.keys(object).length) {
      return object;
    }
  }
  return {};
}

function unwrapToolOutput(value: UnknownMap): UnknownMap {
  const result = parseJsonObject(value.result);
  if (!Object.keys(result).length) {
    return value;
  }
  return {
    ...value,
    ...result,
    toolName: firstText(value.toolName, result.toolName),
    action: firstText(value.action, result.action)
  };
}

function stringArray(value: unknown): string[] {
  return asArray(value)
    .map(text)
    .filter(Boolean);
}

function objectArray(value: unknown): UnknownMap[] {
  return asArray(value)
    .map(asObject)
    .filter((item) => Object.keys(item).length);
}

function artifactKey(value: UnknownMap): string {
  return firstText(value.artifactId, value.fileId, value.downloadUrl, value.url, value.fileName, value.title);
}

function firstText(...values: unknown[]): string {
  for (const value of values) {
    const result = text(value);
    if (result) {
      return result;
    }
  }
  return "";
}

function firstArray(...values: unknown[]): unknown[] {
  for (const value of values) {
    const items = asArray(value);
    if (items.length) {
      return items;
    }
  }
  return [];
}

function columnsFromRows(rows: UnknownMap[]): string[] {
  const result = new Set<string>();
  for (const row of rows) {
    Object.keys(row).forEach((key) => result.add(key));
  }
  return [...result];
}

function toUiResultSource(value: unknown): UiResultSource {
  const data = asObject(value);
  const url = firstText(data.url, data.link, data.href, data.sourceUrl);
  const title = firstText(data.title, data.name, data.pageTitle, data.documentTitle, url);
  const content = firstText(data.content, data.pageContent, data.snippet, data.summary, data.text, data.description);
  const source = firstText(data.source, data.site, data.provider, data.engine);
  const metaLabel = firstText(data.metaLabel, data.type, data.kind, source);
  return {
    title,
    url,
    content,
    source,
    metaLabel
  };
}

function sourceArray(...values: unknown[]): UiResultSource[] {
  return firstArray(...values)
    .map(toUiResultSource)
    .filter((item) => Boolean(item.title || item.url || item.content || item.source));
}

function resultPanelKind(toolName: string, panel: Pick<UiResultPanel, "rows" | "numericStats" | "candidates" | "matches" | "sources" | "fileRefs" | "url" | "content" | "findings" | "metadata">): UiResultPanel["kind"] {
  const normalized = toolName.toLowerCase();
  if (normalized.includes("quota_usage")) {
    return "quota";
  }
  if (normalized.includes("trade_audit") || panel.findings.length) {
    return "audit";
  }
  if (normalized.includes("code_interpreter") || normalized.includes("script_runner")) {
    return "code";
  }
  if (normalized.includes("image_generation")) {
    return "image";
  }
  if (normalized.includes("multimodal")) {
    return "multimodal";
  }
  if (normalized.includes("deep_search") || normalized.includes("deepsearch") || (normalized.includes("search") && panel.sources.length)) {
    return "search";
  }
  if (normalized.includes("web_fetch") || normalized.includes("webfetch") || (normalized.includes("web") && (panel.url || panel.content))) {
    return "web";
  }
  if (normalized.includes("file_tool") || (normalized.includes("report_tool") && panel.fileRefs.length) || normalized === "file" || normalized.endsWith("_file") || (normalized.includes("file") && panel.fileRefs.length)) {
    return "file";
  }
  if (normalized.includes("nl2sql") || panel.candidates.length) {
    return "sql";
  }
  if (normalized.includes("table_rag") || panel.matches.length) {
    return "schema";
  }
  if (normalized.includes("data") || panel.rows.length || Object.keys(panel.numericStats).length) {
    return "data";
  }
  return "summary";
}

function stablePanelId(data: UnknownMap, structuredOutput: UnknownMap, toolName: string): string {
  return text(data.invocationId)
    || text(structuredOutput.invocationId)
    || [
      toolName,
      text(structuredOutput.title),
      text(structuredOutput.summary),
      text(structuredOutput.content).slice(0, 80)
    ].filter(Boolean).join("_")
    || "tool_result";
}

function hasPanelContent(panel: UiResultPanel): boolean {
  return Boolean(
    panel.rows.length
      || Object.keys(panel.numericStats).length
      || Object.keys(panel.missingValues).length
      || panel.candidates.length
      || panel.matches.length
      || panel.sources.length
      || panel.fileRefs.length
      || panel.findings.length
      || panel.url
      || panel.content
      || panel.summary
      || Object.keys(panel.metadata).length
  );
}

export function toUiArtifact(value: unknown): UiArtifact {
  const data = asObject(value);
  const fileName = text(data.fileName) || text(data.filename) || text(data.name) || text(data.title) || "artifact";
  const downloadUrl = normalizeFileUrlForBrowser(text(data.downloadUrl) || text(data.ossUrl) || text(data.url));
  const previewUrl = normalizeFileUrlForBrowser(text(data.previewUrl) || text(data.domainUrl));
  const id = text(data.artifactId)
    || text(data.fileId)
    || `${fileName}_${downloadUrl || previewUrl}`;
  return {
    id,
    title: text(data.title) || fileName || "生成文件",
    type: text(data.artifactType) || text(data.type) || text(data.contentType) || "ARTIFACT",
    fileName,
    fileSize: numberValue(data.fileSize),
    content: text(data.content) || fileName,
    downloadUrl,
    previewUrl,
    contentType: text(data.contentType) || text(data.mimeType),
    toolName: firstText(data.toolName, data.sourceName),
    toolInvocationId: firstText(data.toolInvocationId, data.invocationId),
    toolCallId: text(data.toolCallId)
  };
}

export function toolResultArtifacts(event: unknown): UiArtifact[] {
  const payload = asObject(event);
  const data = asObject(payload.data ?? payload);
  const structuredOutput = unwrapToolOutput(firstObject(data.structuredOutput, data.resultJson, data));
  const toolName = firstText(data.toolName, structuredOutput.toolName);
  const toolInvocationId = firstText(data.toolInvocationId, data.invocationId, structuredOutput.toolInvocationId, structuredOutput.invocationId);
  const toolCallId = firstText(data.toolCallId, structuredOutput.toolCallId);
  const withSource = (value: unknown): UnknownMap => ({
    toolName,
    toolInvocationId,
    toolCallId,
    ...asObject(value)
  });
  const refs = [
    ...asArray(data.fileRefs).map(withSource),
    ...asArray(data.artifactRefs).map(withSource),
    ...asArray(structuredOutput.fileRefs).map(withSource),
    ...asArray(structuredOutput.artifactRefs).map(withSource)
  ];
  return refs.map(toUiArtifact).filter((artifact) => Boolean(artifact.fileName || artifact.downloadUrl));
}

export function toolResultPanels(event: unknown): UiResultPanel[] {
  const payload = asObject(event);
  const data = asObject(payload.data ?? payload);
  const structuredOutput = unwrapToolOutput(firstObject(data.structuredOutput, data.resultJson, data.resultSummary, data));
  const metadata = firstObject(structuredOutput.metadata, structuredOutput.data);
  const rows = objectArray(firstArray(
    metadata.sampleRows,
    metadata.rows,
    metadata.data,
    structuredOutput.sampleRows,
    structuredOutput.rows,
    structuredOutput.data
  ));
  const columns = stringArray(firstArray(metadata.columns, structuredOutput.columns));
  const candidates = objectArray(firstArray(metadata.candidates, structuredOutput.candidates));
  const matches = objectArray(firstArray(metadata.matches, structuredOutput.matches));
  const findings = objectArray(firstArray(metadata.findings, structuredOutput.findings));
  const sources = sourceArray(
    metadata.documents,
    metadata.sources,
    metadata.results,
    structuredOutput.documents,
    structuredOutput.sources,
    structuredOutput.results
  );
  const fileRefs = toolResultArtifacts(event);
  const toolName = text(data.toolName) || text(structuredOutput.toolName) || text(metadata.toolName);
  const url = firstText(metadata.finalUrl, metadata.url, structuredOutput.finalUrl, structuredOutput.url);
  const panel: UiResultPanel = {
    id: stablePanelId(data, structuredOutput, toolName),
    kind: "summary",
    toolName,
    title: text(structuredOutput.title) || text(data.toolName) || "工具结果",
    summary: text(structuredOutput.summary) || text(data.resultSummary),
    content: text(structuredOutput.content),
    url,
    metadata,
    columns: columns.length ? columns : columnsFromRows(rows),
    rows,
    numericStats: asObject(metadata.numericStats ?? structuredOutput.numericStats),
    missingValues: asObject(metadata.missingValues ?? structuredOutput.missingValues),
    candidates,
    matches,
    sources,
    fileRefs,
    findings
  };
  panel.kind = resultPanelKind(toolName, panel);
  return hasPanelContent(panel) && panel.kind !== "summary" ? [panel] : [];
}

export function mergeArtifacts(current: UiArtifact[] = [], incoming: UiArtifact[] = []): UiArtifact[] {
  const result = [...current];
  const seen = new Set(result.map((artifact) => artifact.id));
  for (const artifact of incoming) {
    if (!artifact || seen.has(artifact.id)) {
      continue;
    }
    seen.add(artifact.id);
    result.push(artifact);
  }
  return result;
}

export function mergeResultPanels(current: UiResultPanel[] = [], incoming: UiResultPanel[] = []): UiResultPanel[] {
  const result = [...current];
  const seen = new Set(result.map((panel) => panel.id));
  for (const panel of incoming) {
    if (!panel || seen.has(panel.id)) {
      continue;
    }
    seen.add(panel.id);
    result.push(panel);
  }
  return result;
}

export function replayEventsToArtifacts(replays: unknown[] = []): UiArtifact[] {
  const result: UiArtifact[] = [];
  for (const replay of replays) {
    const events = asArray(asObject(replay).events);
    for (const event of events) {
      const item = asObject(event);
      if (item.event === "artifact_delta") {
        result.push(toUiArtifact(item.data));
      }
      if (item.event === "tool_result") {
        result.push(...toolResultArtifacts(item));
      }
    }
  }
  return mergeArtifacts([], result);
}

export function replayEventsToResultPanels(replays: unknown[] = []): UiResultPanel[] {
  const result: UiResultPanel[] = [];
  for (const replay of replays) {
    const events = asArray(asObject(replay).events);
    for (const event of events) {
      const item = asObject(event);
      if (item.event === "tool_result") {
        result.push(...toolResultPanels(item));
      }
    }
  }
  return mergeResultPanels([], result);
}

export function runDetailToResultPanels(detail: unknown): UiResultPanel[] {
  const data = asObject(detail);
  const artifacts = objectArray(data.artifacts);
  const result: UiResultPanel[] = [];
  for (const value of asArray(data.toolInvocations)) {
    const invocation = asObject(value);
    if (!Object.keys(invocation).length) {
      continue;
    }
    const invocationId = text(invocation.invocationId);
    const explicitRefs = objectArray(firstArray(
      invocation.fileRefs,
      invocation.artifactRefs,
      invocation.artifacts
    ));
    const explicitKeys = new Set(explicitRefs.map(artifactKey).filter(Boolean));
    const matchedArtifacts = artifacts.filter((artifact) => {
      if (invocationId && text(artifact.toolInvocationId) === invocationId) {
        return true;
      }
      const key = artifactKey(artifact);
      return Boolean(key && explicitKeys.has(key));
    });
    result.push(...toolResultPanels({
      event: "tool_result",
      data: {
        invocationId,
        toolCallId: invocation.toolCallId,
        toolName: invocation.toolName,
        action: invocation.action,
        status: invocation.status,
        resultSummary: invocation.resultSummary,
        resultJson: invocation.resultJson,
        structuredOutput: invocation.structuredOutput,
        fileRefs: mergeArtifacts(
          explicitRefs.map(toUiArtifact),
          matchedArtifacts.map(toUiArtifact)
        )
      }
    }));
  }
  return mergeResultPanels([], result);
}
