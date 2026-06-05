import type { AgentMode, WorkspaceId } from "./workspaces";
import { workspaceAgentMode } from "./workspaces";
import { normalizeFileUrlForBrowser } from "./fileUrl";

export type WorkspaceAttachmentMode = "none" | "file" | "file-or-image";

export interface WorkspaceServiceProfile {
  id: WorkspaceId;
  taskType: AgentMode;
  title: string;
  summary: string;
  primaryTools: string[];
  attachmentMode: WorkspaceAttachmentMode;
  outputKinds: string[];
  runEndpoint?: string;
  historyEndpoint?: string;
}

export interface WorkspaceCapabilityStatus {
  key: string;
  label: string;
  active: boolean;
}

interface BackendWorkspaceProfile {
  id?: string;
  primaryTools?: string[];
  availableTools?: string[];
  missingTools?: string[];
  outputKinds?: string[];
  runEndpoint?: string;
  historyEndpoint?: string;
  status?: string;
}

export interface WorkspaceStreamDraft {
  taskType: string;
  question: string;
  fileId: string;
  imageUrl: string;
  imageName: string;
}

export interface WorkspaceDataRunPayload {
  sessionId: string;
  question: string;
  rows: unknown[];
  columns: string[];
  modelCodeList: string[];
  schemaInfo: unknown[];
  businessKnowledge: string;
}

export interface WorkspaceDataCatalogColumn {
  name?: string;
  type?: string;
  description?: string;
  metric?: boolean;
}

export interface WorkspaceDataCatalogModel {
  modelCode?: string;
  displayName?: string;
  tableName?: string;
  description?: string;
  columns?: WorkspaceDataCatalogColumn[];
  defaultRecallFields?: string[];
}

export interface WorkspaceDataCatalogDraft {
  modelCodeText: string;
  columnsText: string;
  schemaInfoJson: string;
  businessKnowledge: string;
}

export interface WorkspaceImageGeneratePayload {
  sessionId: string;
  prompt: string;
  mode: "generate" | "edit";
  size: string;
  batchCount: number;
  sourceFileIds: string[];
  sourceImageUrls: string[];
  maskImageUrls: string[];
}

export interface ToolCatalogGroup {
  key: string;
  count: number;
  tools: string[];
}

export interface ToolRuntimeReadiness {
  name: string;
  status: string;
  category: string;
  source: string;
  message: string;
  hint: string;
}

export interface CapabilityMatrixItem {
  key: string;
  label: string;
  status: string;
  summary: string;
  evidence: string[];
  gaps: string[];
}

export interface AgentExecutionModeItem {
  agentId: string;
  name: string;
  family: string;
  executionMode: string;
  summary: string;
}

export interface WorkspaceHistoryItem {
  id: string;
  workspaceId: string;
  sessionId: string;
  runId: string;
  title: string;
  summary: string;
  status: string;
  createdAt: string;
  durationMillis: number;
  artifactUrl: string;
  artifactName: string;
}

export interface KnowledgeBaseCatalogItem {
  id: string;
  name: string;
  version: string;
  documentType: string;
  documentCount: number;
  fragmentCount: number;
  enabledCount: number;
  failedCount: number;
  latestUpdate: string;
}

const DEFAULT_QUESTION = "请继续处理当前工作区任务";

export const WORKSPACE_SERVICE_PROFILES: Record<WorkspaceId, WorkspaceServiceProfile> = {
  agent: {
    id: "agent",
    taskType: "chat",
    title: "通用 Agent",
    summary: "统一承载聊天、文件问答、深度研究、PPT 和技能任务。",
    primaryTools: ["planning", "web_fetch", "deep_search", "report_tool"],
    attachmentMode: "file",
    outputKinds: ["answer", "reference", "artifact"],
    runEndpoint: "/api/v1/academic/stream",
    historyEndpoint: "/api/v1/academic/sessions"
  },
  image: {
    id: "image",
    taskType: "image",
    title: "图像生成工作区",
    summary: "面向封面图、架构图、流程插图和风格参考图生成。",
    primaryTools: ["image_generation", "multimodal_agent", "file_tool"],
    attachmentMode: "file-or-image",
    outputKinds: ["image", "prompt", "artifact"],
    runEndpoint: "/api/v1/academic/workspace/image/generate",
    historyEndpoint: "/api/v1/academic/workspace/image/history"
  },
  data: {
    id: "data",
    taskType: "data",
    title: "数据问答工作区",
    summary: "面向订单、额度、工具账本和交易一致性分析。",
    primaryTools: ["data_analysis", "table_rag", "nl2sql", "report_tool"],
    attachmentMode: "file",
    outputKinds: ["table", "sql", "chart", "report"],
    runEndpoint: "/api/v1/academic/workspace/data/run",
    historyEndpoint: "/api/v1/academic/workspace/data/history"
  },
  mrag: {
    id: "mrag",
    taskType: "mrag",
    title: "MRAG 知识问答工作区",
    summary: "结合文件、图片、表格、知识检索和网页资料做多模态问答。",
    primaryTools: ["multimodal_agent", "file_tool", "table_rag", "deep_search"],
    attachmentMode: "file-or-image",
    outputKinds: ["answer", "evidence", "file", "image"],
    runEndpoint: "/api/v1/academic/workspace/mrag/run",
    historyEndpoint: "/api/v1/academic/workspace/mrag/history"
  },
  trade: {
    id: "trade",
    taskType: "trade-audit",
    title: "拼团交易工作区",
    summary: "围绕额度购买、拼团成团、支付退款和额度流水做闭环核查。",
    primaryTools: ["planning", "data_analysis", "table_rag", "nl2sql", "report_tool"],
    attachmentMode: "none",
    outputKinds: ["order", "quota", "status", "audit-report"],
    runEndpoint: "/api/v1/academic/stream",
    historyEndpoint: "/api/v1/trade/order/my"
  }
};

export function workspaceServiceProfile(workspaceId: string): WorkspaceServiceProfile {
  return WORKSPACE_SERVICE_PROFILES[workspaceId as WorkspaceId] || WORKSPACE_SERVICE_PROFILES.agent;
}

function backendWorkspaceProfile(
  workspaceId: string,
  capabilities: Record<string, unknown> | null | undefined
): BackendWorkspaceProfile | null {
  const profiles = capabilities?.workspaceProfiles;
  if (!Array.isArray(profiles)) {
    return null;
  }
  return (profiles as BackendWorkspaceProfile[])
    .find((profile) => String(profile.id || "") === workspaceId) || null;
}

function stringList(value: unknown): string[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.map((item) => String(item || "")).filter(Boolean);
}

export function visibleToolCatalogGroups(
  capabilities: Record<string, unknown> | null | undefined,
  limit = 5
): ToolCatalogGroup[] {
  const groups = (capabilities?.toolCatalog as { categoryGroups?: unknown } | undefined)?.categoryGroups;
  if (!Array.isArray(groups)) {
    return [];
  }
  return groups
    .map((group) => {
      const record = group && typeof group === "object" ? group as Record<string, unknown> : {};
      return {
        key: String(record.key || ""),
        count: Number(record.count || 0),
        tools: stringList(record.tools)
      };
    })
    .filter((group) => group.key)
    .slice(0, Math.max(0, limit));
}

export function visibleToolRuntimeReadiness(
  capabilities: Record<string, unknown> | null | undefined,
  limit = 8
): ToolRuntimeReadiness[] {
  const readiness = capabilities?.toolRuntimeReadiness;
  if (!Array.isArray(readiness)) {
    return [];
  }
  return readiness
    .map((item) => {
      const record = item && typeof item === "object" ? item as Record<string, unknown> : {};
      return {
        name: String(record.name || ""),
        status: String(record.status || "missing"),
        category: String(record.category || ""),
        source: String(record.source || ""),
        message: String(record.message || ""),
        hint: String(record.hint || "")
      };
    })
    .filter((item) => item.name)
    .slice(0, Math.max(0, limit));
}

export function visibleCapabilityMatrix(
  capabilities: Record<string, unknown> | null | undefined,
  limit = 6
): CapabilityMatrixItem[] {
  const matrix = capabilities?.capabilityMatrix;
  if (!Array.isArray(matrix)) {
    return [];
  }
  return matrix
    .map((item) => {
      const record = item && typeof item === "object" ? item as Record<string, unknown> : {};
      return {
        key: String(record.key || record.label || ""),
        label: String(record.label || record.key || ""),
        status: String(record.status || "degraded"),
        summary: String(record.summary || ""),
        evidence: stringList(record.evidence).slice(0, 4),
        gaps: stringList(record.gaps).slice(0, 3)
      };
    })
    .filter((item) => item.key && item.label)
    .slice(0, Math.max(0, limit));
}

export function visibleAgentExecutionModes(
  capabilities: Record<string, unknown> | null | undefined,
  limit = 6
): AgentExecutionModeItem[] {
  const modes = capabilities?.agentExecutionModes;
  if (!Array.isArray(modes)) {
    return [];
  }
  return modes
    .map((item) => {
      const record = item && typeof item === "object" ? item as Record<string, unknown> : {};
      return {
        agentId: String(record.agentId || ""),
        name: String(record.name || record.agentId || ""),
        family: String(record.family || ""),
        executionMode: String(record.executionMode || ""),
        summary: String(record.summary || "")
      };
    })
    .filter((item) => item.agentId && item.name)
    .slice(0, Math.max(0, limit));
}

export function workspaceSupportsHistory(workspaceId: string): boolean {
  return workspaceId === "image" || workspaceId === "data" || workspaceId === "mrag";
}

function textValue(record: Record<string, unknown>, ...keys: string[]): string {
  for (const key of keys) {
    const value = record[key];
    if (value !== undefined && value !== null && String(value).trim()) {
      return String(value).trim();
    }
  }
  return "";
}

function numberValue(record: Record<string, unknown>, key: string): number {
  const value = Number(record[key] || 0);
  return Number.isFinite(value) ? value : 0;
}

function imageHistorySummary(record: Record<string, unknown>, imageCount: number): string {
  const mode = textValue(record, "mode");
  const size = textValue(record, "size");
  const sourceImageCount = numberValue(record, "sourceImageCount");
  const parts: string[] = [];
  if (mode === "edit") {
    parts.push("图生图");
  } else if (mode === "generate") {
    parts.push("文生图");
  }
  if (imageCount > 0) {
    parts.push(`${imageCount} 张`);
  }
  if (sourceImageCount > 0) {
    parts.push(`${sourceImageCount} 张参考图`);
  }
  if (size) {
    parts.push(size);
  }
  return parts.join(" · ");
}

export function knowledgeBaseCatalogKey(value: unknown): string {
  const record = value && typeof value === "object" ? value as Record<string, unknown> : {};
  const documentType = textValue(record, "documentType") || "默认知识库";
  const version = textValue(record, "knowledgeVersion") || "v1";
  return `${documentType}::${version}`;
}

export function buildKnowledgeBaseCatalog(value: unknown): KnowledgeBaseCatalogItem[] {
  if (!Array.isArray(value)) {
    return [];
  }
  const groups = new Map<string, KnowledgeBaseCatalogItem>();
  for (const item of value) {
    const record = item && typeof item === "object" ? item as Record<string, unknown> : {};
    const id = knowledgeBaseCatalogKey(record);
    const documentType = textValue(record, "documentType") || "默认知识库";
    const version = textValue(record, "knowledgeVersion") || "v1";
    const status = textValue(record, "documentStatus").toUpperCase();
    const latestUpdate = textValue(record, "updateTime", "createTime");
    const current = groups.get(id) || {
      id,
      name: documentType,
      version,
      documentType,
      documentCount: 0,
      fragmentCount: 0,
      enabledCount: 0,
      failedCount: 0,
      latestUpdate: ""
    };
    current.documentCount += 1;
    current.fragmentCount += numberValue(record, "fragmentCount");
    if (status === "ENABLED") current.enabledCount += 1;
    if (status.includes("FAILED")) current.failedCount += 1;
    current.latestUpdate = latestUpdate > current.latestUpdate ? latestUpdate : current.latestUpdate;
    groups.set(id, current);
  }
  return Array.from(groups.values()).sort((a, b) => b.latestUpdate.localeCompare(a.latestUpdate));
}

export function normalizeWorkspaceHistoryItems(
  workspaceId: string,
  value: unknown,
  limit = 8
): WorkspaceHistoryItem[] {
  if (!workspaceSupportsHistory(workspaceId) || !Array.isArray(value)) {
    return [];
  }
  return value
    .map((item, index) => {
      const record = item && typeof item === "object" ? item as Record<string, unknown> : {};
      const imageRefs = Array.isArray(record.images) ? record.images : [];
      const firstImage = imageRefs[0] && typeof imageRefs[0] === "object"
        ? imageRefs[0] as Record<string, unknown>
        : {};
      const sessionId = textValue(record, "sessionId");
      const runId = textValue(record, "runId");
      const artifactId = textValue(record, "artifactId") || textValue(firstImage, "artifactId");
      const artifactName = textValue(firstImage, "fileName", "title", "artifactType")
        || textValue(record, "fileName", "title", "artifactType");
      const title = textValue(record, "question", "prompt", "title", "fileName", "runId", "artifactId");
      const imageCount = numberValue(record, "batchCount") || imageRefs.length;
      const summary = (workspaceId === "image" ? imageHistorySummary(record, imageCount) : "")
        || textValue(record, "summary", "fileName", "artifactType", "status")
        || (imageCount > 0 ? `${imageCount} 张图片` : "");
      return {
        id: textValue(record, "id", "requestId", "artifactId", "runId") || `${workspaceId}-${sessionId || "local"}-${index}`,
        workspaceId,
        sessionId,
        runId,
        title: title || workspaceServiceProfile(workspaceId).title,
        summary,
        status: textValue(record, "status") || (artifactId ? "SUCCESS" : ""),
        createdAt: textValue(record, "finishedAt", "startedAt", "createTime", "createdAt", "updateTime"),
        durationMillis: numberValue(record, "durationMillis"),
        artifactUrl: normalizeFileUrlForBrowser(
          textValue(firstImage, "previewUrl", "downloadUrl")
          || textValue(record, "previewUrl", "downloadUrl")
        ),
        artifactName
      };
    })
    .filter((item) => item.sessionId || item.runId || item.artifactUrl || item.title)
    .slice(0, Math.max(0, limit));
}

export function workspaceDisplayProfile(
  workspaceId: string,
  capabilities?: Record<string, unknown> | null,
  fallbackProfile: WorkspaceServiceProfile = workspaceServiceProfile(workspaceId)
): WorkspaceServiceProfile {
  const backendProfile = backendWorkspaceProfile(workspaceId, capabilities);
  if (!backendProfile) {
    return fallbackProfile;
  }
  return {
    ...fallbackProfile,
    primaryTools: stringList(backendProfile.primaryTools).length
      ? stringList(backendProfile.primaryTools)
      : fallbackProfile.primaryTools,
    outputKinds: stringList(backendProfile.outputKinds).length
      ? stringList(backendProfile.outputKinds)
      : fallbackProfile.outputKinds,
    runEndpoint: String(backendProfile.runEndpoint || fallbackProfile.runEndpoint || ""),
    historyEndpoint: String(backendProfile.historyEndpoint || fallbackProfile.historyEndpoint || "")
  };
}

export function workspaceAcceptsFile(workspaceId: string, agentId: string = workspaceAgentMode(workspaceId)): boolean {
  const profile = workspaceServiceProfile(workspaceId);
  return profile.attachmentMode !== "none"
    || agentId === "file"
    || agentId === "skills"
    || agentId === "manual-skills";
}

export function workspaceCapabilityStatus(
  workspaceId: string,
  capabilities: Record<string, unknown> | null | undefined
): WorkspaceCapabilityStatus[] {
  const profile = workspaceDisplayProfile(workspaceId, capabilities);
  const backendProfile = backendWorkspaceProfile(workspaceId, capabilities);
  const toolNames = new Set(
    ((capabilities?.academicTools as Array<{ name?: string }> | undefined) || [])
      .map((tool) => String(tool.name || ""))
      .filter(Boolean)
  );
  const matchedTools = backendProfile
    ? stringList(backendProfile.availableTools)
    : profile.primaryTools.filter((toolName) => toolNames.has(toolName));
  const reactorToolEnabled = Boolean(capabilities?.reactorToolEnabled);
  const manualSkillCount = Number(capabilities?.manualSkillCount || 0);

  return [
    {
      key: "primary-tools",
      label: matchedTools.length
        ? `核心工具 ${matchedTools.length}/${profile.primaryTools.length}`
        : `核心工具 0/${profile.primaryTools.length}`,
      active: matchedTools.length > 0
    },
    {
      key: "reactor-tool",
      label: reactorToolEnabled ? "参考工具已连接" : "参考工具未连接",
      active: reactorToolEnabled
    },
    {
      key: "manual-skills",
      label: `技能 ${manualSkillCount}`,
      active: manualSkillCount > 0
    }
  ];
}

export function buildWorkspaceStreamDraft(input: {
  workspaceId: string;
  agentId?: AgentMode | string;
  question?: string;
  fileId?: string;
  imageUrl?: string;
  imageName?: string;
}): WorkspaceStreamDraft {
  const profile = workspaceServiceProfile(input.workspaceId);
  return {
    taskType: String(input.agentId || profile.taskType),
    question: String(input.question || "").trim() || DEFAULT_QUESTION,
    fileId: String(input.fileId || ""),
    imageUrl: String(input.imageUrl || ""),
    imageName: String(input.imageName || "")
  };
}

function splitTextList(value: string | undefined): string[] {
  return String(value || "")
    .split(/[\n,，;；]+/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function textOrArrayList(value: unknown): string[] {
  return Array.isArray(value) ? stringList(value) : splitTextList(String(value || ""));
}

function parseJsonArray(value: string | undefined, label: string): unknown[] {
  const text = String(value || "").trim();
  if (!text) return [];
  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    throw new Error(`${label} 必须是 JSON 数组`);
  }
  if (!Array.isArray(parsed)) {
    throw new Error(`${label} 必须是 JSON 数组`);
  }
  return parsed;
}

export function buildWorkspaceDataRunPayload(input: {
  sessionId: string;
  question: string;
  rowsJson?: string;
  columnsText?: string;
  modelCodeText?: string;
  schemaInfoJson?: string;
  businessKnowledge?: string;
}): WorkspaceDataRunPayload {
  return {
    sessionId: String(input.sessionId || ""),
    question: String(input.question || "").trim(),
    rows: parseJsonArray(input.rowsJson, "表格行"),
    columns: splitTextList(input.columnsText),
    modelCodeList: splitTextList(input.modelCodeText),
    schemaInfo: parseJsonArray(input.schemaInfoJson, "表结构"),
    businessKnowledge: String(input.businessKnowledge || "").trim()
  };
}

export function buildWorkspaceDataCatalogDraft(catalog: {
  defaultModelCodeList?: string[];
  models?: WorkspaceDataCatalogModel[];
  sampleQuestions?: string[];
} | null | undefined): WorkspaceDataCatalogDraft {
  const models = Array.isArray(catalog?.models) ? catalog.models : [];
  const modelCodes = stringList(catalog?.defaultModelCodeList).length
    ? stringList(catalog?.defaultModelCodeList)
    : models.map((model) => String(model.modelCode || model.tableName || "")).filter(Boolean);
  const columnNames = Array.from(new Set(
    models.flatMap((model) => Array.isArray(model.columns) ? model.columns : [])
      .map((column) => String(column.name || "").trim())
      .filter(Boolean)
  ));
  const schemaInfo = models.map((model) => ({
    modelCode: model.modelCode || model.tableName || "",
    tableName: model.tableName || model.modelCode || "",
    displayName: model.displayName || model.modelCode || "",
    description: model.description || "",
    columns: (Array.isArray(model.columns) ? model.columns : []).map((column) => ({
      name: column.name || "",
      type: column.type || "",
      description: column.description || "",
      metric: Boolean(column.metric)
    })).filter((column) => column.name)
  })).filter((model) => model.modelCode || model.tableName);

  return {
    modelCodeText: modelCodes.join(", "),
    columnsText: columnNames.join(", "),
    schemaInfoJson: schemaInfo.length ? JSON.stringify(schemaInfo, null, 2) : "",
    businessKnowledge: "拼团支付成功只代表名额已支付，必须等拼团成团或交易完成后才能发放额度。"
  };
}

function clampBatchCount(value: unknown): number {
  const numeric = Number(value || 1);
  if (!Number.isFinite(numeric)) return 1;
  return Math.max(1, Math.min(Math.floor(numeric), 4));
}

export function buildWorkspaceImageGeneratePayload(input: {
  sessionId: string;
  prompt: string;
  mode?: string;
  size?: string;
  batchCount?: number | string;
  sourceFileIds?: string[];
  sourceImageUrls?: string[];
  maskImageUrls?: string[] | string;
}): WorkspaceImageGeneratePayload {
  const sourceFileIds = stringList(input.sourceFileIds);
  const sourceImageUrls = stringList(input.sourceImageUrls);
  const maskImageUrls = textOrArrayList(input.maskImageUrls);
  const requestedMode = String(input.mode || "").trim() === "edit" ? "edit" : "generate";
  if (requestedMode === "edit" && sourceFileIds.length === 0 && sourceImageUrls.length === 0) {
    throw new Error("图生图需要先上传参考图");
  }
  return {
    sessionId: String(input.sessionId || ""),
    prompt: String(input.prompt || "").trim(),
    mode: requestedMode,
    size: String(input.size || "1024x1024").trim() || "1024x1024",
    batchCount: clampBatchCount(input.batchCount),
    sourceFileIds,
    sourceImageUrls,
    maskImageUrls
  };
}
