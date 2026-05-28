import { useCallback, useEffect, useState } from "react";
import { Bell, CheckCircle, CreditCard, ListOrdered, MessageCircle, QrCode, RefreshCw, Send } from "lucide-react";
import AdminAuthBar from "./AdminAuthBar";
import {
  activePayNotify,
  createLegacyPayOrder,
  createWeixinLoginQr,
  DEMO_USER_ID,
  queryUserOrderList,
  queryWeixinLoginStatus,
  sendWeixinTemplateMessage,
  simulateWeixinScan
} from "../services/api";

function PageShell({ title, subtitle, children }) {
  return (
    <main className="mall-page">
      <header className="mall-header">
        <div>
          <h1>{title}</h1>
          <p>{subtitle}</p>
        </div>
        <a href="/">返回导购</a>
      </header>
      {children}
    </main>
  );
}

function StatusLine({ value }) {
  if (!value) return null;
  return <div className="status-line">{value}</div>;
}

export function LoginPage() {
  const [qr, setQr] = useState(null);
  const [status, setStatus] = useState(null);
  const [message, setMessage] = useState("");

  const createQr = async () => {
    setMessage("正在生成扫码登录二维码...");
    try {
      const res = await createWeixinLoginQr();
      if (res.code === "0000") {
        setQr(res.data);
        setStatus({ status: res.data.status, sceneId: res.data.sceneId });
        setMessage(res.data.officialConfigured ? "请使用微信扫码登录。" : "当前是本地模拟模式，可点击模拟扫码。");
      } else {
        setMessage(res.info || "生成二维码失败");
      }
    } catch (error) {
      setMessage(error.message || "生成二维码失败");
    }
  };

  const refreshStatus = useCallback(async () => {
    if (!qr?.sceneId) return;
    try {
      const res = await queryWeixinLoginStatus(qr.sceneId);
      if (res.code === "0000") {
        setStatus(res.data);
        setMessage(res.data.status === "SCANNED" ? `已登录：${res.data.nickname || res.data.userId}` : `当前状态：${res.data.status}`);
      }
    } catch (error) {
      setMessage(error.message || "查询扫码状态失败");
    }
  }, [qr]);

  const simulateScan = async () => {
    if (!qr?.sceneId) return;
    try {
      const res = await simulateWeixinScan(qr.sceneId);
      if (res.code === "0000") {
        setStatus(res.data);
        setMessage(`模拟扫码成功：${res.data.nickname || res.data.userId}`);
      }
    } catch (error) {
      setMessage(error.message || "模拟扫码失败");
    }
  };

  useEffect(() => {
    let timer = null;
    if (qr?.sceneId && status?.status === "WAITING") {
      timer = window.setInterval(refreshStatus, 3000);
    }
    return () => {
      if (timer) window.clearInterval(timer);
    };
  }, [qr?.sceneId, status?.status, refreshStatus]);

  return (
    <PageShell title="微信扫码登录" subtitle="用于演示公众号二维码、扫码状态查询和本地模拟扫码。">
      <section className="mall-card login-card">
        <div className="qr-box">
          {qr?.qrCodeUrl ? (
            <img src={qr.qrCodeUrl} alt="微信扫码登录二维码" />
          ) : (
            <QrCode size={72} />
          )}
        </div>
        <div className="mall-form">
          <button className="admin-btn primary" onClick={createQr}>
            <QrCode size={16} /> 生成二维码
          </button>
          <button className="admin-btn outline" onClick={refreshStatus} disabled={!qr?.sceneId}>
            <RefreshCw size={16} /> 查询状态
          </button>
          <button className="admin-btn success" onClick={simulateScan} disabled={!qr?.sceneId}>
            <CheckCircle size={16} /> 模拟扫码
          </button>
          <dl className="kv-list">
            <dt>演示用户</dt><dd>{DEMO_USER_ID}</dd>
            <dt>场景值</dt><dd>{qr?.sceneId || "-"}</dd>
            <dt>登录状态</dt><dd>{status?.status || "-"}</dd>
            <dt>OpenID</dt><dd>{status?.openId || "-"}</dd>
          </dl>
          <StatusLine value={message} />
        </div>
      </section>
    </PageShell>
  );
}

export function OrderListPage() {
  const [orders, setOrders] = useState([]);
  const [message, setMessage] = useState("");

  const loadOrders = async () => {
    setMessage("正在读取订单...");
    try {
      const res = await queryUserOrderList(20);
      if (res.code === "0000") {
        setOrders(res.data?.orderList || []);
        setMessage("");
      } else {
        setMessage(res.info || "读取订单失败");
      }
    } catch (error) {
      setMessage(error.message || "读取订单失败");
    }
  };

  useEffect(() => {
    let active = true;
    queryUserOrderList(20).then((res) => {
      if (!active) return;
      if (res.code === "0000") {
        setOrders(res.data?.orderList || []);
      }
    }).catch((error) => {
      if (active) setMessage(error.message || "读取订单失败");
    });
    return () => {
      active = false;
    };
  }, []);

  return (
    <PageShell title="订单列表" subtitle="查看演示用户的单买和拼团交易记录。">
      <AdminAuthBar onSaved={loadOrders} />
      <StatusLine value={message} />
      <section className="mall-card">
        <div className="section-title">
          <ListOrdered size={18} />
          <h2>用户订单</h2>
          <button className="admin-btn outline" onClick={loadOrders}>刷新</button>
        </div>
        <div className="table-wrap">
          <table className="admin-table">
            <thead>
              <tr>
                <th>订单号</th>
                <th>商品</th>
                <th>类型</th>
                <th>应付</th>
                <th>状态</th>
                <th>支付链接</th>
              </tr>
            </thead>
            <tbody>
              {orders.map((order) => (
                <tr key={order.id || order.orderId}>
                  <td className="mono">{order.orderId}</td>
                  <td>{order.productName}</td>
                  <td>{order.marketType === 1 ? "拼团" : "单独购买"}</td>
                  <td>￥{order.payAmount || order.totalAmount}</td>
                  <td>{order.status}</td>
                  <td>{order.payUrl ? <a href={order.payUrl}>打开</a> : "-"}</td>
                </tr>
              ))}
              {orders.length === 0 && <tr><td colSpan="6" className="empty-cell">暂无订单</td></tr>}
            </tbody>
          </table>
        </div>
      </section>
    </PageShell>
  );
}

export function CheckoutPage() {
  const [form, setForm] = useState({
    productId: "G10001",
    decisionId: "",
    marketType: "1",
    activityId: "A10001",
    teamId: ""
  });
  const [message, setMessage] = useState("");
  const [payUrl, setPayUrl] = useState("");

  const updateForm = (key, value) => setForm((prev) => ({ ...prev, [key]: value }));

  const submit = async () => {
    setMessage("正在创建支付单...");
    setPayUrl("");
    try {
      const res = await createLegacyPayOrder(form);
      if (res.code === "0000") {
        setPayUrl(res.data || "");
        setMessage("支付单创建成功");
      } else {
        setMessage(res.info || "创建支付单失败");
      }
    } catch (error) {
      setMessage(error.message || "创建支付单失败");
    }
  };

  return (
    <PageShell title="结算演示" subtitle="保留旧 checkout.html 入口，用于演示商城支付单创建。">
      <AdminAuthBar />
      <section className="mall-card">
        <div className="section-title">
          <CreditCard size={18} />
          <h2>创建支付单</h2>
        </div>
        <div className="form-grid">
          <label>商品 ID<input value={form.productId} onChange={(event) => updateForm("productId", event.target.value)} /></label>
          <label>决策凭证<input value={form.decisionId} onChange={(event) => updateForm("decisionId", event.target.value)} placeholder="导购推荐后生成" /></label>
          <label>购买类型
            <select value={form.marketType} onChange={(event) => updateForm("marketType", event.target.value)}>
              <option value="1">拼团</option>
              <option value="0">单独购买</option>
            </select>
          </label>
          <label>活动 ID<input value={form.activityId} onChange={(event) => updateForm("activityId", event.target.value)} /></label>
          <label>团队 ID<input value={form.teamId} onChange={(event) => updateForm("teamId", event.target.value)} placeholder="参团时填写" /></label>
        </div>
        <div className="form-actions">
          <button className="admin-btn primary" onClick={submit}>创建支付单</button>
          {payUrl && <a className="pay-link" href={payUrl}>打开支付链接</a>}
        </div>
        <StatusLine value={message} />
      </section>
    </PageShell>
  );
}

export function CallbackTestPage() {
  const [orderId, setOrderId] = useState("");
  const [templateId, setTemplateId] = useState("demo_template_id");
  const [message, setMessage] = useState("");

  const notifyPay = async () => {
    if (!orderId.trim()) {
      setMessage("请输入订单号");
      return;
    }
    try {
      const res = await activePayNotify(orderId.trim());
      setMessage(res.code === "0000" ? `支付回调触发成功：${res.data}` : res.info);
    } catch (error) {
      setMessage(error.message || "支付回调触发失败");
    }
  };

  const sendTemplate = async () => {
    try {
      const res = await sendWeixinTemplateMessage({
        userId: DEMO_USER_ID,
        templateId,
        title: "拼团状态更新",
        remark: "请查看订单列表",
        targetUrl: window.location.origin + "/order-list"
      });
      setMessage(res.code === "0000" ? `模板消息处理成功：${res.data?.message || res.data?.mode}` : res.info);
    } catch (error) {
      setMessage(error.message || "模板消息发送失败");
    }
  };

  return (
    <PageShell title="回调测试" subtitle="用于演示支付主动通知和微信公众号模板消息。">
      <AdminAuthBar />
      <section className="mall-card">
        <div className="section-title">
          <Bell size={18} />
          <h2>支付回调</h2>
        </div>
        <div className="inline-form">
          <input value={orderId} onChange={(event) => setOrderId(event.target.value)} placeholder="订单号" />
          <button className="admin-btn primary" onClick={notifyPay}>触发支付通知</button>
        </div>
      </section>
      <section className="mall-card">
        <div className="section-title">
          <MessageCircle size={18} />
          <h2>模板消息</h2>
        </div>
        <div className="inline-form">
          <input value={templateId} onChange={(event) => setTemplateId(event.target.value)} placeholder="模板 ID" />
          <button className="admin-btn success" onClick={sendTemplate}>
            <Send size={16} /> 发送模板消息
          </button>
        </div>
      </section>
      <StatusLine value={message} />
    </PageShell>
  );
}
