export type TradeWorkspaceInput = {
  quota?: Record<string, unknown> | null;
  flows?: Array<Record<string, unknown>>;
  orders?: Array<Record<string, unknown>>;
};

export type TradeWorkspaceSummary = {
  quotaBalance: number;
  usedQuota: number;
  frozenQuota: number;
  totalOrders: number;
  directOrders: number;
  groupOrders: number;
  paidOrders: number;
  waitingGroupOrders: number;
  settledGroupOrders: number;
  doneOrders: number;
  refundLikeOrders: number;
  recentOrders: Array<Record<string, unknown>>;
  recentFlows: Array<Record<string, unknown>>;
  consistencyHints: string[];
};

export type TradeHistoryAuditItem = {
  id?: unknown;
  title?: unknown;
  status?: unknown;
  summary?: unknown;
  source?: Record<string, unknown> | null;
};

export type TradeSettlementHint = {
  key: string;
  label: string;
  detail: string;
  tone: "neutral" | "warn" | "ready" | "danger";
  quotaGrantAllowed: boolean;
};

const WAITING_GROUP_STATUSES = new Set(["PAY_SUCCESS", "PAY_SUCCEEDED", "PAID", "WAIT_GROUP", "GROUP_WAITING"]);
const SETTLED_GROUP_STATUSES = new Set(["GROUP_SETTLED", "DEAL_DONE"]);
const DONE_STATUSES = new Set(["DEAL_DONE", "FINISHED", "COMPLETED"]);
const REFUND_LIKE_STATUSES = new Set(["REFUND", "REFUNDING", "WAIT_REFUND", "REFUND_SUCCESS", "REFUNDED", "CLOSED"]);

function numberValue(value: unknown): number {
  const numeric = Number(value || 0);
  return Number.isFinite(numeric) ? numeric : 0;
}

function textValue(value: unknown): string {
  return String(value || "").trim();
}

function recordValue(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function orderStatus(order: Record<string, unknown>): string {
  return textValue(order.orderStatus || order.status || order.payStatus).toUpperCase();
}

function isGroupOrder(order: Record<string, unknown>): boolean {
  return Number(order.marketType || 0) === 1 || Boolean(order.teamId || order.activityId);
}

export function tradeOrderStatusLabel(status: unknown): string {
  const normalized = textValue(status).toUpperCase();
  const labels: Record<string, string> = {
    CREATE: "已创建",
    PAY_WAIT: "待支付",
    WAIT_PAY: "待支付",
    PAY_SUCCESS: "已支付",
    WAIT_GROUP: "等待成团",
    GROUP_WAITING: "等待成团",
    GROUP_SETTLED: "已成团",
    DEAL_DONE: "已完成",
    WAIT_REFUND: "待退款",
    REFUNDING: "退款中",
    REFUND_SUCCESS: "已退款",
    REFUNDED: "已退款",
    CLOSED: "已关闭"
  };
  return labels[normalized] || normalized || "未知";
}

export function tradeSettlementHint(order: Record<string, unknown>): TradeSettlementHint {
  const status = orderStatus(order);
  const groupOrder = isGroupOrder(order);
  if (REFUND_LIKE_STATUSES.has(status)) {
    return {
      key: "refund-check",
      label: "核对退款",
      detail: "退款或关闭后需要核对额度是否已回滚。",
      tone: "danger",
      quotaGrantAllowed: false
    };
  }
  if (groupOrder && WAITING_GROUP_STATUSES.has(status)) {
    return {
      key: "waiting-group",
      label: "等待成团",
      detail: "拼团支付成功只表示名额已支付，暂不能发放额度。",
      tone: "warn",
      quotaGrantAllowed: false
    };
  }
  if (groupOrder && SETTLED_GROUP_STATUSES.has(status)) {
    return {
      key: "group-settled",
      label: "核对到账",
      detail: "拼团已成团或交易完成，应核对额度流水是否到账。",
      tone: "ready",
      quotaGrantAllowed: true
    };
  }
  if (!groupOrder && WAITING_GROUP_STATUSES.has(status)) {
    return {
      key: "direct-paid",
      label: "可到账",
      detail: "直购支付成功后可以发放额度，需要核对到账流水。",
      tone: "ready",
      quotaGrantAllowed: true
    };
  }
  if (DONE_STATUSES.has(status)) {
    return {
      key: "deal-done",
      label: "已完成",
      detail: "交易已完成，应存在对应额度到账或消耗记录。",
      tone: "ready",
      quotaGrantAllowed: true
    };
  }
  return {
    key: "pending-check",
    label: "待核查",
    detail: "需要结合后端订单、支付、拼团和额度流水事实判断。",
    tone: "neutral",
    quotaGrantAllowed: false
  };
}

export function buildTradeAuditPrompt(order: Record<string, unknown>): string {
  const orderId = textValue(order.orderId || order.outTradeNo || order.payOrderId);
  const teamId = textValue(order.teamId || order.groupTeamId || order.activityId);
  const status = textValue(order.orderStatus || order.status || order.payStatus);
  const product = textValue(order.productName || order.goodsName || order.productId);
  const amount = textValue(order.payAmount || order.totalAmount || order.amount || order.lockAmount);
  const marketType = isGroupOrder(order) ? "拼团订单" : "直接购买订单";
  const settlement = tradeSettlementHint(order);
  return [
    "请按拼团交易审计 Flow 核查这笔订单。",
    orderId ? `订单号：${orderId}` : "",
    teamId ? `拼团队伍或活动：${teamId}` : "",
    product ? `商品：${product}` : "",
    amount ? `金额：${amount}` : "",
    status ? `当前状态：${status}` : "",
    `订单类型：${marketType}`,
    `结算判断：${settlement.label}。${settlement.detail}`,
    "请优先调用 trade_audit 读取后端事实，区分支付成功、成团、额度到账、退款回滚和 Agent 消耗流水，并给出结论和下一步处理建议。"
  ].filter(Boolean).join("\n");
}

export function buildTradeHistoryAuditPrompt(item: TradeHistoryAuditItem | null | undefined): string {
  const historyItem = recordValue(item);
  const source = recordValue(historyItem.source);
  return buildTradeAuditPrompt({
    ...source,
    orderId: source.orderId || historyItem.id,
    productName: source.productName || historyItem.title,
    orderStatus: source.orderStatus || source.status || historyItem.status,
    marketType: source.marketType ?? (textValue(historyItem.summary).includes("\u62fc\u56e2") ? 1 : 0)
  });
}

export function summarizeTradeWorkspace(input: TradeWorkspaceInput): TradeWorkspaceSummary {
  const quota = input.quota || {};
  const flows = Array.isArray(input.flows) ? input.flows : [];
  const orders = Array.isArray(input.orders) ? input.orders : [];
  const groupOrders = orders.filter(isGroupOrder);
  const waitingGroupOrders = groupOrders.filter((order) => WAITING_GROUP_STATUSES.has(orderStatus(order)));
  const settledGroupOrders = groupOrders.filter((order) => SETTLED_GROUP_STATUSES.has(orderStatus(order)));
  const paidOrders = orders.filter((order) => WAITING_GROUP_STATUSES.has(orderStatus(order))
    || SETTLED_GROUP_STATUSES.has(orderStatus(order)));
  const doneOrders = orders.filter((order) => DONE_STATUSES.has(orderStatus(order)));
  const refundLikeOrders = orders.filter((order) => REFUND_LIKE_STATUSES.has(orderStatus(order)));
  const consistencyHints: string[] = [];

  if (waitingGroupOrders.length > 0) {
    consistencyHints.push("存在支付成功但等待成团的拼团单，额度必须等成团后发放。");
  }
  if (settledGroupOrders.length > 0) {
    consistencyHints.push("存在已成团拼团单，需要核对额度流水是否已到账。");
  }
  if (refundLikeOrders.length > 0) {
    consistencyHints.push("存在退款或关闭订单，需要核对额度回滚流水。");
  }
  if (orders.length === 0) {
    consistencyHints.push("暂无订单，交易工作区会在购买或拼团后显示闭环状态。");
  }

  return {
    quotaBalance: numberValue(quota.quotaBalance),
    usedQuota: numberValue(quota.usedQuota),
    frozenQuota: numberValue(quota.frozenQuota),
    totalOrders: orders.length,
    directOrders: orders.length - groupOrders.length,
    groupOrders: groupOrders.length,
    paidOrders: paidOrders.length,
    waitingGroupOrders: waitingGroupOrders.length,
    settledGroupOrders: settledGroupOrders.length,
    doneOrders: doneOrders.length,
    refundLikeOrders: refundLikeOrders.length,
    recentOrders: orders.slice(0, 5),
    recentFlows: flows.slice(0, 5),
    consistencyHints
  };
}
