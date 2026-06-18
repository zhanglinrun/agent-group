import { Eye, Loader2, RotateCcw, Wallet } from "lucide-react";

import { formatTradeNumber, tradeOrderAmount } from "../appFormatters";
import { tradeOrderStatusLabel, tradeSettlementHint } from "../tradeWorkspace";

export function TradeWorkspacePanel({ summary, loading, onRefresh, onOpenRecharge, onOpenOrderRecords }) {
  const stats = [
    { label: "当前余额", value: `${formatTradeNumber(summary.quotaBalance)} 点` },
    { label: "已用额度", value: `${formatTradeNumber(summary.usedQuota)} 点` },
    { label: "拼团订单", value: `${summary.groupOrders} 单` },
    { label: "待成团", value: `${summary.waitingGroupOrders} 单`, danger: summary.waitingGroupOrders > 0 }
  ];

  return (
    <section className="trade-workspace-panel">
      <div className="trade-workspace-head">
        <div>
          <strong>交易闭环看板</strong>
          <span>把额度账户、拼团订单、支付状态和额度流水放在同一个工作区核对</span>
        </div>
        <div>
          <button type="button" onClick={onOpenRecharge}>
            <Wallet size={15} />
            <span>购买额度</span>
          </button>
          <button type="button" onClick={onRefresh} disabled={loading}>
            {loading ? <Loader2 size={15} className="spin" /> : <RotateCcw size={15} />}
            <span>刷新订单</span>
          </button>
        </div>
      </div>
      <div className="trade-stat-grid">
        {stats.map((item) => (
          <div className={`trade-stat-card ${item.danger ? "danger" : ""}`} key={item.label}>
            <span>{item.label}</span>
            <strong>{item.value}</strong>
          </div>
        ))}
      </div>
      <div className="trade-consistency-list">
        {summary.consistencyHints.map((hint) => (
          <span key={hint}>{hint}</span>
        ))}
      </div>
      <div className="trade-workspace-grid">
        <div>
          <div className="trade-workspace-subhead">
            <strong>最近订单</strong>
            <span>{summary.totalOrders} 单</span>
          </div>
          {summary.recentOrders.length === 0 && <p className="trade-workspace-empty">暂无订单</p>}
          {summary.recentOrders.map((order, index) => {
            const status = order.orderStatus || order.status || order.payStatus;
            const settlementHint = tradeSettlementHint(order);
            return (
              <article className="trade-order-card" key={order.orderId || order.outTradeNo || index}>
                <div>
                  <strong>{order.productName || order.goodsName || order.productId || "额度订单"}</strong>
                  <span>{order.orderId || order.outTradeNo || "未生成订单号"}</span>
                </div>
                <em>{Number(order.marketType || 0) === 1 ? "拼团" : "直购"}</em>
                <b>{tradeOrderStatusLabel(status)}</b>
                <small className={`trade-settlement-hint ${settlementHint.tone}`} title={settlementHint.detail}>
                  {settlementHint.label}
                </small>
                <span>￥{tradeOrderAmount(order)}</span>
                <button type="button" className="trade-order-audit" onClick={() => onOpenOrderRecords?.()}>
                  <Eye size={14} />
                  <span>订单</span>
                </button>
              </article>
            );
          })}
        </div>
        <div>
          <div className="trade-workspace-subhead">
            <strong>最近额度流水</strong>
            <span>{summary.recentFlows.length} 条</span>
          </div>
          {summary.recentFlows.length === 0 && <p className="trade-workspace-empty">暂无流水</p>}
          {summary.recentFlows.map((flow, index) => (
            <article className="trade-flow-card" key={flow.flowId || flow.bizId || index}>
              <div>
                <strong>{flow.bizType || flow.flowType || "额度流水"}</strong>
                <span>{flow.bizId || flow.remark || flow.createTime || ""}</span>
              </div>
              <b>{formatTradeNumber(flow.quotaAmount)}</b>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}
