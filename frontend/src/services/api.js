export const DEMO_USER_ID = "U10001";

const ADMIN_AUTH_KEY = "agentGroupAdminAuth";

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
  try {
    return JSON.parse(localStorage.getItem(ADMIN_AUTH_KEY) || "null");
  } catch {
    return null;
  }
}

export function saveAdminAuth(username, password) {
  localStorage.setItem(ADMIN_AUTH_KEY, JSON.stringify({ username, password }));
}

export function clearAdminAuth() {
  localStorage.removeItem(ADMIN_AUTH_KEY);
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
    throw new ApiError(payload?.info || payload?.message || fallback, response.status, payload);
  }
  return payload;
}

async function request(path, options = {}) {
  const { auth = false, headers, ...rest } = options;
  const response = await fetch(path, {
    ...rest,
    headers: {
      ...(auth ? authHeader() : {}),
      ...(headers || {})
    }
  });
  return parseResponse(response);
}

export function requestGuideStream(message, imageUrl, imageName, onEvent, onDone, onError, sessionId = getSessionId()) {
  const abortController = new AbortController();

  const run = async () => {
    try {
      const response = await fetch("/api/v1/agent/guide/stream", {
        method: "POST",
        headers: {
          Accept: "text/event-stream",
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          sessionId,
          userId: DEMO_USER_ID,
          question: message,
          imageUrl: imageUrl || "",
          imageName: imageName || ""
        }),
        signal: abortController.signal
      });

      if (!response.ok) {
        throw new ApiError(`导购流请求失败：${response.status}`, response.status);
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder("utf-8");
      let buffer = "";

      while (true) {
        const { value, done } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const blocks = buffer.split(/\r?\n\r?\n/);
        buffer = blocks.pop() || "";

        for (const block of blocks) {
          const lines = block.split(/\r?\n/);
          const dataLines = lines.filter(line => line.startsWith("data:"));
          const data = (dataLines.length ? dataLines : lines)
            .map(line => line.replace(/^data:\s*/, "").trim())
            .filter(line => line && !line.startsWith("event:") && !line.startsWith("id:") && !line.startsWith("retry:"))
            .join("");

          if (!data) continue;

          try {
            onEvent(JSON.parse(data));
          } catch (error) {
            console.warn("解析 SSE 数据失败", error, data);
          }
        }
      }

      onDone?.();
    } catch (error) {
      if (error.name === "AbortError") {
        onDone?.();
        return;
      }
      onError?.(error);
    }
  };

  run();
  return abortController;
}

export async function stopGuideStream(sessionId = getSessionId()) {
  return request("/api/v1/agent/stop", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ sessionId })
  }).catch(error => {
    console.warn("停止导购流失败", error);
  });
}

export async function createDirectOrder(product) {
  return request("/api/v1/trade/order/direct", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      userId: DEMO_USER_ID,
      goodsId: product.id,
      decisionId: product.decisionId,
      idempotentKey: `IDEMP_${Date.now()}`,
      payChannel: "MOCK_PAY"
    })
  });
}

export async function lockGroupBuyOrder(product) {
  return request("/api/v1/group/trade/lock", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      userId: DEMO_USER_ID,
      goodsId: product.id,
      decisionId: product.decisionId,
      activityId: product.activityId,
      teamId: "",
      idempotentKey: `IDEMP_${Date.now()}`,
      payChannel: "MOCK_PAY"
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

export async function runGuideEvaluation() {
  return request("/api/v1/evaluate/guide/run", {
    auth: true,
    method: "POST"
  });
}

export async function getLatestGuideEvaluation() {
  return request("/api/v1/evaluate/guide/latest", {
    auth: true,
    method: "GET"
  });
}

export async function queryUserOrderList(options = 10) {
  const params = typeof options === "number" ? { pageSize: options } : (options || {});
  return request("/api/v1/alipay/query_user_order_list", {
    auth: true,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      userId: DEMO_USER_ID,
      pageSize: params.pageSize || 10,
      lastId: params.lastId,
      marketType: params.marketType === "" || params.marketType === undefined ? undefined : Number(params.marketType),
      orderStatus: params.orderStatus || undefined,
      keyword: params.keyword || undefined
    })
  });
}

export async function queryRefundOrderList(options = {}) {
  return request("/api/v1/alipay/query_refund_order_list", {
    auth: true,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      userId: options.userId === undefined ? DEMO_USER_ID : options.userId,
      refundStatus: options.refundStatus || undefined,
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

export async function queryProductCatalog(keyword = "", limit = 20) {
  const params = new URLSearchParams();
  if (keyword) params.set("keyword", keyword);
  params.set("limit", String(limit));
  return request(`/api/v1/mall/products?${params.toString()}`, {
    method: "GET"
  });
}

export async function queryProductDetail(goodsId) {
  return request(`/api/v1/mall/products/${encodeURIComponent(goodsId)}`, {
    method: "GET"
  });
}

export async function validateCart(items) {
  return request("/api/v1/mall/cart/validate", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      userId: DEMO_USER_ID,
      items: (items || []).map((item) => ({
        goodsId: item.goodsId,
        quantity: item.quantity || 1,
        marketType: item.marketType,
        activityId: item.activityId
      }))
    })
  });
}

export async function createLegacyPayOrder({ productId, decisionId, marketType, activityId, teamId }) {
  return request("/api/v1/alipay/create_pay_order", {
    auth: true,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      userId: DEMO_USER_ID,
      productId,
      decisionId,
      marketType: Number(marketType || 0),
      activityId,
      teamId,
      payChannel: "MOCK_PAY",
      idempotentKey: `IDEMP_${Date.now()}`
    })
  });
}

export async function refundOrder(orderId, refundReason = "用户申请售后退款") {
  return request("/api/v1/alipay/refund_order", {
    auth: true,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      userId: DEMO_USER_ID,
      orderId,
      refundReason
    })
  });
}

export async function activePayNotify(orderId) {
  return request(`/api/v1/alipay/active_pay_notify?outTradeNo=${encodeURIComponent(orderId)}`, {
    auth: true,
    method: "POST"
  });
}

export async function createWeixinLoginQr() {
  return request("/api/v1/weixin/login/qr", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      userId: DEMO_USER_ID,
      redirectUrl: "/"
    })
  });
}

export async function queryWeixinLoginStatus(sceneId) {
  return request(`/api/v1/weixin/login/status?sceneId=${encodeURIComponent(sceneId)}`, {
    method: "GET"
  });
}

export async function simulateWeixinScan(sceneId) {
  return request("/api/v1/weixin/login/simulate", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      sceneId,
      userId: DEMO_USER_ID,
      openId: `mock_openid_${DEMO_USER_ID}`,
      nickname: "演示用户"
    })
  });
}

export async function sendWeixinTemplateMessage(payload) {
  return request("/api/v1/weixin/template/send", {
    auth: true,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
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
