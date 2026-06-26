import { Fragment, useEffect, useMemo, useState } from "react";
import { AlertTriangle, CreditCard, Boxes, RotateCcw, RefreshCw, Sparkles } from "lucide-react";
import AdminSidebar from "./AdminSidebar";
import AgentConfigManager from "./AgentConfigManager";
import GroupBuyActivityManager from "./GroupBuyActivityManager";
import {
  queryAdminOrderList,
  queryOpsDashboard,
  queryRefundOrderList,
  queryTradeConsistency
} from "../services/api";
import { applyTheme, getStoredTheme, nextTheme } from "../theme";

async function fetchAdminData() {
  const [ordersResult, refundsResult, opsResult, auditResult] = await Promise.allSettled([
    queryAdminOrderList({ pageSize: 20 }),
    queryRefundOrderList({ userId: null, pageSize: 20 }),
    queryOpsDashboard(),
    queryTradeConsistency({ pageSize: 20 })
  ]);
  return { ordersResult, refundsResult, opsResult, auditResult };
}

function resultError(result) {
  return result.status === "fulfilled" ? "" : result.reason?.message || "请求失败";
}

const ORDER_STATUS_LABEL = {
  CREATE: "已创建",
  PAY_WAIT: "待支付",
  PAY_SUCCESS: "已支付",
  GROUP_SETTLED: "已成团",
  DEAL_DONE: "交易完成",
  CLOSED: "已关闭",
  WAIT_REFUND: "待退款",
  REFUNDED: "已退款"
};

const ORDER_STATUS_BADGE = {
  CREATE: "badge-gray",
  PAY_WAIT: "badge-yellow",
  PAY_SUCCESS: "badge-blue",
  GROUP_SETTLED: "badge-purple",
  DEAL_DONE: "badge-green",
  CLOSED: "badge-gray",
  WAIT_REFUND: "badge-orange",
  REFUNDED: "badge-gray"
};

const REFUND_STATUS_LABEL = {
  PROCESSING: "处理中",
  FAILED: "退款失败",
  CLOSED: "已关闭",
  SUCCESS: "退款成功"
};

const REFUND_STATUS_BADGE = {
  PROCESSING: "badge-yellow",
  FAILED: "badge-orange",
  CLOSED: "badge-gray",
  SUCCESS: "badge-green"
};

function auditBadgeClass(conclusion) {
  switch (conclusion) {
    case "QUOTA_GRANTED_CONSISTENT":
      return "badge-green";
    case "WAIT_GROUP_SETTLEMENT":
      return "badge-yellow";
    case "QUOTA_GRANT_REQUIRED":
    case "REFUND_ROLLBACK_REQUIRED":
    case "TRADE_STATE_CONFLICT":
      return "badge-orange";
    default:
      return "badge-gray";
  }
}

const MENU_TITLES = {
  overview: "总览",
  activity: "拼团活动管理",
  channel: "渠道与库存",
  agentConfig: "模型与提示词配置",
  llmConfig: "模型配置",
  skills: "技能 Skills",
  mcp: "MCP 服务",
  order: "交易订单与一致性核查",
  refund: "售后退款"
};

export default function AdminDashboard() {
  const [theme, setTheme] = useState(() => getStoredTheme());
  const [current, setCurrent] = useState("overview");
  const [orders, setOrders] = useState([]);
  const [refunds, setRefunds] = useState([]);
  const [tradeAuditItems, setTradeAuditItems] = useState([]);
  const [opsDashboard, setOpsDashboard] = useState({ channels: [], crowdTags: [], stocks: [], notifyTasks: [] });
  const [expandedOrder, setExpandedOrder] = useState(null);
  const [errorMsg, setErrorMsg] = useState("");
  const [authVersion, setAuthVersion] = useState(0);

  useEffect(() => {
    applyTheme(theme);
  }, [theme]);

  const toggleTheme = () => setTheme((prev) => applyTheme(nextTheme(prev)));

  const applyResults = ({ ordersResult, refundsResult, opsResult, auditResult }) => {
    if (ordersResult.status === "fulfilled" && ordersResult.value.code === "0000") {
      setOrders(ordersResult.value.data?.orderList || []);
    }
    if (refundsResult.status === "fulfilled" && refundsResult.value.code === "0000") {
      setRefunds(refundsResult.value.data?.refundList || []);
    }
    if (opsResult.status === "fulfilled" && opsResult.value.code === "0000") {
      setOpsDashboard(opsResult.value.data || { channels: [], crowdTags: [], stocks: [], notifyTasks: [] });
    }
    if (auditResult.status === "fulfilled" && auditResult.value.code === "0000") {
      setTradeAuditItems(auditResult.value.data?.items || []);
    }
    const errors = [resultError(ordersResult), resultError(refundsResult), resultError(opsResult), resultError(auditResult)].filter(Boolean);
    if (errors.length > 0) {
      setErrorMsg([...new Set(errors)].join("；"));
    }
  };

  const loadData = async () => {
    setErrorMsg("");
    applyResults(await fetchAdminData());
  };

  const handleAuthChanged = async () => {
    setAuthVersion((v) => v + 1);
    await loadData();
  };

  useEffect(() => {
    let active = true;
    fetchAdminData().then((results) => {
      if (active) applyResults(results);
    });
    return () => { active = false; };
  }, []);

  const auditByOrderId = useMemo(() => {
    const map = {};
    (tradeAuditItems || []).forEach((item) => {
      if (item.orderId) {
        (map[item.orderId] = map[item.orderId] || []).push(item);
      }
    });
    return map;
  }, [tradeAuditItems]);

  const statCards = [
    { label: "渠道配置", value: (opsDashboard.channels || []).length, icon: CreditCard },
    { label: "订单数", value: orders.length, icon: CreditCard },
    { label: "退款单", value: refunds.length, icon: RotateCcw },
    { label: "活动数", value: (opsDashboard.activities || []).length, icon: Boxes }
  ];

  return (
    <div className="admin-shell" data-theme={theme}>
      <AdminSidebar
        current={current}
        onSelect={setCurrent}
        theme={theme}
        onToggleTheme={toggleTheme}
        onAuthChanged={handleAuthChanged}
      />

      <div className="admin-main">
        <header className="admin-topbar">
          <div className="admin-topbar-title">{MENU_TITLES[current] || "管理后台"}</div>
          <div className="admin-topbar-actions">
            <button className="admin-icon-btn" type="button" onClick={loadData} title="刷新" aria-label="刷新">
              <RefreshCw size={16} />
            </button>
            <a className="admin-exit" href="/">
              返回前台
            </a>
          </div>
        </header>

        {errorMsg && (
          <div className="admin-error">
            <AlertTriangle size={16} />
            <span>{errorMsg}</span>
          </div>
        )}

        <div className="admin-content">
          {current === "overview" && (
            <div className="admin-overview">
              <div className="admin-stat-grid">
                {statCards.map((card) => (
                  <div key={card.label} className="admin-stat-card">
                    <div className="admin-stat-label">{card.label}</div>
                    <div className="admin-stat-value">{card.value}</div>
                  </div>
                ))}
              </div>
              <div className="admin-card">
                <div className="admin-card-header">
                  <div className="admin-title-line"><Sparkles size={18} /><h3>快速入口</h3></div>
                </div>
                <div className="admin-card-body">
                  <p className="admin-desc">从左侧菜单进入各功能模块：拼团活动可新建编辑上下架，智能体配置可管理默认模型与提示词，交易订单可展开查看一致性核查结论。</p>
                </div>
              </div>
            </div>
          )}

          {current === "activity" && (
            <div className="admin-card">
              <div className="admin-card-body">
                <GroupBuyActivityManager authVersion={authVersion} />
              </div>
            </div>
          )}

          {current === "channel" && (
            <div className="admin-card">
              <div className="admin-card-body">
                <div className="ops-mini-grid">
                  <div className="ops-block">
                    <div className="admin-title-line ops-title"><CreditCard size={16} /><h4>渠道额度包</h4></div>
                    <div className="table-wrap compact">
                      <table className="admin-table compact">
                        <thead><tr><th>来源</th><th>渠道</th><th>额度包</th><th>活动</th></tr></thead>
                        <tbody>
                          {(opsDashboard.channels || []).map((item) => (
                            <tr key={`${item.source}-${item.channel}-${item.goodsId}`}>
                              <td>{item.source}</td>
                              <td>{item.channel}</td>
                              <td>{item.goodsName || item.goodsId}</td>
                              <td className="mono">{item.activityId}</td>
                            </tr>
                          ))}
                          {(opsDashboard.channels || []).length === 0 && <tr><td colSpan="4" className="empty-cell">暂无渠道配置</td></tr>}
                        </tbody>
                      </table>
                    </div>
                  </div>
                  <div className="ops-block">
                    <div className="admin-title-line ops-title"><Boxes size={16} /><h4>库存水位</h4></div>
                    <div className="table-wrap compact">
                      <table className="admin-table compact">
                        <thead><tr><th>活动</th><th>额度包</th><th>可用</th><th>锁定</th><th>已付</th></tr></thead>
                        <tbody>
                          {(opsDashboard.stocks || []).map((item) => (
                            <tr key={`${item.activityId}-${item.goodsId}`}>
                              <td className="mono">{item.activityId}</td>
                              <td className="mono">{item.goodsId}</td>
                              <td>{item.availableStock || 0}/{item.totalStock || 0}</td>
                              <td>{item.lockedStock || 0}</td>
                              <td>{item.paidStock || 0}</td>
                            </tr>
                          ))}
                          {(opsDashboard.stocks || []).length === 0 && <tr><td colSpan="5" className="empty-cell">暂无库存配置</td></tr>}
                        </tbody>
                      </table>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          )}

          {(current === "llmConfig" || current === "skills" || current === "mcp") && (
            <AgentConfigManager section={current} authVersion={authVersion} />
          )}

          {current === "order" && (
            <div className="admin-card">
              <div className="admin-card-header">
                <div className="admin-title-line"><CreditCard size={18} color="#ea580c" /><h3>交易订单与一致性核查</h3></div>
              </div>
              <div className="admin-card-body">
                <p className="admin-desc">订单为主，展开可查看该订单的支付、退款与额度流水一致性结论。</p>
                <div className="table-wrap">
                  <table className="admin-table">
                    <thead>
                      <tr><th>订单号</th><th>额度包</th><th>类型</th><th>金额</th><th>状态</th><th>创建时间</th><th>核查</th></tr>
                    </thead>
                    <tbody>
                      {orders.map((order) => {
                        const audits = auditByOrderId[order.orderId] || [];
                        const hasAudit = audits.length > 0;
                        return (
                          <Fragment key={order.id || order.orderId}>
                            <tr className={hasAudit ? "row-expandable" : ""} onClick={hasAudit ? () => setExpandedOrder(expandedOrder === order.orderId ? null : order.orderId) : undefined}>
                              <td className="mono">{order.orderId}</td>
                              <td>{order.productName || order.goodsId || "-"}</td>
                              <td><span className={`badge ${order.marketType === 1 ? "badge-purple" : "badge-gray"}`}>{order.marketType === 1 ? "拼团" : "单独购买"}</span></td>
                              <td>￥{order.payAmount || order.totalAmount}</td>
                              <td><span className={`badge ${ORDER_STATUS_BADGE[order.status] || "badge-gray"}`}>{order.displayStatus || ORDER_STATUS_LABEL[order.status] || order.status}</span></td>
                              <td>{order.orderTime ? order.orderTime.replace("T", " ") : ""}</td>
                              <td>
                                {hasAudit ? (
                                  <button className="admin-btn outline small" onClick={(e) => { e.stopPropagation(); setExpandedOrder(expandedOrder === order.orderId ? null : order.orderId); }}>
                                    {expandedOrder === order.orderId ? "收起" : `查看(${audits.length})`}
                                  </button>
                                ) : <span className="dim">-</span>}
                              </td>
                            </tr>
                            {hasAudit && expandedOrder === order.orderId && (
                              <tr className="row-detail">
                                <td colSpan={7}>
                                  <div className="audit-detail">
                                    {audits.map((item, index) => (
                                      <div key={`${order.orderId}-audit-${index}`} className="audit-detail-item">
                                        <span className={`badge ${auditBadgeClass(item.conclusion)}`}>{item.settlementLabel || item.conclusion || "-"}</span>
                                        <span className="audit-detail-text">{item.settlementDetail || item.message || "-"}</span>
                                        <span className="audit-tags">
                                          <span className={`badge ${item.quotaGrantAllowed ? "badge-green" : "badge-gray"}`}>{item.quotaGrantAllowed ? "可发额度" : "不可发额度"}</span>
                                          {item.refundRollbackRequired && <span className="badge badge-orange">需回滚</span>}
                                        </span>
                                      </div>
                                    ))}
                                  </div>
                                </td>
                              </tr>
                            )}
                          </Fragment>
                        );
                      })}
                      {orders.length === 0 && <tr><td colSpan="7" className="empty-cell">暂无真实订单数据，请在前台发起购买</td></tr>}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          )}

          {current === "refund" && (
            <div className="admin-card">
              <div className="admin-card-header">
                <div className="admin-title-line"><RotateCcw size={18} color="#7c3aed" /><h3>售后退款</h3></div>
              </div>
              <div className="admin-card-body">
                <div className="table-wrap">
                  <table className="admin-table">
                    <thead><tr><th>退款单号</th><th>订单号</th><th>金额</th><th>状态</th><th>原因</th><th>创建时间</th></tr></thead>
                    <tbody>
                      {refunds.map((refund) => (
                        <tr key={refund.id || refund.refundId}>
                          <td className="mono">{refund.refundId}</td>
                          <td className="mono">{refund.orderId}</td>
                          <td>￥{refund.refundAmount || 0}</td>
                          <td><span className={`badge ${REFUND_STATUS_BADGE[refund.refundStatus] || "badge-gray"}`}>{REFUND_STATUS_LABEL[refund.refundStatus] || refund.refundStatus}</span></td>
                          <td>{refund.refundReason || "-"}</td>
                          <td>{refund.createTime ? refund.createTime.replace("T", " ") : ""}</td>
                        </tr>
                      ))}
                      {refunds.length === 0 && <tr><td colSpan="6" className="empty-cell">暂无退款单</td></tr>}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          )}

        </div>
      </div>
    </div>
  );
}
