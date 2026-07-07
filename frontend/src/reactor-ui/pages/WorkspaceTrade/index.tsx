import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import {
  ArrowLeft,
  CreditCard,
  Loader2,
  LogIn,
  RefreshCw,
  ShoppingBag,
  UserPlus,
  Wallet,
  X,
} from "lucide-react";

import { ROUTES } from "@/router/routes";
import {
  openGatewayPayment,
  paymentReturnUrl,
  preferredFrontendPayChannel,
} from "../../../appRuntime";
import {
  createDirectOrder,
  createPayment,
  getQuotaSummary,
  getUserAuth,
  login,
  lockMarketPayOrder,
  normalizeApiMessage,
  queryQuotaPackages,
  queryGroupBuyMarketConfig,
  queryUserOrderList,
} from "../../../services/api.js";
import {
  summarizeTradeWorkspace,
  tradeOrderStatusLabel,
  tradeSettlementHint,
} from "../../../tradeWorkspace";
import { tradeOrderAmount } from "../../../appFormatters";

const DEMO_CREDENTIALS = {
  username: "demo",
  password: "123456",
};

type TradeTab = "packages" | "orders";

type TradePackage = Record<string, unknown>;
type TradeOrder = Record<string, unknown>;
type GroupConfig = Record<string, unknown> | null;

export default function WorkspaceTrade() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [auth, setAuth] = useState(() => getUserAuth());
  const [loading, setLoading] = useState(false);
  const [loginLoading, setLoginLoading] = useState(false);
  const [error, setError] = useState("");
  const [quota, setQuota] = useState<Record<string, unknown> | null>(null);
  const [flows, setFlows] = useState<Array<Record<string, unknown>>>([]);
  const [orders, setOrders] = useState<Array<Record<string, unknown>>>([]);
  const [packages, setPackages] = useState<Array<Record<string, unknown>>>([]);
  const [buyingKey, setBuyingKey] = useState("");
  const [groupPreviewPackage, setGroupPreviewPackage] = useState<TradePackage | null>(null);
  const [groupMarketConfig, setGroupMarketConfig] = useState<GroupConfig>(null);
  const [groupTeamsLoading, setGroupTeamsLoading] = useState(false);

  const summary = useMemo(
    () => summarizeTradeWorkspace({ quota, flows, orders }),
    [quota, flows, orders]
  );
  const activeTab = searchParams.get("tab") === "orders" ? "orders" : "packages";

  const loadTradeData = useCallback(async () => {
    if (!getUserAuth()?.token) return;
    setLoading(true);
    setError("");
    try {
      const [quotaRes, orderRes, packageRes] = await Promise.all([
        getQuotaSummary(20),
        queryUserOrderList({ pageSize: 8 }),
        queryQuotaPackages("", 6),
      ]);

      if (quotaRes?.code === "0000") {
        setQuota(quotaRes.data?.account || null);
        setFlows(Array.isArray(quotaRes.data?.flows) ? quotaRes.data.flows : []);
      }
      if (orderRes?.code === "0000") {
        setOrders(Array.isArray(orderRes.data?.orderList) ? orderRes.data.orderList : []);
      }
      if (packageRes?.code === "0000") {
        setPackages(Array.isArray(packageRes.data?.records) ? packageRes.data.records : []);
      }
    } catch (nextError) {
      setError(normalizeApiMessage((nextError as Error)?.message, "交易数据读取失败"));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadTradeData().catch(() => {});
  }, [auth?.token, loadTradeData]);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    if (params.get("paymentReturn") !== "1" || !getUserAuth()?.token) return;
    setSearchParams({ tab: "orders" }, { replace: true });
    loadTradeData().catch(() => {});
  }, [loadTradeData, setSearchParams]);

  const handleDemoLogin = async () => {
    setLoginLoading(true);
    setError("");
    try {
      const res = await login(DEMO_CREDENTIALS.username, DEMO_CREDENTIALS.password);
      if (res?.code === "0000" && res.data?.token) {
        setAuth(res.data);
      } else {
        throw new Error(res?.info || res?.message || "登录失败");
      }
    } catch (nextError) {
      setError(normalizeApiMessage((nextError as Error)?.message, "登录失败"));
    } finally {
      setLoginLoading(false);
    }
  };

  const switchTab = (tab: TradeTab) => {
    setSearchParams({ tab });
  };

  const openPayment = useCallback(async (seed: Record<string, unknown>) => {
    const orderId = String(seed.orderId || "");
    if (!orderId) {
      throw new Error("订单号为空，无法拉起支付");
    }
    setBuyingKey(`pay-${orderId}`);
    const payWindow = window.open("", "_blank");
    if (payWindow && !payWindow.closed) {
      payWindow.document.write("<!doctype html><html><head><meta charset=\"UTF-8\"><title>支付宝支付</title></head><body>正在进入支付宝...</body></html>");
      payWindow.document.close();
    }
    try {
      if (openGatewayPayment(seed, payWindow)) {
        return;
      }
      const payRes = await createPayment(orderId, {
        payChannel: String(seed.payChannel || preferredFrontendPayChannel()),
        returnUrl: paymentReturnUrl(orderId),
      });
      if (payRes?.code !== "0000") {
        throw new Error(payRes?.info || payRes?.message || "支付宝支付创建失败");
      }
      if (!openGatewayPayment(payRes.data || {}, payWindow)) {
        throw new Error("支付宝支付表单为空");
      }
    } finally {
      setBuyingKey("");
    }
  }, []);

  const loadGroupPreview = useCallback(async (pkg: TradePackage) => {
    if (!getUserAuth()?.token) return;
    setGroupPreviewPackage(pkg);
    setGroupMarketConfig(null);
    setGroupTeamsLoading(true);
    try {
      const userId = String(auth?.userId || quota?.userId || "");
      const res = await queryGroupBuyMarketConfig(pkg, userId);
      if (res?.code !== "0000") {
        throw new Error(res?.info || res?.message || "拼团信息读取失败");
      }
      setGroupMarketConfig(res.data || null);
    } catch (nextError) {
      setError(normalizeApiMessage((nextError as Error)?.message, "拼团信息读取失败"));
    } finally {
      setGroupTeamsLoading(false);
    }
  }, [auth?.userId, quota?.userId]);

  const handleBuy = useCallback(async (
    pkg: TradePackage,
    buyType: "direct" | "group",
    options: { teamId?: string } = {}
  ) => {
    if (!auth?.token) {
      return;
    }
    const key = `${String(pkg.goodsId || pkg.id || "pkg")}-${buyType}${options.teamId ? `-${options.teamId}` : ""}`;
    setBuyingKey(key);
    setError("");
    try {
      const product = {
        ...pkg,
        activityId:
          pkg.activityId ||
          (groupMarketConfig?.activityId as string | undefined) ||
          "",
        groupPrice:
          Number(groupMarketConfig?.goods?.payPrice || pkg.groupPrice || pkg.originPrice || 0),
        originPrice:
          Number(groupMarketConfig?.goods?.originalPrice || pkg.originPrice || 0),
        teamId: options.teamId || "",
      };
      if (buyType === "group" && !product.activityId) {
        throw new Error("当前额度包没有可用拼团活动");
      }
      const userId = String(auth.userId || quota?.userId || "");
      const orderRes =
        buyType === "group"
          ? await lockMarketPayOrder(product, userId, { teamId: options.teamId || "" })
          : await createDirectOrder(product, userId);
      if (orderRes?.code !== "0000") {
        throw new Error(orderRes?.info || orderRes?.message || "订单创建失败");
      }
      const data = orderRes.data || {};
      await openPayment({
        orderId: data.orderId,
        payUrl: data.payUrl || "",
        payFormHtml: data.payFormHtml || "",
        paymentType: data.paymentType || "",
        payChannel: data.payChannel || preferredFrontendPayChannel(),
      });
      switchTab("orders");
      setGroupPreviewPackage(null);
      setGroupMarketConfig(null);
      await loadTradeData();
    } catch (nextError) {
      setError(normalizeApiMessage((nextError as Error)?.message, "购买失败"));
    } finally {
      setBuyingKey("");
    }
  }, [auth, groupMarketConfig, loadTradeData, openPayment, quota?.userId]);

  if (!auth?.token) {
    return (
      <div className="flex h-full min-h-screen items-center justify-center bg-[var(--page-gradient)] px-6">
        <div className="w-full max-w-md rounded-3xl border border-[var(--chat-border)] bg-[var(--chat-surface)]/95 p-8 shadow-[var(--shadow-lg)]">
          <div className="mb-3 flex items-center gap-3">
            <div className="rounded-2xl bg-[var(--chat-surface-soft)] p-3">
              <Wallet className="h-5 w-5 text-[var(--chat-text)]" />
            </div>
            <div>
              <h1 className="text-lg font-medium text-[var(--chat-text)]">额度交易工作台</h1>
              <p className="text-sm text-[var(--chat-text-soft)]">先登录，再查看额度、订单和拼团状态。</p>
            </div>
          </div>
          {error ? (
            <div className="mb-4 rounded-2xl border border-[var(--status-failed-text)]/20 bg-[var(--status-failed-bg)] px-4 py-3 text-sm text-[var(--status-failed-text)]">
              {error}
            </div>
          ) : null}
          <div className="flex gap-3">
            <button
              type="button"
              onClick={handleDemoLogin}
              disabled={loginLoading}
              className="inline-flex items-center gap-2 rounded-full bg-[var(--chat-text)] px-4 py-2 text-sm text-white disabled:opacity-60"
            >
              {loginLoading ? <Loader2 className="h-4 w-4 animate-spin" /> : <LogIn className="h-4 w-4" />}
              <span>{loginLoading ? "登录中..." : "演示账号登录"}</span>
            </button>
            <Link
              to={ROUTES.HOME}
              className="inline-flex items-center rounded-full border border-[var(--chat-border)] px-4 py-2 text-sm text-[var(--chat-text-soft)]"
            >
              返回主页
            </Link>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-[var(--page-gradient)] px-6 py-6 text-[var(--chat-text)]">
      <div className="mx-auto flex max-w-6xl flex-col gap-6">
        <div className="flex flex-col gap-4 rounded-3xl border border-[var(--chat-border)] bg-[var(--chat-surface)]/90 p-6 shadow-[var(--shadow-md)] lg:flex-row lg:items-center lg:justify-between">
          <div>
            <div className="text-xl font-medium">
              {activeTab === "packages" ? "额度购买" : "订单与到账状态"}
            </div>
            <div className="mt-1 text-sm text-[var(--chat-text-soft)]">
              {activeTab === "packages"
                ? "选择额度包后可直接购买，也可以发起拼团；拼团成团后额度到账。"
                : "查看支付状态、拼团进度和最近额度流水。"}
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <div className="inline-flex rounded-full border border-[var(--chat-border)] bg-[var(--chat-surface)]/70 p-1">
              <button
                type="button"
                onClick={() => switchTab("packages")}
                className={
                  activeTab === "packages"
                    ? "rounded-full bg-[var(--chat-text)] px-4 py-2 text-sm text-white"
                    : "rounded-full px-4 py-2 text-sm text-[var(--chat-text-soft)]"
                }
              >
                购买额度
              </button>
              <button
                type="button"
                onClick={() => switchTab("orders")}
                className={
                  activeTab === "orders"
                    ? "rounded-full bg-[var(--chat-text)] px-4 py-2 text-sm text-white"
                    : "rounded-full px-4 py-2 text-sm text-[var(--chat-text-soft)]"
                }
              >
                订单记录
              </button>
            </div>
            <button
              type="button"
              onClick={() => loadTradeData().catch(() => {})}
              disabled={loading}
              className="inline-flex items-center gap-2 rounded-full border border-[var(--chat-border)] px-4 py-2 text-sm text-[var(--chat-text-soft)] disabled:opacity-60"
            >
              {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <RefreshCw className="h-4 w-4" />}
              <span>刷新</span>
            </button>
            <Link
              to={ROUTES.HOME}
              className="inline-flex items-center rounded-full border border-[var(--chat-border)] px-4 py-2 text-sm text-[var(--chat-text-soft)]"
            >
              返回对话
            </Link>
          </div>
        </div>

        {error ? (
          <div className="rounded-2xl border border-[var(--status-failed-text)]/20 bg-[var(--status-failed-bg)] px-4 py-3 text-sm text-[var(--status-failed-text)]">
            {error}
          </div>
        ) : null}

        <div className="grid gap-4 md:grid-cols-4">
          <StatCard label="当前余额" value={`${summary.quotaBalance.toFixed(2)} 点`} />
          <StatCard label="已用额度" value={`${summary.usedQuota.toFixed(2)} 点`} />
          <StatCard label="拼团订单" value={`${summary.groupOrders} 单`} />
          <StatCard label="待成团" value={`${summary.waitingGroupOrders} 单`} warn={summary.waitingGroupOrders > 0} />
        </div>
        {activeTab === "packages" ? (
          <section className="rounded-3xl border border-[var(--chat-border)] bg-[var(--chat-surface)]/90 p-6 shadow-[var(--shadow-sm)]">
            <div className="mb-5 flex items-center gap-2 text-base font-medium">
              <ShoppingBag className="h-4 w-4" />
              <span>可购额度包</span>
            </div>
            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
              {packages.length === 0 ? (
                <EmptyLine text="暂无可用额度包" />
              ) : (
                packages.map((item, index) => {
                  const goodsId = String(item.goodsId || item.productId || index);
                  return (
                    <article
                      key={goodsId}
                      className="rounded-3xl border border-[var(--chat-border)] bg-white/60 p-5 shadow-[var(--shadow-sm)]"
                    >
                      <div className="text-base font-medium">
                        {String(item.goodsName || item.productName || "额度包")}
                      </div>
                      <div className="mt-2 text-sm text-[var(--chat-text-soft)]">
                        {String(item.specSummary || item.description || item.activityDesc || "购买后可用于对话、文件理解、深度任务和生图。")}
                      </div>
                      <div className="mt-4 text-2xl font-medium">
                        {Number(item.quotaAmount || 0).toFixed(0)} 点
                      </div>
                      <div className="mt-1 text-sm text-[var(--chat-text-soft)]">
                        直购价 ￥{Number(item.originPrice || 0).toFixed(2)}
                      </div>
                      <div className="mt-5 flex flex-wrap gap-3">
                        <button
                          type="button"
                          onClick={() => handleBuy(item, "direct").catch?.(() => {})}
                          disabled={Boolean(buyingKey)}
                          className="inline-flex items-center gap-2 rounded-full bg-[var(--chat-text)] px-4 py-2 text-sm text-white disabled:opacity-60"
                        >
                          {buyingKey === `${goodsId}-direct` ? (
                            <Loader2 className="h-4 w-4 animate-spin" />
                          ) : (
                            <CreditCard className="h-4 w-4" />
                          )}
                          <span>直接购买</span>
                        </button>
                        <button
                          type="button"
                          onClick={() => loadGroupPreview(item).catch?.(() => {})}
                          disabled={Boolean(buyingKey)}
                          className="inline-flex items-center gap-2 rounded-full border border-[var(--chat-border)] px-4 py-2 text-sm text-[var(--chat-text-soft)] disabled:opacity-60"
                        >
                          <UserPlus className="h-4 w-4" />
                          <span>拼团购买</span>
                        </button>
                      </div>
                    </article>
                  );
                })
              )}
            </div>
          </section>
        ) : (
          <div className="grid gap-6 lg:grid-cols-[1.2fr_0.8fr]">
            <section className="rounded-3xl border border-[var(--chat-border)] bg-[var(--chat-surface)]/90 p-6 shadow-[var(--shadow-sm)]">
              <div className="mb-4 flex items-center justify-between">
                <div className="text-base font-medium">最近订单</div>
                <div className="text-sm text-[var(--chat-text-soft)]">{summary.totalOrders} 单</div>
              </div>
              <div className="space-y-3">
                {summary.recentOrders.length === 0 ? (
                  <EmptyLine text="暂无订单" />
                ) : (
                  summary.recentOrders.map((order, index) => {
                    const hint = tradeSettlementHint(order);
                    return (
                      <article
                        key={String(order.orderId || order.outTradeNo || index)}
                        className="rounded-2xl border border-[var(--chat-border)] bg-white/60 p-4"
                      >
                        <div className="flex flex-wrap items-start justify-between gap-3">
                          <div className="min-w-0">
                            <div className="truncate text-sm font-medium">
                              {String(order.productName || order.goodsName || order.productId || "额度订单")}
                            </div>
                            <div className="mt-1 text-xs text-[var(--chat-text-soft)]">
                              {String(order.orderId || order.outTradeNo || "未生成订单号")}
                            </div>
                          </div>
                          <div className="text-right">
                            <div className="text-sm font-medium">￥{tradeOrderAmount(order)}</div>
                            <div className="mt-1 text-xs text-[var(--chat-text-soft)]">
                              {tradeOrderStatusLabel(order.orderStatus || order.status || order.payStatus)}
                            </div>
                          </div>
                        </div>
                        <div className="mt-3 flex flex-wrap items-center gap-3">
                          <div className="inline-flex rounded-full bg-[var(--chat-surface-soft)] px-3 py-1 text-xs text-[var(--chat-text-soft)]">
                            {hint.label}：{hint.detail}
                          </div>
                          {(order.status === "CREATE" || order.status === "PAY_WAIT") ? (
                            <button
                              type="button"
                              onClick={() => openPayment({
                                orderId: order.orderId,
                                payUrl: order.payUrl || "",
                                payFormHtml: "",
                                paymentType: "",
                                payChannel: order.payChannel || preferredFrontendPayChannel(),
                              }).catch((nextError) => {
                                setError(normalizeApiMessage((nextError as Error)?.message, "支付宝支付创建失败"));
                              })}
                              disabled={buyingKey === `pay-${String(order.orderId || "")}`}
                              className="inline-flex items-center gap-2 rounded-full border border-[var(--chat-border)] px-3 py-1 text-xs text-[var(--chat-text-soft)]"
                            >
                              {buyingKey === `pay-${String(order.orderId || "")}` ? (
                                <Loader2 className="h-3.5 w-3.5 animate-spin" />
                              ) : (
                                <CreditCard className="h-3.5 w-3.5" />
                              )}
                              <span>继续支付</span>
                            </button>
                          ) : null}
                        </div>
                      </article>
                    );
                  })
                )}
              </div>
            </section>

            <section className="rounded-3xl border border-[var(--chat-border)] bg-[var(--chat-surface)]/90 p-6 shadow-[var(--shadow-sm)]">
              <div className="mb-4 text-base font-medium">最近额度流水</div>
              <div className="space-y-3">
                {summary.recentFlows.length === 0 ? (
                  <EmptyLine text="暂无流水" />
                ) : (
                  summary.recentFlows.map((flow, index) => (
                    <article
                      key={String(flow.flowId || flow.bizId || index)}
                      className="rounded-2xl border border-[var(--chat-border)] bg-white/60 p-4"
                    >
                      <div className="text-sm font-medium">
                        {String(flow.bizType || flow.flowType || "额度流水")}
                      </div>
                      <div className="mt-1 text-xs text-[var(--chat-text-soft)]">
                        {String(flow.bizId || flow.remark || flow.createTime || "")}
                      </div>
                      <div className="mt-2 text-sm">{Number(flow.quotaAmount || 0).toFixed(2)} 点</div>
                    </article>
                  ))
                )}
              </div>
            </section>
          </div>
        )}
      </div>

      {groupPreviewPackage ? (
        <GroupPreviewDialog
          pkg={groupPreviewPackage}
          config={groupMarketConfig}
          loading={groupTeamsLoading}
          buyingKey={buyingKey}
          onClose={() => {
            setGroupPreviewPackage(null);
            setGroupMarketConfig(null);
          }}
          onBuy={handleBuy}
        />
      ) : null}
    </div>
  );
}

function StatCard(props: { label: string; value: string; warn?: boolean }) {
  return (
    <div className="rounded-3xl border border-[var(--chat-border)] bg-[var(--chat-surface)]/90 p-5 shadow-[var(--shadow-sm)]">
      <div className="text-sm text-[var(--chat-text-soft)]">{props.label}</div>
      <div className={props.warn ? "mt-2 text-xl font-medium text-[var(--warning)]" : "mt-2 text-xl font-medium"}>
        {props.value}
      </div>
    </div>
  );
}

function EmptyLine(props: { text: string }) {
  return (
    <div className="rounded-2xl border border-dashed border-[var(--chat-border)] px-4 py-6 text-sm text-[var(--chat-text-soft)]">
      {props.text}
    </div>
  );
}

function GroupPreviewDialog(props: {
  pkg: TradePackage;
  config: GroupConfig;
  loading: boolean;
  buyingKey: string;
  onClose: () => void;
  onBuy: (pkg: TradePackage, buyType: "direct" | "group", options?: { teamId?: string }) => Promise<void>;
}) {
  const goods = (props.config?.goods as Record<string, unknown>) || {};
  const teamList = Array.isArray(props.config?.teamList) ? props.config?.teamList as Array<Record<string, unknown>> : [];
  const groupEnabled = props.config?.enable !== false;
  const groupPrice = Number(goods.payPrice || props.pkg.groupPrice || props.pkg.originPrice || 0).toFixed(2);
  const originPrice = Number(goods.originalPrice || props.pkg.originPrice || 0).toFixed(2);
  const goodsId = String(props.pkg.goodsId || props.pkg.id || "pkg");

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/45 px-4 py-6 backdrop-blur-sm">
      <div className="w-full max-w-3xl rounded-3xl border border-[var(--chat-border)] bg-[var(--chat-surface)] p-6 shadow-[var(--shadow-lg)]">
        <div className="mb-5 flex items-center justify-between gap-3">
          <button
            type="button"
            onClick={props.onClose}
            className="inline-flex h-9 w-9 items-center justify-center rounded-full border border-[var(--chat-border)]"
          >
            <ArrowLeft className="h-4 w-4" />
          </button>
          <div className="min-w-0 flex-1">
            <div className="truncate text-lg font-medium">
              {String(props.pkg.goodsName || props.pkg.productName || "额度包")}
            </div>
            <div className="mt-1 text-sm text-[var(--chat-text-soft)]">
              直接购买立即到账；拼团支付成功后等待成团到账。
            </div>
          </div>
          <button
            type="button"
            onClick={props.onClose}
            className="inline-flex h-9 w-9 items-center justify-center rounded-full border border-[var(--chat-border)]"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <div className="grid gap-4 md:grid-cols-[0.9fr_1.1fr]">
          <section className="rounded-2xl border border-[var(--chat-border)] bg-white/50 p-5">
            <div className="text-sm text-[var(--chat-text-soft)]">额度包信息</div>
            <div className="mt-2 text-xl font-medium">{Number(props.pkg.quotaAmount || 0).toFixed(0)} 点</div>
            <div className="mt-2 text-sm text-[var(--chat-text-soft)]">
              直购 ￥{originPrice}，拼团 ￥{groupPrice}
            </div>
            <div className="mt-4 flex flex-wrap gap-3">
              <button
                type="button"
                onClick={() => props.onBuy(props.pkg, "direct")}
                disabled={Boolean(props.buyingKey)}
                className="inline-flex items-center gap-2 rounded-full bg-[var(--chat-text)] px-4 py-2 text-sm text-white disabled:opacity-60"
              >
                {props.buyingKey === `${goodsId}-direct` ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <CreditCard className="h-4 w-4" />
                )}
                <span>直接购买</span>
              </button>
              <button
                type="button"
                onClick={() => props.onBuy(props.pkg, "group")}
                disabled={Boolean(props.buyingKey) || !groupEnabled}
                className="inline-flex items-center gap-2 rounded-full border border-[var(--chat-border)] px-4 py-2 text-sm text-[var(--chat-text-soft)] disabled:opacity-60"
              >
                {props.buyingKey === `${goodsId}-group` ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <UserPlus className="h-4 w-4" />
                )}
                <span>自己开团</span>
              </button>
            </div>
          </section>

          <section className="rounded-2xl border border-[var(--chat-border)] bg-white/50 p-5">
            <div className="mb-3 text-base font-medium">可加入拼团</div>
            {props.loading ? (
              <EmptyLine text="拼团列表读取中..." />
            ) : teamList.length === 0 ? (
              <EmptyLine text="暂无可加入队伍，可以自己开团。" />
            ) : (
              <div className="space-y-3">
                {teamList.map((team, index) => {
                  const teamId = String(team.teamId || index);
                  const remaining = Number(team.progress?.remainingCount ?? Math.max(Number(team.targetCount || 0) - Number(team.lockCount || 0), 0));
                  const complete = Number(team.progress?.completeCount ?? team.completeCount ?? 0);
                  return (
                    <article key={teamId} className="rounded-2xl border border-[var(--chat-border)] bg-[var(--chat-surface)]/70 p-4">
                      <div className="text-sm font-medium">{teamId}</div>
                      <div className="mt-1 text-xs text-[var(--chat-text-soft)]">
                        已支付 {complete} 人，还差 {remaining} 人
                      </div>
                      <button
                        type="button"
                        onClick={() => props.onBuy(props.pkg, "group", { teamId })}
                        disabled={Boolean(props.buyingKey) || remaining <= 0 || !groupEnabled}
                        className="mt-3 inline-flex items-center gap-2 rounded-full border border-[var(--chat-border)] px-3 py-1.5 text-xs text-[var(--chat-text-soft)] disabled:opacity-60"
                      >
                        {props.buyingKey === `${goodsId}-group-${teamId}` ? (
                          <Loader2 className="h-3.5 w-3.5 animate-spin" />
                        ) : (
                          <UserPlus className="h-3.5 w-3.5" />
                        )}
                        <span>加入这个团</span>
                      </button>
                    </article>
                  );
                })}
              </div>
            )}
          </section>
        </div>
      </div>
    </div>
  );
}
