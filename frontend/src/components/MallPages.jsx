import { useCallback, useEffect, useState } from "react";
import {
  Bell,
  CheckCircle,
  CreditCard,
  ExternalLink,
  Info,
  ListOrdered,
  MessageCircle,
  PackageSearch,
  QrCode,
  RefreshCw,
  RotateCcw,
  Search,
  Send,
  ShoppingBag,
  ShoppingCart,
  Trash2
} from "lucide-react";
import AdminAuthBar from "./AdminAuthBar";
import {
  activePayNotify,
  createLegacyPayOrder,
  createWeixinLoginQr,
  DEMO_USER_ID,
  queryProductCatalog,
  queryProductDetail,
  queryUserOrderList,
  queryWeixinLoginStatus,
  refundOrder,
  sendWeixinTemplateMessage,
  simulateWeixinScan,
  validateCart
} from "../services/api";

const CART_KEY = "agentGroupMallCart";

function formatMoney(value) {
  const number = Number(value || 0);
  return number.toFixed(2);
}

function readCart() {
  try {
    return JSON.parse(localStorage.getItem(CART_KEY) || "[]");
  } catch {
    return [];
  }
}

function writeCart(items) {
  localStorage.setItem(CART_KEY, JSON.stringify(items));
}

function checkoutUrl(product, marketType = 1) {
  const params = new URLSearchParams();
  params.set("productId", product.goodsId);
  params.set("marketType", String(marketType));
  if (product.activityId) params.set("activityId", product.activityId);
  return `/checkout?${params.toString()}`;
}

function addProductToCart(product, marketType = 1) {
  const existed = readCart().find(existing => existing.goodsId === product.goodsId && existing.marketType === marketType);
  const item = {
    goodsId: product.goodsId,
    goodsName: product.goodsName,
    imageUrl: product.imageUrl,
    originPrice: product.originPrice,
    groupPrice: product.groupPrice,
    activityId: product.activityId,
    teamSize: product.teamSize,
    marketType,
    quantity: existed ? Number(existed.quantity || 1) + 1 : 1
  };
  const next = [item, ...readCart().filter(existing => !(existing.goodsId === product.goodsId && existing.marketType === marketType))];
  writeCart(next.slice(0, 20));
}

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

function MallProductCard({ product, onAddCart }) {
  return (
    <article className="mall-product-card">
      <div className="mall-product-image">
        {product.imageUrl ? <img src={product.imageUrl} alt={product.goodsName} /> : <ShoppingBag size={44} />}
      </div>
      <div className="mall-product-body">
        <div className="mall-product-title">{product.goodsName}</div>
        <p>{product.specSummary || product.recommendReason || "商品资料待运营补全"}</p>
        <div className="mall-price-row">
          <strong>¥{formatMoney(product.groupPrice || product.originPrice)}</strong>
          {product.originPrice && <span>¥{formatMoney(product.originPrice)}</span>}
        </div>
        <div className="mall-tag-row">
          <span>{product.groupBuyAvailable ? `${product.teamSize || 2} 人拼团` : "直接购买"}</span>
          {product.remainingSeconds > 0 && <span>剩余 {Math.ceil(product.remainingSeconds / 60)} 分钟</span>}
        </div>
      </div>
      <div className="mall-product-actions">
        <a className="admin-btn outline" href={`/products/${product.goodsId}`}>
          <Info size={16} /> 详情
        </a>
        <button className="admin-btn outline" onClick={() => onAddCart(product)}>
          <ShoppingCart size={16} /> 加购
        </button>
        <a className="admin-btn primary" href={checkoutUrl(product, product.groupBuyAvailable ? 1 : 0)}>
          <CreditCard size={16} /> 结算
        </a>
      </div>
    </article>
  );
}

export function ProductListPage() {
  const [keyword, setKeyword] = useState("");
  const [products, setProducts] = useState([]);
  const [message, setMessage] = useState("");

  const loadProducts = useCallback(async (searchKeyword = "") => {
    setMessage("正在读取商品...");
    try {
      const res = await queryProductCatalog(searchKeyword, 20);
      if (res.code === "0000") {
        setProducts(res.data?.products || []);
        setMessage("");
      } else {
        setMessage(res.info || "读取商品失败");
      }
    } catch (error) {
      setMessage(error.message || "读取商品失败");
    }
  }, []);

  const handleAddCart = (product) => {
    addProductToCart(product, product.groupBuyAvailable ? 1 : 0);
    setMessage(`已加入购物车：${product.goodsName}`);
  };

  useEffect(() => {
    loadProducts("");
  }, [loadProducts]);

  return (
    <PageShell title="商品商城" subtitle="商品列表、详情、购物车和结算入口，用于演示完整商城购买链路。">
      <section className="mall-card">
        <div className="mall-search-bar">
          <Search size={18} />
          <input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="搜索商品、用途或配置" />
          <button className="admin-btn primary" onClick={() => loadProducts(keyword)}>搜索</button>
          <a className="admin-btn outline" href="/cart">
            <ShoppingCart size={16} /> 购物车
          </a>
        </div>
      </section>
      <StatusLine value={message} />
      <section className="mall-product-grid">
        {products.map((product) => (
          <MallProductCard key={product.goodsId} product={product} onAddCart={handleAddCart} />
        ))}
        {products.length === 0 && (
          <div className="mall-card empty-cell">
            <PackageSearch size={28} />
            <div>暂无商品</div>
          </div>
        )}
      </section>
    </PageShell>
  );
}

export function ProductDetailPage() {
  const goodsId = decodeURIComponent(window.location.pathname.split("/").filter(Boolean).pop() || "");
  const [product, setProduct] = useState(null);
  const [message, setMessage] = useState("");

  useEffect(() => {
    let active = true;
    setMessage("正在读取商品详情...");
    queryProductDetail(goodsId).then((res) => {
      if (!active) return;
      if (res.code === "0000") {
        setProduct(res.data);
        setMessage("");
      } else {
        setMessage(res.info || "读取商品详情失败");
      }
    }).catch((error) => {
      if (active) setMessage(error.message || "读取商品详情失败");
    });
    return () => {
      active = false;
    };
  }, [goodsId]);

  const addCart = () => {
    if (!product) return;
    addProductToCart(product, product.groupBuyAvailable ? 1 : 0);
    setMessage(`已加入购物车：${product.goodsName}`);
  };

  return (
    <PageShell title="商品详情" subtitle="展示商品资料、拼团活动、适用边界和售后政策。">
      <StatusLine value={message} />
      {product && (
        <section className="mall-card product-detail-panel">
          <div className="mall-product-image large">
            {product.imageUrl ? <img src={product.imageUrl} alt={product.goodsName} /> : <ShoppingBag size={72} />}
          </div>
          <div>
            <h2>{product.goodsName}</h2>
            <div className="mall-price-row detail">
              <strong>¥{formatMoney(product.groupPrice || product.originPrice)}</strong>
              {product.originPrice && <span>¥{formatMoney(product.originPrice)}</span>}
            </div>
            <dl className="kv-list detail">
              <dt>商品编号</dt><dd>{product.goodsId}</dd>
              <dt>活动编号</dt><dd>{product.activityId || "-"}</dd>
              <dt>拼团状态</dt><dd>{product.groupBuyAvailable ? "可拼团" : product.marketMessage || "不可拼团"}</dd>
              <dt>成团人数</dt><dd>{product.teamSize || "-"}</dd>
              <dt>规格说明</dt><dd>{product.specSummary || "-"}</dd>
              <dt>推荐理由</dt><dd>{product.recommendReason || "-"}</dd>
              <dt>售后政策</dt><dd>{product.afterSalePolicy || "-"}</dd>
              <dt>不适合</dt><dd>{product.notSuitableFor || "-"}</dd>
            </dl>
            <div className="form-actions">
              <button className="admin-btn outline" onClick={addCart}>
                <ShoppingCart size={16} /> 加入购物车
              </button>
              <a className="admin-btn outline" href={checkoutUrl(product, 0)}>直接购买</a>
              <a className="admin-btn primary" href={checkoutUrl(product, product.groupBuyAvailable ? 1 : 0)}>
                拼团结算
              </a>
            </div>
          </div>
        </section>
      )}
    </PageShell>
  );
}

export function CartPage() {
  const [items, setItems] = useState(readCart());
  const [message, setMessage] = useState("");
  const [validation, setValidation] = useState(null);
  const [validating, setValidating] = useState(false);
  const total = items.reduce((sum, item) => sum + Number(item.groupPrice || item.originPrice || 0) * Number(item.quantity || 1), 0);

  const saveItems = (next) => {
    setItems(next);
    writeCart(next);
  };

  const removeItem = (goodsId) => {
    const next = items.filter(item => item.goodsId !== goodsId);
    saveItems(next);
  };

  const clearCart = () => {
    saveItems([]);
    setValidation(null);
    setMessage("购物车已清空");
  };

  const updateQuantity = (goodsId, value) => {
    const quantity = Math.max(1, Number(value || 1));
    saveItems(items.map(item => item.goodsId === goodsId ? { ...item, quantity } : item));
    setValidation(null);
  };

  const findValidation = (item) => validation?.items?.find(line => line.goodsId === item.goodsId && line.marketType === item.marketType);

  const handleValidateCart = async () => {
    if (items.length === 0) {
      setMessage("购物车为空");
      return null;
    }
    setValidating(true);
    setMessage("正在校验库存和活动...");
    try {
      const res = await validateCart(items);
      if (res.code === "0000") {
        setValidation(res.data);
        setMessage(res.data?.pass ? "购物车校验通过" : "部分商品库存或活动不可用");
        return res.data;
      }
      setMessage(res.info || "购物车校验失败");
    } catch (error) {
      setMessage(error.message || "购物车校验失败");
    } finally {
      setValidating(false);
    }
    return null;
  };

  const handleCheckout = async (item) => {
    setValidating(true);
    setMessage("正在校验当前商品...");
    try {
      const res = await validateCart([item]);
      if (res.code === "0000" && res.data?.pass) {
        window.location.href = checkoutUrl(item, item.marketType);
        return;
      }
      setValidation(res.data || null);
      setMessage(res.data?.items?.[0]?.message || res.info || "当前商品暂不可结算");
    } catch (error) {
      setMessage(error.message || "当前商品暂不可结算");
    } finally {
      setValidating(false);
    }
  };

  return (
    <PageShell title="购物车" subtitle="用于演示商城加购、库存校验、金额汇总和跳转结算。">
      <StatusLine value={message} />
      <section className="mall-card">
        <div className="section-title">
          <ShoppingCart size={18} />
          <h2>已选商品</h2>
          <button className="admin-btn outline" onClick={handleValidateCart} disabled={items.length === 0 || validating}>
            <CheckCircle size={16} /> 校验
          </button>
          <button className="admin-btn outline" onClick={clearCart} disabled={items.length === 0}>
            <Trash2 size={16} /> 清空
          </button>
        </div>
        <div className="cart-list">
          {items.map((item) => (
            <div className="cart-row" key={item.goodsId}>
              <div className="mall-product-image small">
                {item.imageUrl ? <img src={item.imageUrl} alt={item.goodsName} /> : <ShoppingBag size={24} />}
              </div>
              <div>
                <strong>{item.goodsName}</strong>
                <span>{item.marketType === 1 ? "拼团购买" : "直接购买"} · ¥{formatMoney(item.groupPrice || item.originPrice)}</span>
                {findValidation(item) && (
                  <span className={findValidation(item)?.pass ? "cart-check ok" : "cart-check error"}>
                    {findValidation(item)?.message}
                  </span>
                )}
              </div>
              <input
                className="qty-input"
                type="number"
                min="1"
                value={item.quantity || 1}
                onChange={(event) => updateQuantity(item.goodsId, event.target.value)}
              />
              <button className="admin-btn primary" onClick={() => handleCheckout(item)} disabled={validating}>结算</button>
              <button className="admin-btn outline" onClick={() => removeItem(item.goodsId)}>
                <Trash2 size={16} />
              </button>
            </div>
          ))}
          {items.length === 0 && <div className="empty-cell">购物车为空</div>}
        </div>
        <div className="cart-summary">
          <span>合计</span>
          <strong>¥{formatMoney(validation?.totalAmount || total)}</strong>
        </div>
      </section>
    </PageShell>
  );
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
  const [refundLoading, setRefundLoading] = useState("");
  const [filters, setFilters] = useState({
    marketType: "",
    orderStatus: "",
    keyword: ""
  });

  const loadOrders = useCallback(async () => {
    setMessage("正在读取订单...");
    try {
      const res = await queryUserOrderList({
        pageSize: 20,
        marketType: filters.marketType,
        orderStatus: filters.orderStatus,
        keyword: filters.keyword.trim()
      });
      if (res.code === "0000") {
        setOrders(res.data?.orderList || []);
        setMessage("");
      } else {
        setMessage(res.info || "读取订单失败");
      }
    } catch (error) {
      setMessage(error.message || "读取订单失败");
    }
  }, [filters]);

  useEffect(() => {
    let active = true;
    queryUserOrderList({ pageSize: 20 }).then((res) => {
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

  const applyRefund = async (orderId) => {
    setRefundLoading(orderId);
    setMessage("正在提交售后退款...");
    try {
      const res = await refundOrder(orderId);
      const nextMessage = res.code === "0000" && res.data?.success
        ? "售后退款处理成功"
        : (res.data?.message || res.info || "售后退款处理失败");
      await loadOrders();
      setMessage(nextMessage);
    } catch (error) {
      setMessage(error.message || "售后退款处理失败");
    } finally {
      setRefundLoading("");
    }
  };

  const updateFilter = (key, value) => setFilters(prev => ({ ...prev, [key]: value }));

  return (
    <PageShell title="订单列表" subtitle="查看演示用户的单买、拼团、支付和退款交易记录。">
      <AdminAuthBar onSaved={loadOrders} />
      <StatusLine value={message} />
      <section className="mall-card">
        <div className="section-title">
          <ListOrdered size={18} />
          <h2>用户订单</h2>
          <button className="admin-btn outline" onClick={loadOrders}>刷新</button>
        </div>
        <div className="filter-bar">
          <select value={filters.marketType} onChange={(event) => updateFilter("marketType", event.target.value)}>
            <option value="">全部类型</option>
            <option value="1">拼团</option>
            <option value="0">单独购买</option>
          </select>
          <select value={filters.orderStatus} onChange={(event) => updateFilter("orderStatus", event.target.value)}>
            <option value="">全部状态</option>
            <option value="CREATE">已创建</option>
            <option value="PAY_WAIT">待支付</option>
            <option value="PAY_SUCCESS">支付成功</option>
            <option value="GROUP_SETTLED">已成团</option>
            <option value="DEAL_DONE">交易完成</option>
            <option value="WAIT_REFUND">退款中</option>
            <option value="REFUNDED">已退款</option>
            <option value="CLOSED">已关闭</option>
          </select>
          <input
            value={filters.keyword}
            onChange={(event) => updateFilter("keyword", event.target.value)}
            placeholder="搜索订单号或商品"
          />
          <button className="admin-btn primary" onClick={loadOrders}>查询</button>
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
                <th>售后</th>
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
                  <td>{order.payUrl ? <a href={order.payUrl} target="_blank" rel="noreferrer">打开</a> : "-"}</td>
                  <td>
                    <button className="admin-btn outline" onClick={() => applyRefund(order.orderId)} disabled={refundLoading === order.orderId}>
                      <RotateCcw size={16} /> 退款
                    </button>
                  </td>
                </tr>
              ))}
              {orders.length === 0 && <tr><td colSpan="7" className="empty-cell">暂无订单</td></tr>}
            </tbody>
          </table>
        </div>
      </section>
    </PageShell>
  );
}

export function CheckoutPage() {
  const params = new URLSearchParams(window.location.search);
  const [form, setForm] = useState({
    productId: params.get("productId") || "G10001",
    decisionId: params.get("decisionId") || "",
    marketType: params.get("marketType") || "1",
    activityId: params.get("activityId") || "A10001",
    teamId: params.get("teamId") || ""
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
          {payUrl && <a className="pay-link" href={payUrl} target="_blank" rel="noreferrer">
            <ExternalLink size={16} /> 打开支付链接
          </a>}
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
