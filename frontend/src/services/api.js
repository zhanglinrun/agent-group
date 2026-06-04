const ADMIN_AUTH_KEY = "agentGroupAdminAuth";
const USER_AUTH_KEY = "agentGroupUserAuth";
const MODEL_CONFIG_KEY = "agentGroupModelConfig";
let adminAuthMemory = null;

const DEFAULT_MODEL_CONFIG = {
  enabled: false,
  baseUrl: "https://dashscope.aliyuncs.com/compatible-mode",
  apiKey: "",
  model: "qwen3.6-plus"
};

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

function normalizeModelBaseUrl(value) {
  let text = String(value || DEFAULT_MODEL_CONFIG.baseUrl).trim();
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
  return {
    ...DEFAULT_MODEL_CONFIG,
    ...config,
    enabled: Boolean(config.enabled),
    baseUrl: normalizeModelBaseUrl(config.baseUrl),
    apiKey: String(config.apiKey || "").trim(),
    model: String(config.model || DEFAULT_MODEL_CONFIG.model).trim()
  };
}

export function getModelConfig() {
  try {
    return normalizeModelConfig(readVolatileJson(MODEL_CONFIG_KEY) || {});
  } catch {
    return { ...DEFAULT_MODEL_CONFIG };
  }
}

export function saveModelConfig(config) {
  const normalized = normalizeModelConfig(config);
  writeVolatileJson(MODEL_CONFIG_KEY, { ...normalized, apiKey: "" });
  return normalized;
}

export function modelConfigReady(config) {
  const normalized = normalizeModelConfig(config);
  return !normalized.enabled || (Boolean(normalized.baseUrl) && Boolean(normalized.apiKey));
}

function modelConfigPayload(config) {
  const normalized = normalizeModelConfig(config);
  if (!normalized.enabled) {
    return {};
  }
  return {
    llmBaseUrl: normalized.baseUrl,
    llmApiKey: normalized.apiKey,
    llmModel: normalized.model
  };
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

export async function downloadAcademicArtifact(downloadUrl, fallbackName = "artifact") {
  const response = await fetch(downloadUrl, {
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
  const match = disposition.match(/filename\*=UTF-8''([^;]+)|filename="?([^";]+)"?/i);
  const fileName = match
    ? decodeURIComponent(match[1] || match[2] || fallbackName)
    : fallbackName;
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

export async function stopAcademicStream(sessionId = getSessionId()) {
  return request("/api/v1/academic/stop", {
    userAuth: true,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ sessionId })
  }).catch(error => {
    console.warn("停止学术智能体流失败", error);
  });
}

export async function queryAcademicTaskStatus(sessionId = getSessionId()) {
  return request(`/api/v1/academic/task/status?sessionId=${encodeURIComponent(sessionId)}`, {
    userAuth: true,
    method: "GET"
  });
}

export function requestAcademicStream({ question, taskType, fileId, imageUrl, imageName, sessionId = getSessionId(), modelConfig }, onEvent, onDone, onError) {
  return requestAcademicStreamInternal("/api/v1/academic/stream", {
    sessionId,
    question,
    taskType,
    fileId: fileId || "",
    imageUrl: imageUrl || "",
    imageName: imageName || "",
    ...modelConfigPayload(modelConfig)
  }, onEvent, onDone, onError);
}

export function requestAcademicResumeStream(sessionId = getSessionId(), modelConfig, onEvent, onDone, onError) {
  return requestAcademicStreamInternal("/api/v1/academic/resume", {
    sessionId,
    ...modelConfigPayload(modelConfig)
  }, onEvent, onDone, onError);
}

export function requestAcademicAttachStream(sessionId = getSessionId(), onEvent, onDone, onError) {
  return requestAcademicStreamInternal("/api/v1/academic/stream/attach", {
    sessionId
  }, onEvent, onDone, onError);
}

function parseAcademicStreamBlock(block, onEvent) {
  const lines = block.split(/\r?\n/);
  const dataLines = lines.filter(line => line.startsWith("data:"));
  const data = (dataLines.length ? dataLines : lines)
    .map(line => line.replace(/^data:\s*/, "").trim())
    .filter(line => line && !line.startsWith("event:") && !line.startsWith("id:") && !line.startsWith("retry:"))
    .join("");

  if (!data) return null;

  try {
    const event = JSON.parse(data);
    onEvent(event);
    return event;
  } catch (error) {
    console.warn("解析学术 SSE 数据失败", error, data);
    return null;
  }
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
          `学术智能体请求失败：${response.status}`
        ), response.status, payload);
      }

      reader = response.body.getReader();
      const decoder = new TextDecoder("utf-8");
      let buffer = "";

      while (true) {
        const { value, done } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const blocks = buffer.split(/\r?\n\r?\n/);
        buffer = blocks.pop() || "";

        for (const block of blocks) {
          const event = parseAcademicStreamBlock(block, onEvent);
          if (event?.event === "done" || event?.event === "error") {
            await reader.cancel().catch(() => {});
            finish();
            return;
          }
        }
      }

      buffer += decoder.decode();
      if (buffer.trim()) {
        parseAcademicStreamBlock(buffer, onEvent);
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
      payChannel: "MOCK_PAY"
    })
  });
}

export async function lockGroupBuyOrder(product, userId) {
  return request("/api/v1/group/trade/lock", {
    userAuth: true,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      userId,
      goodsId: product.id || product.goodsId,
      activityId: product.activityId,
      teamId: product.teamId || "",
      idempotentKey: `IDEMP_${Date.now()}`,
      payChannel: "MOCK_PAY"
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
      notifyConfigVO: {
        notifyType: "MQ"
      }
    })
  });
}

export async function mockPaySuccess(orderId) {
  return request("/api/v1/trade/order/mock-pay-success", {
    auth: true,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      orderId,
      outTradeNo: `MOCK_${orderId}_${Date.now()}`,
      payChannel: "MOCK_PAY"
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
  return request("/api/v1/alipay/query_user_order_list", {
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
  return request("/api/v1/alipay/query_refund_order_list", {
    auth: true,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body)
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
