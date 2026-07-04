import { useCallback, useEffect, useState } from "react";
import { AlertTriangle, CreditCard, RefreshCw, RotateCcw } from "lucide-react";
import {
  grantQuotaByOrders,
  listTradeEventDeadLetters,
  normalizeApiMessage,
  queryPaymentGatewayStatus,
  replayTradeEventDeadLetter
} from "../services/api";

const CONSUME_STATUS_LABEL = {
  0: "待消费",
  1: "已消费",
  2: "重试中",
  3: "死信",
  4: "处理中"
};

function parseOrderIds(raw) {
  return String(raw || "")
    .split(/[\s,，;；]+/)
    .map((item) => item.trim())
    .filter(Boolean);
}

export default function TradeOpsManager({ authVersion = 0 }) {
  const [deadLetters, setDeadLetters] = useState([]);
  const [gatewayStatus, setGatewayStatus] = useState(null);
  const [orderIdsInput, setOrderIdsInput] = useState("");
  const [grantResult, setGrantResult] = useState(null);
  const [loadingDeadLetters, setLoadingDeadLetters] = useState(false);
  const [loadingGateway, setLoadingGateway] = useState(false);
  const [granting, setGranting] = useState(false);
  const [replayingId, setReplayingId] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  const loadDeadLetters = useCallback(async () => {
    setLoadingDeadLetters(true);
    setError("");
    try {
      const res = await listTradeEventDeadLetters(50);
      if (res?.code === "0000") {
        setDeadLetters(res.data || []);
      } else {
        setError(normalizeApiMessage(res, "加载死信失败"));
      }
    } catch (err) {
      setError(err?.message || "加载死信失败");
    } finally {
      setLoadingDeadLetters(false);
    }
  }, []);

  const loadGatewayStatus = useCallback(async () => {
    setLoadingGateway(true);
    setError("");
    try {
      const res = await queryPaymentGatewayStatus();
      if (res?.code === "0000") {
        setGatewayStatus(res.data || null);
      } else {
        setError(normalizeApiMessage(res, "加载支付网关状态失败"));
      }
    } catch (err) {
      setError(err?.message || "加载支付网关状态失败");
    } finally {
      setLoadingGateway(false);
    }
  }, []);

  useEffect(() => {
    loadDeadLetters();
    loadGatewayStatus();
  }, [authVersion, loadDeadLetters, loadGatewayStatus]);

  const handleReplay = async (eventId) => {
    if (!eventId || replayingId) return;
    setReplayingId(eventId);
    setMessage("");
    setError("");
    try {
      const res = await replayTradeEventDeadLetter(eventId);
      if (res?.code === "0000") {
        setMessage(`事件 ${eventId} 已提交重放`);
        await loadDeadLetters();
      } else {
        setError(normalizeApiMessage(res, "重放失败"));
      }
    } catch (err) {
      setError(err?.message || "重放失败");
    } finally {
      setReplayingId("");
    }
  };

  const handleGrantQuota = async (event) => {
    event.preventDefault();
    const orderIds = parseOrderIds(orderIdsInput);
    if (orderIds.length === 0) {
      setError("请输入至少一个订单号");
      return;
    }
    setGranting(true);
    setMessage("");
    setError("");
    setGrantResult(null);
    try {
      const res = await grantQuotaByOrders(orderIds);
      if (res?.code === "0000") {
        setGrantResult(res.data || null);
        setMessage(res.data?.message || "补发请求已提交");
      } else {
        setError(normalizeApiMessage(res, "补发额度失败"));
      }
    } catch (err) {
      setError(err?.message || "补发额度失败");
    } finally {
      setGranting(false);
    }
  };

  return (
    <div className="trade-ops-manager">
      <div className="admin-card">
        <div className="admin-card-header">
          <div className="admin-title-line"><CreditCard size={18} color="#ea580c" /><h3>支付网关状态</h3></div>
          <button className="admin-btn outline small" type="button" onClick={loadGatewayStatus} disabled={loadingGateway}>
            <RefreshCw size={14} className={loadingGateway ? "spin" : ""} />
            <span>刷新</span>
          </button>
        </div>
        <div className="admin-card-body">
          {gatewayStatus ? (
            <div className="trade-ops-kv-grid">
              <div><span>推荐渠道</span><strong>{gatewayStatus.recommendedChannel || "-"}</strong></div>
              <div><span>官方网关</span><strong>{gatewayStatus.officialGatewayReady ? "就绪" : "未就绪"}</strong></div>
              <div><span>支付宝沙箱</span><strong>{gatewayStatus.alipaySandboxReady ? "就绪" : "未就绪"}</strong></div>
              <div><span>说明</span><strong>{gatewayStatus.message || gatewayStatus.sandboxEvidence || "-"}</strong></div>
              {(gatewayStatus.channels || []).length > 0 && (
                <div className="trade-ops-channel-list">
                  {(gatewayStatus.channels || []).map((channel) => (
                    <div key={channel.payChannel} className="trade-ops-channel-item">
                      <strong>{channel.payChannel}</strong>
                      <span>{channel.configured ? "已配置" : "未配置"} · {channel.sandboxMode ? "沙箱" : "正式"} · {channel.readyItemCount}/{channel.requiredItemCount}</span>
                      {channel.message && <em>{channel.message}</em>}
                    </div>
                  ))}
                </div>
              )}
            </div>
          ) : (
            <p className="admin-desc">{loadingGateway ? "正在读取支付网关状态…" : "暂无网关状态"}</p>
          )}
        </div>
      </div>

      <div className="admin-card">
        <div className="admin-card-header">
          <div className="admin-title-line"><RotateCcw size={18} color="#7c3aed" /><h3>按订单补发额度</h3></div>
        </div>
        <div className="admin-card-body">
          <p className="admin-desc">仅对后端判定满足到账条件的订单发放额度，多个订单号可用逗号或换行分隔。</p>
          <form className="trade-ops-grant-form" onSubmit={handleGrantQuota}>
            <textarea
              value={orderIdsInput}
              onChange={(event) => setOrderIdsInput(event.target.value)}
              placeholder="例如：O202601010001&#10;O202601010002"
              rows={4}
            />
            <button className="admin-btn primary small" type="submit" disabled={granting}>
              {granting ? "提交中…" : "执行补发"}
            </button>
          </form>
          {grantResult && (
            <div className="trade-ops-result">
              <span>请求 {grantResult.requestedCount || 0} 单，成功处理 {grantResult.processedCount || 0} 单</span>
              {(grantResult.processedOrderIds || []).length > 0 && (
                <code>{grantResult.processedOrderIds.join(", ")}</code>
              )}
            </div>
          )}
        </div>
      </div>

      <div className="admin-card">
        <div className="admin-card-header">
          <div className="admin-title-line"><AlertTriangle size={18} color="#dc2626" /><h3>交易事件死信</h3></div>
          <button className="admin-btn outline small" type="button" onClick={loadDeadLetters} disabled={loadingDeadLetters}>
            <RefreshCw size={14} className={loadingDeadLetters ? "spin" : ""} />
            <span>刷新</span>
          </button>
        </div>
        <div className="admin-card-body">
          <div className="table-wrap">
            <table className="admin-table compact">
              <thead>
                <tr>
                  <th>事件 ID</th>
                  <th>订单号</th>
                  <th>类型</th>
                  <th>状态</th>
                  <th>重试</th>
                  <th>最后错误</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {deadLetters.map((item) => (
                  <tr key={item.eventId || item.id}>
                    <td className="mono">{item.eventId || "-"}</td>
                    <td className="mono">{item.orderId || "-"}</td>
                    <td>{item.eventType || item.bizType || "-"}</td>
                    <td>{CONSUME_STATUS_LABEL[item.consumeStatus] || item.consumeStatus || "-"}</td>
                    <td>{item.consumeCount ?? 0}</td>
                    <td className="trade-ops-error-cell">{item.lastError || "-"}</td>
                    <td>
                      <button
                        className="admin-btn outline small"
                        type="button"
                        disabled={replayingId === item.eventId}
                        onClick={() => handleReplay(item.eventId)}
                      >
                        {replayingId === item.eventId ? "重放中…" : "重放"}
                      </button>
                    </td>
                  </tr>
                ))}
                {deadLetters.length === 0 && (
                  <tr><td colSpan="7" className="empty-cell">{loadingDeadLetters ? "加载中…" : "暂无死信记录"}</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      {message && <div className="admin-tip">{message}</div>}
      {error && <div className="admin-error"><AlertTriangle size={16} /><span>{error}</span></div>}
    </div>
  );
}
