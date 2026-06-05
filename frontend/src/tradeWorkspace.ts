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

const WAITING_GROUP_STATUSES = new Set(["PAY_SUCCESS", "PAY_SUCCEEDED", "PAID", "WAIT_GROUP", "GROUP_WAITING"]);
const SETTLED_GROUP_STATUSES = new Set(["GROUP_SETTLED", "DEAL_DONE"]);
const DONE_STATUSES = new Set(["DEAL_DONE", "FINISHED", "COMPLETED"]);
const REFUND_LIKE_STATUSES = new Set(["REFUND", "REFUNDING", "REFUND_SUCCESS", "REFUNDED", "CLOSED"]);

function numberValue(value: unknown): number {
  const numeric = Number(value || 0);
  return Number.isFinite(numeric) ? numeric : 0;
}

function textValue(value: unknown): string {
  return String(value || "").trim();
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
    WAIT_PAY: "待支付",
    PAY_SUCCESS: "已支付",
    WAIT_GROUP: "等待成团",
    GROUP_WAITING: "等待成团",
    GROUP_SETTLED: "已成团",
    DEAL_DONE: "已完成",
    REFUNDING: "退款中",
    REFUND_SUCCESS: "已退款",
    CLOSED: "已关闭"
  };
  return labels[normalized] || normalized || "未知";
}

export function buildTradeAuditPrompt(order: Record<string, unknown>): string {
  const orderId = textValue(order.orderId || order.outTradeNo || order.payOrderId);
  const teamId = textValue(order.teamId || order.groupTeamId || order.activityId);
  const status = textValue(order.orderStatus || order.status || order.payStatus);
  const product = textValue(order.productName || order.goodsName || order.productId);
  const amount = textValue(order.payAmount || order.totalAmount || order.amount || order.lockAmount);
  const marketType = isGroupOrder(order) ? "拼团订单" : "直接购买订单";
  return [
    "请按拼团交易审计 Flow 核查这笔订单。",
    orderId ? `订单号：${orderId}` : "",
    teamId ? `拼团队伍或活动：${teamId}` : "",
    product ? `商品：${product}` : "",
    amount ? `金额：${amount}` : "",
    status ? `当前状态：${status}` : "",
    `订单类型：${marketType}`,
    "请优先调用 trade_audit 读取后端事实，区分支付成功、成团、额度到账、退款回滚和 Agent 消耗流水，并给出结论和下一步处理建议。"
  ].filter(Boolean).join("\n");
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
