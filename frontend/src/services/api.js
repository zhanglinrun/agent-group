import { isAcademicTerminalEvent, parseAcademicSseBlock, splitAcademicSseBlocks } from "../academicSse";
import { normalizeFileUrlForBrowser } from "../fileUrl";

const ADMIN_AUTH_KEY = "agentGroupAdminAuth";
const USER_AUTH_KEY = "agentGroupUserAuth";
const MODEL_CONFIG_KEY = "agentGroupModelConfig";
let adminAuthMemory = null;

const DEFAULT_TEXT_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode";
const DEFAULT_IMAGE_BASE_URL = "https://api.openai.com";

const DEFAULT_MODEL_CONFIG = {
  enabled: false,
  baseUrl: DEFAULT_TEXT_BASE_URL,
  apiKey: "",
  model: "qwen3.7-plus",
  textBaseUrl: DEFAULT_TEXT_BASE_URL,
  textApiKey: "",
  textModel: "qwen3.7-plus",
  imageBaseUrl: DEFAULT_IMAGE_BASE_URL,
  imageApiKey: "",
  imageModel: "gpt-image-2",
  keyMasked: "",
  textKeyMasked: "",
  imageKeyMasked: ""
};

function preferredPayChannel(explicitChannel = "") {
  const configured = String(explicitChannel || import.meta.env?.VITE_PAYMENT_CHANNEL || "").trim();
  if (configured) return configured.toUpperCase();
  const host = typeof window !== "undefined" ? window.location?.hostname : "";
  if (host === "localhost" || host === "127.0.0.1" || host === "::1") {
    return "MOCK_PAY";
  }
  return "ALIPAY";
}

function volatileStorage() {
  return window.sessionStorage;
}

function readVolatileJson(key) {
  try {
    const raw = volatileStorage().getItem(key);
    localStorage.removeItem(key);
    return JSON.parse(raw || "null");
  } catch {
    return null;
  }
}

function writeVolatileJson(key, value) {
  volatileStorage().setItem(key, JSON.stringify(value));
  localStorage.removeItem(key);
}

function clearVolatile(key) {
  volatileStorage().removeItem(key);
  localStorage.removeItem(key);
}

function normalizeModelBaseUrl(value, fallback = DEFAULT_TEXT_BASE_URL) {
  let text = String(value || fallback).trim();
  if (!text) return "";
  if (/^ttps:\/\//i.test(text)) {
    text = `h${text}`;
  }
  if (!/^[a-z][a-z0-9+.-]*:\/\//i.test(text)) {
    text = `https://${text.replace(/^\/+/, "")}`;
  }
  return text.replace(/\/+$/, "");
}

export function normalizeApiMessage(message, fallback = "操作失败") {
  const text = String(message || "").trim();
  if (!text) return fallback;
  const lower = text.toLowerCase();
  if (lower.includes("data_inspection_failed") || lower.includes("inappropriate content")) {
    return "本次请求被模型服务内容安全检查拦截。可以删减敏感表达、开启新对话减少历史上下文，或关闭联网搜索后重试。";
  }
  if ((lower.includes("401 unauthorized") || lower.includes("unauthorized"))
    && (lower.includes("dashscope") || lower.includes("chat/completions") || lower.includes("openai") || lower.includes("api key"))) {
    if (!lower.includes("dashscope")) {
      return "自定义模型接口认证失败，请检查模型配置里的 API 地址、密钥和模型名";
    }
    return "模型密钥无效或权限不足，请检查 .env 中的 DashScope API Key，或在模型配置里填写可用的 API 地址和密钥";
  }
  if (lower.includes("api key") && (lower.includes("invalid") || lower.includes("not configured"))) {
    if (!lower.includes("dashscope")) {
      return "自定义模型配置不可用，请检查模型配置里的 API 地址、密钥和模型名";
    }
    return "模型密钥未配置或不可用，请检查 .env 中的 DashScope API Key，或在模型配置里填写可用的 API 地址和密钥";
  }
  if (lower.includes("alipay config incomplete")) {
    return "支付宝沙箱配置不完整；本地开发可使用模拟支付，真实支付需要配置支付宝 appId、应用私钥和支付宝公钥";
  }
  if ((lower.includes("duplicate entry") || lower.includes("sqlintegrityconstraintviolationexception"))
    && (lower.includes("uk_user_biz_flow") || lower.includes("user_quota_flow"))) {
    return "本次请求已处理，请勿重复提交或刷新后重试";
  }
  if (lower.includes("invalid uri scheme")
    || lower.includes("unsupported uri scheme")
    || lower.includes("scheme ttps")
    || lower.includes("uri scheme ttps")) {
    return "API 地址格式不正确，请确认以 https:// 开头";
  }
  if (lower.includes("自定义 api 地址仅支持 https")) return "自定义 API 地址仅支持 HTTPS";
  if (lower.includes("自定义 api 地址不能指向本地或内网地址")) return "自定义 API 地址不能指向本地或内网地址";
  if (lower.includes("user group buy take limit reached")) return "你已达到该拼团活动的参与次数上限";
  if (lower.includes("group team slot is full") || lower.includes("group team quota is full")) return "拼团队伍名额已满";
  if (lower.includes("group team not found") || lower.includes("group lock not found") || lower.includes("group order lock not found")) return "拼团队伍不存在或已失效";
  if (lower.includes("idempotent key conflict")) return "请勿重复提交不同的拼团订单";
  if (lower.includes("request activity does not match market trial activity")) return "当前拼团活动已变化，请刷新后重试";
  if (lower.includes("user cannot join this group activity")) return "当前账号暂不能参加这个拼团活动";
  if (lower.includes("group buy market is downgraded")) return "拼团活动暂时不可用";
  if (lower.includes("user is outside market cut range")) return "当前账号暂不在活动范围内";
  if (lower.includes("source and channel are blocked")) return "当前渠道暂不能参加活动";
  if (lower.includes("product not found")) return "额度包不存在或已下架";
  if (lower.includes("pay order not found")) return "支付单不存在";
  if (lower.includes("refund order not found")) return "退款单不存在";
  if (lower.includes("order not found or user mismatch")) return "订单不存在或不属于当前用户";
  if (lower.includes("order not found")) return "订单不存在";
  if (lower.includes("cannot be blank") || lower.includes("cannot be empty") || lower.includes("is required")) return "请补全必要信息";
  if (lower.includes("request cannot be null")) return "请求参数不能为空";
  if (lower.includes("group buy timeout unformed")) return "拼团超时未成团";
  if (lower.includes("too many requests")) return "操作过于频繁，请稍后再试";
  if (lower.includes("human approval required")) return "该操作需要人工确认";
  if (lower.includes("human approval expired")) return "人工确认已过期";
  if (lower.includes("human approval user mismatch")) return "人工确认用户不匹配";
  if (lower.includes("human approval is not approved")) return "人工确认未通过";
  if (lower.includes("human approval action mismatch") || lower.includes("human approval biz mismatch")) return "人工确认信息不匹配";
  if (lower.includes("human approval not found")) return "人工确认记录不存在";
  return text;
}

export class ApiError extends Error {
  constructor(message, status, payload) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.payload = payload;
  }
}

export function getSessionId() {
  let sessionId = localStorage.getItem("agentGroupSessionId");
  if (!sessionId) {
    sessionId = `S${Date.now()}`;
    localStorage.setItem("agentGroupSessionId", sessionId);
  }
  return sessionId;
}

export function getAdminAuth() {
  clearVolatile(ADMIN_AUTH_KEY);
  return adminAuthMemory;
}

export function saveAdminAuth(username, password) {
  adminAuthMemory = { username, password };
  clearVolatile(ADMIN_AUTH_KEY);
}

export function clearAdminAuth() {
  adminAuthMemory = null;
  clearVolatile(ADMIN_AUTH_KEY);
}

export function getUserAuth() {
  return readVolatileJson(USER_AUTH_KEY);
}

export function saveUserAuth(auth) {
  writeVolatileJson(USER_AUTH_KEY, auth);
}

export function clearUserAuth() {
  clearVolatile(USER_AUTH_KEY);
}

function normalizeModelConfig(config = {}) {
  const textModel = String(config.textModel || config.model || DEFAULT_MODEL_CONFIG.textModel).trim();
  const imageModel = String(config.imageModel || DEFAULT_MODEL_CONFIG.imageModel).trim();
  const textBaseUrl = normalizeModelBaseUrl(config.textBaseUrl || config.baseUrl, DEFAULT_TEXT_BASE_URL);
  const imageBaseUrl = normalizeModelBaseUrl(config.imageBaseUrl, DEFAULT_IMAGE_BASE_URL);
  const textKeyMasked = String(config.textKeyMasked || config.keyMasked || "").trim();
  const imageKeyMasked = String(config.imageKeyMasked || "").trim();
  return {
    ...DEFAULT_MODEL_CONFIG,
    ...config,
    enabled: Boolean(config.enabled),
    baseUrl: textBaseUrl,
    apiKey: String(config.textApiKey || config.apiKey || "").trim(),
    model: textModel || DEFAULT_MODEL_CONFIG.textModel,
    textBaseUrl,
    textApiKey: String(config.textApiKey || config.apiKey || "").trim(),
    textModel: textModel || DEFAULT_MODEL_CONFIG.textModel,
    imageBaseUrl,
    imageApiKey: String(config.imageApiKey || "").trim(),
    imageModel: imageModel || DEFAULT_MODEL_CONFIG.imageModel,
    keyMasked: textKeyMasked,
    textKeyMasked,
    imageKeyMasked
  };
}

export function getModelConfig() {
  try {
    return normalizeModelConfig(readVolatileJson(MODEL_CONFIG_KEY) || {});
  } catch {
    return { ...DEFAULT_MODEL_CONFIG };
  }
}

function rememberModelConfig(config) {
  const normalized = normalizeModelConfig(config);
  writeVolatileJson(MODEL_CONFIG_KEY, { ...normalized, apiKey: "", textApiKey: "", imageApiKey: "" });
  return normalized;
}

export function modelConfigReady(config, scope = "text") {
  const normalized = normalizeModelConfig(config);
  if (!normalized.enabled) return true;
  const textReady = Boolean(normalized.textBaseUrl) && Boolean(normalized.textModel)
    && (Boolean(normalized.textApiKey) || Boolean(normalized.textKeyMasked));
  const imageReady = Boolean(normalized.imageBaseUrl) && Boolean(normalized.imageModel)
    && (Boolean(normalized.imageApiKey) || Boolean(normalized.imageKeyMasked));
  if (scope === "image") return imageReady;
  if (scope === "all") return textReady && imageReady;
  return textReady;
}

function modelConfigPayload(config) {
  return {};
}

function authHeader() {
  const auth = getAdminAuth();
  if (!auth?.username || !auth?.password) {
    return {};
  }
  return {
    Authorization: `Basic ${window.btoa(`${auth.username}:${auth.password}`)}`
  };
}

function userAuthHeader() {
  const auth = getUserAuth();
  if (!auth?.token) {
    return {};
  }
  return {
    Authorization: `Bearer ${auth.token}`
  };
}

async function parseResponse(response) {
  const contentType = response.headers.get("content-type") || "";
  const isJson = contentType.includes("application/json");
  const payload = isJson ? await response.json().catch(() => null) : await response.text().catch(() => "");

  if (!response.ok) {
    const fallback = response.status === 401
      ? "未登录或账号密码不正确"
      : response.status === 403
        ? "当前账号权限不足"
        : `请求失败：${response.status}`;
    throw new ApiError(normalizeApiMessage(payload?.info || payload?.message, fallback), response.status, payload);
  }
  return payload;
}

async function request(path, options = {}) {
  const { auth = false, userAuth = false, headers, ...rest } = options;
  const response = await fetch(path, {
    ...rest,
    headers: {
      ...(auth ? authHeader() : {}),
      ...(userAuth ? userAuthHeader() : {}),
      ...(headers || {})
    }
  });
  return parseResponse(response);
}

export async function login(username, password) {
  const res = await request("/api/v1/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password })
  });
  if (res.code === "0000" && res.data?.token) {
    saveUserAuth(res.data);
  }
  return res;
}

export async function register({ username, password, nickname, email }) {
  const res = await request("/api/v1/auth/register", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password, nickname, email })
  });
  if (res.code === "0000" && res.data?.token) {
    saveUserAuth(res.data);
  }
  return res;
}

export async function logout() {
  try {
    await request("/api/v1/auth/logout", { userAuth: true, method: "POST" });
  } finally {
    clearUserAuth();
  }
}

export async function getProfile() {
  return request("/api/v1/auth/profile", {
    userAuth: true,
    method: "GET"
  });
}

export async function getQuotaSummary(limit = 20) {
  return request(`/api/v1/quota/summary?limit=${encodeURIComponent(limit)}`, {
    userAuth: true,
    method: "GET"
  });
}

export async function getQuotaAccount() {
  return request("/api/v1/quota/account", {
    userAuth: true,
    method: "GET"
  });
}

export async function getUserModelConfig() {
  const res = await request("/api/v1/quota/model-config", {
    userAuth: true,
    method: "GET"
  });
  if (res.code === "0000") {
    rememberModelConfig(res.data || {});
  }
  return res;
}

export async function saveModelConfig(config) {
  const normalized = normalizeModelConfig(config);
  const res = await request("/api/v1/quota/model-config", {
    userAuth: true,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      enabled: normalized.enabled,
      baseUrl: normalized.textBaseUrl,
      apiKey: normalized.textApiKey,
      model: normalized.textModel,
      textBaseUrl: normalized.textBaseUrl,
      textApiKey: normalized.textApiKey,
      textModel: normalized.textModel,
      imageBaseUrl: normalized.imageBaseUrl,
      imageApiKey: normalized.imageApiKey,
      imageModel: normalized.imageModel
    })
  });
  if (res.code === "0000") {
    rememberModelConfig({ ...res.data, apiKey: "", textApiKey: "", imageApiKey: "" });
  }
  return res;
}

export async function uploadAcademicFile(file, sessionId = getSessionId()) {
  const formData = new FormData();
  formData.append("file", file);
  if (sessionId) formData.append("sessionId", sessionId);
  return request("/api/v1/academic/file/upload", {
    userAuth: true,
    method: "POST",
    body: formData
  });
}

export async function createAcademicProject(payload) {
  return request("/api/v1/academic/projects", {
    userAuth: true,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload || {})
  });
}

export async function queryAcademicProjects(limit = 20) {
  return request(`/api/v1/academic/projects?limit=${encodeURIComponent(limit)}`, {
    userAuth: true,
    method: "GET"
  });
}

export async function queryAcademicProject(projectId) {
  return request(`/api/v1/academic/projects/${encodeURIComponent(projectId)}`, {
    userAuth: true,
    method: "GET"
  });
}

export async function bindAcademicProjectFile(projectId, payload) {
  return request(`/api/v1/academic/projects/${encodeURIComponent(projectId)}/files`, {
    userAuth: true,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload || {})
  });
}

export async function proposeAcademicProjectPatch(projectId, payload) {
  return request(`/api/v1/academic/projects/${encodeURIComponent(projectId)}/patches`, {
    userAuth: true,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload || {})
  });
}

export async function applyAcademicProjectPatch(projectId, patchId) {
  return request(`/api/v1/academic/projects/${encodeURIComponent(projectId)}/patches/${encodeURIComponent(patchId)}/apply`, {
    userAuth: true,
    method: "POST"
  });
}

export async function queryAcademicSessions(limit = 20) {
  return request(`/api/v1/academic/sessions?limit=${encodeURIComponent(limit)}`, {
    userAuth: true,
    method: "GET"
  });
}

export async function queryAcademicSessionDetail(sessionId) {
  return request(`/api/v1/academic/sessions/${encodeURIComponent(sessionId)}`, {
    userAuth: true,
    method: "GET"
  });
}

export async function queryAcademicReplay(sessionId) {
  return request(`/api/v1/academic/sessions/${encodeURIComponent(sessionId)}/replay`, {
    userAuth: true,
    method: "GET"
  });
}

export async function queryAcademicRuns(sessionId, limit = 20) {
  return request(`/api/v1/academic/sessions/${encodeURIComponent(sessionId)}/runs?limit=${encodeURIComponent(limit)}`, {
    userAuth: true,
    method: "GET"
  });
}

export async function queryAcademicRunDetail(runId) {
  return request(`/api/v1/academic/runs/${encodeURIComponent(runId)}`, {
    userAuth: true,
    method: "GET"
  });
}

export async function queryAcademicRunDiagnosis(runId) {
  return request(`/api/v1/academic/runs/${encodeURIComponent(runId)}/diagnosis`, {
    userAuth: true,
    method: "GET"
  });
}

function decodeQuotedPrintable(text) {
  return text.replace(/=([0-9A-F]{2})/gi, (_, hex) =>
    String.fromCharCode(parseInt(hex, 16))
  );
}

function decodeMimeEncodedFilename(value, fallbackName) {
  const raw = String(value || "").trim().replace(/^["']|["']$/g, "");
  const standard = raw.match(/^=\?UTF-8\?Q\?(.+)\?=$/i);
  const loose = raw.match(/^=_UTF-8_Q_(.+)_=$/i);
  const body = standard?.[1] || loose?.[1];
  if (!body) return raw || fallbackName;
  try {
    return decodeURIComponent(escape(decodeQuotedPrintable(body.replace(/_/g, " "))));
  } catch {
    return fallbackName;
  }
}

function parseDownloadFileName(disposition, fallbackName) {
  const fallback = fallbackName || "artifact";
  const star = disposition.match(/filename\*\s*=\s*UTF-8''([^;]+)/i);
  if (star?.[1]) {
    try {
      return decodeURIComponent(star[1].trim().replace(/^"|"$/g, ""));
    } catch {
      return fallback;
    }
  }
  const regular = disposition.match(/filename\s*=\s*"([^"]+)"|filename\s*=\s*([^;]+)/i);
  const raw = regular?.[1] || regular?.[2] || "";
  return decodeMimeEncodedFilename(raw, fallback);
}

export async function downloadAcademicArtifact(downloadUrl, fallbackName = "artifact") {
  const response = await fetch(normalizeFileUrlForBrowser(downloadUrl), {
    method: "GET",
    headers: {
      ...userAuthHeader()
    }
  });
  if (!response.ok) {
    const text = await response.text().catch(() => "");
    throw new ApiError(normalizeApiMessage(text, "文件下载失败"), response.status, text);
  }
  const blob = await response.blob();
  const disposition = response.headers.get("content-disposition") || "";
  const fileName = parseDownloadFileName(disposition, fallbackName);
  const objectUrl = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = objectUrl;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(objectUrl);
}

export async function deleteAcademicSession(sessionId) {
  return request(`/api/v1/academic/sessions/${encodeURIComponent(sessionId)}`, {
    userAuth: true,
    method: "DELETE"
  });
}

export async function rollbackAcademicSession(sessionId, messageId) {
  return request(`/api/v1/academic/sessions/${encodeURIComponent(sessionId)}/rollback`, {
    userAuth: true,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ messageId })
  });
}

export async function stopAcademicStream(sessionId = getSessionId()) {
  return request("/api/v1/academic/stop", {
    userAuth: true,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ sessionId })
  }).catch(error => {
    console.warn("停止 Agent 流失败", error);
  });
}

export async function queryAcademicTaskStatus(sessionId = getSessionId()) {
  return request(`/api/v1/academic/task/status?sessionId=${encodeURIComponent(sessionId)}`, {
    userAuth: true,
    method: "GET"
  });
}

export async function generateWorkspaceImage(payload = {}) {
  return request("/api/v1/academic/workspace/image/generate", {
    userAuth: true,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      sessionId: payload.sessionId || getSessionId(),
      prompt: payload.prompt || payload.question || "",
      mode: payload.mode || "generate",
      model: payload.model || getModelConfig().imageModel || DEFAULT_MODEL_CONFIG.imageModel,
      quality: payload.quality || "auto",
      aspectRatio: payload.aspectRatio || "1:1",
      size: payload.size || "1024x1024",
      batchCount: payload.batchCount || 1,
      sourceFileIds: payload.sourceFileIds || [],
      sourceImageUrls: payload.sourceImageUrls || [],
      maskImageUrls: payload.maskImageUrls || []
    })
  });
}

export async function queryWorkspaceImageHistory({ sessionId = "", limit = 20 } = {}) {
  const params = new URLSearchParams();
  if (sessionId) params.set("sessionId", sessionId);
  params.set("limit", String(limit));
  return request(`/api/v1/academic/workspace/image/history?${params.toString()}`, {
    userAuth: true,
    method: "GET"
  });
}

export async function runWorkspaceData(payload = {}) {
  return request("/api/v1/academic/workspace/data/run", {
    userAuth: true,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      sessionId: payload.sessionId || getSessionId(),
      question: payload.question || payload.prompt || "",
      rows: payload.rows || [],
      columns: payload.columns || [],
      modelCodeList: payload.modelCodeList || [],
      schemaInfo: payload.schemaInfo || [],
      businessKnowledge: payload.businessKnowledge || "",
      dbType: payload.dbType || "mysql",
      useVector: payload.useVector !== false,
      useElastic: Boolean(payload.useElastic),
      topK: payload.topK || 5,
      maxSteps: payload.maxSteps || 10,
      includeTableRag: payload.includeTableRag !== false,
      includeNl2Sql: payload.includeNl2Sql !== false,
      includeAnalysis: payload.includeAnalysis !== false,
      includeTradeAudit: Boolean(payload.includeTradeAudit),
      auditOrderId: payload.auditOrderId || "",
      auditTeamId: payload.auditTeamId || "",
      auditKeyword: payload.auditKeyword || "",
      metadata: payload.metadata || {}
    })
  });
}

export async function queryWorkspaceDataHistory({ sessionId = "", limit = 20 } = {}) {
  const params = new URLSearchParams();
  if (sessionId) params.set("sessionId", sessionId);
  params.set("limit", String(limit));
  return request(`/api/v1/academic/workspace/data/history?${params.toString()}`, {
    userAuth: true,
    method: "GET"
  });
}

export async function queryWorkspaceDataCatalog() {
  return request("/api/v1/academic/workspace/data/catalog", {
    userAuth: true,
    method: "GET"
  });
}

export async function runWorkspaceMrag(payload = {}) {
  return request("/api/v1/academic/workspace/mrag/run", {
    userAuth: true,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      sessionId: payload.sessionId || getSessionId(),
      question: payload.question || payload.prompt || "",
      text: payload.text || "",
      imageUrls: payload.imageUrls || [],
      fileUrls: payload.fileUrls || [],
      modelCodeList: payload.modelCodeList || [],
      sourceTypes: payload.sourceTypes || [],
      topK: payload.topK || 5,
      maxResults: payload.maxResults || 5,
      includeMultimodal: payload.includeMultimodal !== false,
      includeTableRag: payload.includeTableRag !== false,
      includeDeepSearch: payload.includeDeepSearch !== false,
      useVector: payload.useVector !== false,
      useElastic: Boolean(payload.useElastic),
      metadata: payload.metadata || {}
    })
  });
}

export async function queryWorkspaceMragHistory({ sessionId = "", limit = 20 } = {}) {
  const params = new URLSearchParams();
  if (sessionId) params.set("sessionId", sessionId);
  params.set("limit", String(limit));
  return request(`/api/v1/academic/workspace/mrag/history?${params.toString()}`, {
    userAuth: true,
    method: "GET"
  });
}

export async function queryAgentCapabilities() {
  return request("/api/v1/academic/capabilities", {
    userAuth: true,
    method: "GET"
  });
}

export async function queryMcpServers() {
  return request("/api/v1/mcp/admin/servers", {
    auth: true,
    method: "GET"
  });
}

export async function registerMcpServer(payload) {
  return request("/api/v1/mcp/admin/servers", {
    auth: true,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload || {})
  });
}

export async function enableMcpServer(serverId, enabled) {
  return request(`/api/v1/mcp/admin/servers/${encodeURIComponent(serverId)}/enabled`, {
    auth: true,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ enabled: Boolean(enabled) })
  });
}

export async function cacheMcpTools(serverId, payload) {
  return request(`/api/v1/mcp/admin/servers/${encodeURIComponent(serverId)}/tools/cache`, {
    auth: true,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload || { tools: [] })
  });
}

export async function discoverMcpTools(serverId, payload) {
  return request(`/api/v1/mcp/admin/servers/${encodeURIComponent(serverId)}/tools/discover`, {
    auth: true,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload || { cache: true })
  });
}

export async function queryMcpTools({ serverId = "", enabledOnly = false } = {}) {
  const params = new URLSearchParams();
  if (serverId) params.set("serverId", serverId);
  params.set("enabledOnly", String(Boolean(enabledOnly)));
  return request(`/api/v1/mcp/admin/tools?${params.toString()}`, {
    auth: true,
    method: "GET"
  });
}

export async function queryMcpHealth() {
  return request("/api/v1/mcp/admin/health", {
    auth: true,
    method: "GET"
  });
}

export async function exportMcpState() {
  return request("/api/v1/mcp/admin/export", {
    auth: true,
    method: "GET"
  });
}

export async function importMcpState(payload) {
  return request("/api/v1/mcp/admin/import", {
    auth: true,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload || {})
  });
}

export async function callMcpTool(toolName, payload) {
  return request(`/api/v1/mcp/admin/tools/${encodeURIComponent(toolName)}/call`, {
    auth: true,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload || { arguments: {} })
  });
}

export async function queryAgentAdminConfigs({ category = "", enabledOnly = false } = {}) {
  const params = new URLSearchParams();
  if (category) params.set("category", category);
  params.set("enabledOnly", String(Boolean(enabledOnly)));
  return request(`/api/v1/agent/admin/configs?${params.toString()}`, {
    auth: true,
    method: "GET"
  });
}

export async function upsertAgentAdminConfig(payload) {
  return request("/api/v1/agent/admin/configs", {
    auth: true,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload || {})
  });
}

export async function enableAgentAdminConfig(configId, enabled) {
  return request(`/api/v1/agent/admin/configs/${encodeURIComponent(configId)}/enabled`, {
    auth: true,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ enabled: Boolean(enabled) })
  });
}

export async function deleteAgentAdminConfig(configId) {
  return request(`/api/v1/agent/admin/configs/${encodeURIComponent(configId)}`, {
    auth: true,
    method: "DELETE"
  });
}

export async function exportAgentAdminState() {
  return request("/api/v1/agent/admin/export", {
    auth: true,
    method: "GET"
  });
}

export async function importAgentAdminState(payload) {
  return request("/api/v1/agent/admin/import", {
    auth: true,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload || {})
  });
}

export async function queryAgentAdminStatistics() {
  return request("/api/v1/agent/admin/statistics", {
    auth: true,
    method: "GET"
  });
}

export async function queryAgentAdminRuntimeSnapshot() {
  return request("/api/v1/agent/admin/runtime-snapshot", {
    auth: true,
    method: "GET"
  });
}

export function requestAcademicStream({
  question,
  taskType,
  taskMode = "",
  projectId = "",
  threadId = "",
  selectedFileIds = [],
  fileId,
  imageUrl,
  imageName,
  sessionId = getSessionId(),
  modelConfig,
  webSearchEnabled = false
}, onEvent, onDone, onError) {
  return requestAcademicStreamInternal("/api/v1/academic/stream", {
    sessionId,
    projectId,
    threadId,
    question,
    taskType,
    taskMode,
    fileId: fileId || "",
    selectedFileIds: Array.isArray(selectedFileIds) ? selectedFileIds : [],
    imageUrl: imageUrl || "",
    imageName: imageName || "",
    webSearchEnabled: Boolean(webSearchEnabled),
    ...modelConfigPayload(modelConfig)
  }, onEvent, onDone, onError);
}

export function requestAcademicResumeStream(sessionId = getSessionId(), modelConfig, webSearchEnabled = false, onEvent, onDone, onError) {
  return requestAcademicStreamInternal("/api/v1/academic/resume", {
    sessionId,
    webSearchEnabled: Boolean(webSearchEnabled),
    ...modelConfigPayload(modelConfig)
  }, onEvent, onDone, onError);
}

export function requestAcademicAttachStream(sessionId = getSessionId(), onEvent, onDone, onError) {
  return requestAcademicStreamInternal("/api/v1/academic/stream/attach", {
    sessionId
  }, onEvent, onDone, onError);
}

function requestAcademicStreamInternal(path, payload, onEvent, onDone, onError) {
  const abortController = new AbortController();

  const run = async () => {
    let reader;
    let settled = false;
    const finish = () => {
      if (settled) return;
      settled = true;
      onDone?.();
    };

    try {
      const response = await fetch(path, {
        method: "POST",
        headers: {
          Accept: "text/event-stream",
          "Content-Type": "application/json",
          ...userAuthHeader()
        },
        body: JSON.stringify(payload),
        signal: abortController.signal
      });

      if (!response.ok) {
        const contentType = response.headers.get("content-type") || "";
        const payload = contentType.includes("application/json")
          ? await response.json().catch(() => null)
          : await response.text().catch(() => "");
        throw new ApiError(normalizeApiMessage(
          payload?.info || payload?.message || payload,
          `Agent 请求失败：${response.status}`
        ), response.status, payload);
      }

      reader = response.body.getReader();
      const decoder = new TextDecoder("utf-8");
      let buffer = "";

      while (true) {
        const { value, done } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const { blocks, rest } = splitAcademicSseBlocks(buffer);
        buffer = rest;

        for (const block of blocks) {
          const event = parseAcademicSseBlock(block);
          if (event) onEvent?.(event);
          if (isAcademicTerminalEvent(event)) {
            await reader.cancel().catch(() => {});
            finish();
            return;
          }
        }
      }

      buffer += decoder.decode();
      if (buffer.trim()) {
        const event = parseAcademicSseBlock(buffer);
        if (event) onEvent?.(event);
      }
      finish();
    } catch (error) {
      if (error.name === "AbortError") {
        finish();
        return;
      }
      if (settled) return;
      settled = true;
      onError?.(error);
    } finally {
      try {
        reader?.releaseLock?.();
      } catch {
        // Reader may already be released after cancel/abort.
      }
    }
  };

  run();
  return abortController;
}

export async function createDirectOrder(product, userId) {
  return request("/api/v1/trade/order/direct", {
    userAuth: true,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      userId,
      goodsId: product.id || product.goodsId,
      idempotentKey: `IDEMP_${Date.now()}`,
      payChannel: preferredPayChannel()
    })
  });
}

export async function queryGroupBuyMarketConfig(product, userId) {
  return request("/api/v1/gbm/index/query_group_buy_market_config", {
    userAuth: true,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      userId,
      source: "s01",
      channel: "c01",
      goodsId: product.id || product.goodsId
    })
  });
}

export async function lockMarketPayOrder(product, userId, options = {}) {
  const outTradeNo = options.outTradeNo || `GBM_${Date.now()}_${Math.random().toString(16).slice(2, 8)}`;
  return request("/api/v1/gbm/trade/lock_market_pay_order", {
    userAuth: true,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      userId,
      goodsId: product.id || product.goodsId,
      activityId: product.activityId,
      teamId: options.teamId || product.teamId || "",
      source: "s01",
      channel: "c01",
      outTradeNo,
      payChannel: preferredPayChannel(options.payChannel),
      notifyConfigVO: {
        notifyType: "MQ"
      }
    })
  });
}

export async function mockPaySuccess(orderId) {
  return request("/api/v1/trade/order/mock-pay-success", {
    userAuth: true,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      orderId,
      outTradeNo: `MOCK_${orderId}_${Date.now()}`,
      payChannel: "MOCK_PAY"
    })
  });
}

export async function createPayment(orderId, options = {}) {
  const origin = typeof window !== "undefined" && window.location?.origin ? window.location.origin : "http://localhost:5174";
  const returnUrl = options.returnUrl || `${origin}/?paymentReturn=1&orderId=${encodeURIComponent(orderId || "")}`;
  return request("/api/v1/payment/create", {
    userAuth: true,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      orderId,
      payChannel: preferredPayChannel(options.payChannel),
      notifyUrl: options.notifyUrl || "",
      returnUrl
    })
  });
}

export async function uploadKnowledgeDocument(file, goodsId, documentName, documentType) {
  const formData = new FormData();
  formData.append("file", file);
  formData.append("goodsId", goodsId || "global");
  if (documentName) formData.append("documentName", documentName);
  if (documentType) formData.append("documentType", documentType);

  return request("/api/v1/knowledge/document/upload-file", {
    auth: true,
    method: "POST",
    body: formData
  });
}

export async function uploadKnowledgeWebUrl(payload = {}) {
  return request("/api/v1/knowledge/document/upload-web-url", {
    auth: true,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      url: payload.url || "",
      goodsId: payload.goodsId || "global",
      documentName: payload.documentName || "",
      documentType: payload.documentType || "MRAG Web Page",
      knowledgeVersion: payload.knowledgeVersion || ""
    })
  });
}

export async function rebuildKnowledgeVector() {
  return request("/api/v1/knowledge/vector/rebuild", {
    auth: true,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({})
  });
}

export async function compensateKnowledgeVector() {
  return request("/api/v1/knowledge/vector/compensate-failed-embedding?limit=20", {
    auth: true,
    method: "POST"
  });
}

export async function getKnowledgeDocuments() {
  return request("/api/v1/knowledge/document/list?limit=10", {
    auth: true,
    method: "GET"
  });
}

export async function getKnowledgeFragments(documentId) {
  return request(`/api/v1/knowledge/document/fragments?documentId=${encodeURIComponent(documentId || "")}`, {
    auth: true,
    method: "GET"
  });
}

export async function getKnowledgeDocumentFullContent(documentId) {
  return request(`/api/v1/knowledge/document/full-content?documentId=${encodeURIComponent(documentId || "")}`, {
    auth: true,
    method: "GET"
  });
}

export async function deleteKnowledgeDocument(documentId) {
  return request(`/api/v1/knowledge/document/${encodeURIComponent(documentId || "")}`, {
    auth: true,
    method: "DELETE"
  });
}

export async function runAgentEvaluation() {
  return request("/api/v1/evaluate/agent/run", {
    auth: true,
    method: "POST"
  });
}

export async function getLatestAgentEvaluation() {
  return request("/api/v1/evaluate/agent/latest", {
    auth: true,
    method: "GET"
  });
}

export async function queryUserOrderList(options = 10) {
  const params = typeof options === "number" ? { pageSize: options } : (options || {});
  const query = new URLSearchParams();
  query.set("pageSize", String(params.pageSize || 10));
  if (params.lastId) query.set("lastId", String(params.lastId));
  if (params.marketType !== "" && params.marketType !== undefined) query.set("marketType", String(params.marketType));
  if (params.orderStatus) query.set("orderStatus", params.orderStatus);
  if (params.keyword) query.set("keyword", params.keyword);
  return request(`/api/v1/trade/order/my?${query.toString()}`, {
    userAuth: true,
    method: "GET"
  });
}

export async function queryAdminOrderList(options = {}) {
  return request("/api/v1/trade/order/admin", {
    auth: true,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      userId: options.userId || undefined,
      lastId: options.lastId || undefined,
      marketType: options.marketType === "" ? undefined : options.marketType,
      orderStatus: options.orderStatus || undefined,
      keyword: options.keyword || undefined,
      pageSize: options.pageSize || 20
    })
  });
}

export async function queryRefundOrderList(options = {}) {
  const body = {
    refundStatus: options.refundStatus || undefined,
    pageSize: options.pageSize || 20
  };
  if (options.userId !== undefined) {
    body.userId = options.userId;
  }
  return request("/api/v1/trade/order/admin/refunds", {
    auth: true,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body)
  });
}

export async function queryTradeConsistency(options = {}) {
  return request("/api/v1/trade/order/admin/consistency", {
    auth: true,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      orderId: options.orderId || undefined,
      userId: options.userId || undefined,
      pageSize: options.pageSize || 20
    })
  });
}

export async function downloadPaymentBill(payload) {
  return request("/api/v1/payment/bill/download", {
    auth: true,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
}

export async function queryPaymentRefund(payload) {
  return request("/api/v1/payment/refund/query", {
    auth: true,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
}

export async function refreshPaymentCertificate(payChannel) {
  return request("/api/v1/payment/certificate/refresh", {
    auth: true,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ payChannel })
  });
}

export async function queryPaymentErrorMap(payChannel, gatewayCode) {
  const params = new URLSearchParams({ payChannel, gatewayCode });
  return request(`/api/v1/payment/error-map?${params.toString()}`, {
    auth: true,
    method: "GET"
  });
}

export async function queryQuotaPackages(keyword = "", limit = 20) {
  const params = new URLSearchParams();
  if (keyword) params.set("keyword", keyword);
  params.set("limit", String(limit));
  return request(`/api/v1/quota/packages?${params.toString()}`, {
    method: "GET"
  });
}

export async function queryOperationalRules() {
  return request("/api/v1/ops/rules", {
    auth: true,
    method: "GET"
  });
}

export async function queryOpsDashboard() {
  return request("/api/v1/ops/dashboard", {
    auth: true,
    method: "GET"
  });
}

export async function updateOperationalRule(ruleKey, ruleValue) {
  return request("/api/v1/ops/rules", {
    auth: true,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ ruleKey, ruleValue })
  });
}
