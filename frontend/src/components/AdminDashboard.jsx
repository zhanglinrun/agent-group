import { useEffect, useState } from "react";
import { Activity, AlertTriangle, Bell, Boxes, CreditCard, Database, LogOut, PlayCircle, RefreshCw, RotateCcw, Save, Settings, ShieldCheck, Tags, Upload } from "lucide-react";
import AdminAuthBar from "./AdminAuthBar";
import ThemeToggle from "./ThemeToggle";
import {
  compensateKnowledgeVector,
  downloadPaymentBill,
  getKnowledgeDocumentFullContent,
  getKnowledgeDocuments,
  getLatestAgentEvaluation,
  normalizeApiMessage,
  queryAdminOrderList,
  queryOpsDashboard,
  queryOperationalRules,
  queryPaymentErrorMap,
  queryPaymentRefund,
  queryRefundOrderList,
  queryTradeConsistency,
  rebuildKnowledgeVector,
  refreshPaymentCertificate,
  runAgentEvaluation,
  updateOperationalRule,
  uploadKnowledgeDocument
} from "../services/api";
import { applyTheme, getStoredTheme, nextTheme } from "../theme";

async function fetchAdminData() {
  const [docsResult, evalResult, ordersResult, refundsResult, rulesResult, opsResult, auditResult] = await Promise.allSettled([
    getKnowledgeDocuments(),
    getLatestAgentEvaluation(),
    queryAdminOrderList({ pageSize: 20 }),
    queryRefundOrderList({ userId: null, pageSize: 20 }),
    queryOperationalRules(),
    queryOpsDashboard(),
    queryTradeConsistency({ pageSize: 20 })
  ]);

  return {
    docsResult,
    evalResult,
    ordersResult,
    refundsResult,
    rulesResult,
    opsResult,
    auditResult
  };
}

function resultError(result) {
  if (result.status === "fulfilled") {
    return "";
  }
  return result.reason?.message || "请求失败";
}

function formatRate(value) {
  if (value === null || value === undefined) {
    return "N/A";
  }
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return "N/A";
  }
  return `${(numeric > 1 ? numeric : numeric * 100).toFixed(1)}%`;
}

function formatDateTime(value) {
  const text = String(value || "");
  return text ? text.replace("T", " ").slice(0, 19) : "-";
}

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

export default function AdminDashboard() {
  const [theme, setTheme] = useState(() => getStoredTheme());
  const [loadingMsg, setLoadingMsg] = useState("");
  const [documents, setDocuments] = useState([]);
  const [knowledgeEvidence, setKnowledgeEvidence] = useState(null);
  const [evaluation, setEvaluation] = useState(null);
  const [orders, setOrders] = useState([]);
  const [refunds, setRefunds] = useState([]);
  const [tradeAuditItems, setTradeAuditItems] = useState([]);
  const [rules, setRules] = useState([]);
  const [opsDashboard, setOpsDashboard] = useState({ activities: [], channels: [], crowdTags: [], stocks: [], notifyTasks: [] });
  const [paymentOps, setPaymentOps] = useState({ payChannel: "ALIPAY", billDate: "", refundOrderId: "", gatewayCode: "SYSTEMERROR" });
  const [paymentOpsResult, setPaymentOpsResult] = useState(null);
  const [errorMsg, setErrorMsg] = useState("");

  useEffect(() => {
    applyTheme(theme);
  }, [theme]);

  const toggleTheme = () => {
    setTheme((prev) => applyTheme(nextTheme(prev)));
  };

  const loadData = async () => {
    setErrorMsg("");
    const { docsResult, evalResult, ordersResult, refundsResult, rulesResult, opsResult, auditResult } = await fetchAdminData();

    if (docsResult.status === "fulfilled" && docsResult.value.code === "0000") {
      setDocuments(docsResult.value.data || []);
    }
    if (evalResult.status === "fulfilled" && evalResult.value.code === "0000") {
      setEvaluation(evalResult.value.data);
    }
    if (ordersResult.status === "fulfilled" && ordersResult.value.code === "0000") {
      setOrders(ordersResult.value.data?.orderList || []);
    }
    if (refundsResult.status === "fulfilled" && refundsResult.value.code === "0000") {
      setRefunds(refundsResult.value.data?.refundList || []);
    }
    if (rulesResult.status === "fulfilled" && rulesResult.value.code === "0000") {
      setRules(rulesResult.value.data || []);
    }
    if (opsResult.status === "fulfilled" && opsResult.value.code === "0000") {
      setOpsDashboard(opsResult.value.data || { activities: [], channels: [], crowdTags: [], stocks: [], notifyTasks: [] });
    }
    if (auditResult.status === "fulfilled" && auditResult.value.code === "0000") {
      setTradeAuditItems(auditResult.value.data?.items || []);
    }

    const errors = [resultError(docsResult), resultError(evalResult), resultError(ordersResult), resultError(refundsResult), resultError(rulesResult), resultError(opsResult), resultError(auditResult)].filter(Boolean);
    if (errors.length > 0) {
      setErrorMsg([...new Set(errors)].join("；"));
    }
  };

  useEffect(() => {
    let active = true;
    fetchAdminData().then(({ docsResult, evalResult, ordersResult, refundsResult, rulesResult, opsResult, auditResult }) => {
      if (!active) return;
      if (docsResult.status === "fulfilled" && docsResult.value.code === "0000") {
        setDocuments(docsResult.value.data || []);
      }
      if (evalResult.status === "fulfilled" && evalResult.value.code === "0000") {
        setEvaluation(evalResult.value.data);
      }
      if (ordersResult.status === "fulfilled" && ordersResult.value.code === "0000") {
        setOrders(ordersResult.value.data?.orderList || []);
      }
      if (refundsResult.status === "fulfilled" && refundsResult.value.code === "0000") {
        setRefunds(refundsResult.value.data?.refundList || []);
      }
      if (rulesResult.status === "fulfilled" && rulesResult.value.code === "0000") {
        setRules(rulesResult.value.data || []);
      }
      if (opsResult.status === "fulfilled" && opsResult.value.code === "0000") {
        setOpsDashboard(opsResult.value.data || { activities: [], channels: [], crowdTags: [], stocks: [], notifyTasks: [] });
      }
      if (auditResult.status === "fulfilled" && auditResult.value.code === "0000") {
        setTradeAuditItems(auditResult.value.data?.items || []);
      }
      const errors = [resultError(docsResult), resultError(evalResult), resultError(ordersResult), resultError(refundsResult), resultError(rulesResult), resultError(opsResult), resultError(auditResult)].filter(Boolean);
      if (errors.length > 0) {
        setErrorMsg([...new Set(errors)].join("；"));
      }
    });
    return () => {
      active = false;
    };
  }, []);

  const handleAction = async (actionName, apiCall) => {
    setLoadingMsg(`${actionName}中...`);
    try {
      const res = await apiCall();
      if (res.code === "0000") {
        alert(`${actionName}成功`);
        await loadData();
      } else {
        alert(`${actionName}失败：${normalizeApiMessage(res.info, "请求失败")}`);
      }
    } catch (error) {
      alert(`${actionName}异常：${normalizeApiMessage(error.message, "请求失败")}`);
    } finally {
      setLoadingMsg("");
    }
  };

  const handleFileUpload = (event) => {
    const file = event.target.files[0];
    if (file) {
      handleAction(`上传文档 ${file.name}`, () => uploadKnowledgeDocument(file, "global", file.name, "Knowledge"));
    }
    event.target.value = null;
  };

  const loadKnowledgeEvidence = async (documentId) => {
    if (!documentId) return;
    setLoadingMsg("读取知识证据中...");
    try {
      const res = await getKnowledgeDocumentFullContent(documentId);
      if (res.code === "0000") {
        setKnowledgeEvidence(res.data || null);
      } else {
        alert(`读取知识证据失败：${normalizeApiMessage(res.info, "请求失败")}`);
      }
    } catch (error) {
      alert(`读取知识证据异常：${normalizeApiMessage(error.message, "请求失败")}`);
    } finally {
      setLoadingMsg("");
    }
  };

  const updateRuleDraft = (ruleKey, ruleValue) => {
    setRules(prev => prev.map(rule => rule.ruleKey === ruleKey ? { ...rule, ruleValue } : rule));
  };

  const saveRule = async (rule) => {
    await handleAction(`保存规则 ${rule.ruleKey}`, () => updateOperationalRule(rule.ruleKey, rule.ruleValue));
  };

  const updatePaymentOps = (field, value) => {
    setPaymentOps(prev => ({ ...prev, [field]: value }));
  };

  const runPaymentOps = async (actionName, apiCall) => {
    setLoadingMsg(`${actionName}中...`);
    try {
      const res = await apiCall();
      setPaymentOpsResult(res.data || res);
      if (res.code !== "0000") {
        alert(`${actionName}失败：${normalizeApiMessage(res.info, "请求失败")}`);
      }
    } catch (error) {
      alert(`${actionName}异常：${normalizeApiMessage(error.message, "请求失败")}`);
    } finally {
      setLoadingMsg("");
    }
  };

  return (
    <div className="admin-dashboard" data-theme={theme}>
      <header className="admin-header">
        <div>
          <h2>系统管理后台</h2>
          <span>额度与智能体控制台</span>
        </div>
        <div className="admin-header-actions">
          <ThemeToggle theme={theme} onToggle={toggleTheme} />
          <a className="admin-exit" href="/">
            <LogOut size={16} /> 返回前台
          </a>
        </div>
      </header>

      <AdminAuthBar onSaved={loadData} />

      {errorMsg && (
        <div className="admin-error">
          <AlertTriangle size={16} />
          <span>{errorMsg}</span>
        </div>
      )}

      {loadingMsg && (
        <div className="admin-loading-toast">
          <RefreshCw className="spin" size={16} /> {loadingMsg}
        </div>
      )}

      <div className="admin-grid">
        <section className="admin-card">
          <div className="admin-card-header">
            <div className="admin-title-line">
              <Database size={18} color="#2563eb" />
              <h3>知识库向量管理</h3>
            </div>
          </div>
          <div className="admin-card-body">
            <p className="admin-desc">
              管理本地和向量库中的非结构化文档，更新额度包说明、使用规则等上下文。
            </p>
            <div className="admin-actions">
              <button className="admin-btn primary" onClick={() => document.getElementById("file-upload").click()}>
                <Upload size={16} /> 上传知识文档
              </button>
              <input type="file" id="file-upload" className="visually-hidden" onChange={handleFileUpload} />

              <button className="admin-btn outline" onClick={() => handleAction("全量重建向量索引", rebuildKnowledgeVector)}>
                <RefreshCw size={16} /> 重建向量
              </button>

              <button className="admin-btn warning" onClick={() => handleAction("失败补偿重试", compensateKnowledgeVector)}>
                <AlertTriangle size={16} /> 失败补偿
              </button>
            </div>

            <div className="table-wrap">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>文档名称</th>
                    <th>类型</th>
                    <th>状态</th>
                    <th>解析</th>
                    <th>向量</th>
                    <th>可检索</th>
                    <th>解析段落</th>
                    <th>失败原因</th>
                    <th>引用证据</th>
                  </tr>
                </thead>
                <tbody>
                  {documents.map((doc) => (
                    <tr key={doc.documentId || doc.documentName}>
                      <td>{doc.documentName}</td>
                      <td><span className="badge badge-blue">{doc.documentType || "Doc"}</span></td>
                      <td><span className={`badge ${doc.retrievalReady ? "badge-green" : "badge-yellow"}`}>{doc.documentStatus}</span></td>
                      <td><span className="badge badge-gray">{doc.parseStatus || "-"}</span></td>
                      <td><span className={`badge ${doc.embeddingStatus === "READY" ? "badge-green" : doc.embeddingStatus === "FAILED" ? "badge-orange" : "badge-gray"}`}>{doc.embeddingStatus || "-"}</span></td>
                      <td>{doc.retrievalReady ? "是" : "否"}</td>
                      <td>{doc.fragmentCount || 0}</td>
                      <td>{doc.failureReason || "-"}</td>
                      <td>
                        <button className="admin-btn outline" onClick={() => loadKnowledgeEvidence(doc.documentId)}>
                          <Database size={14} /> 查看
                        </button>
                      </td>
                    </tr>
                  ))}
                  {documents.length === 0 && <tr><td colSpan="9" className="empty-cell">暂无数据</td></tr>}
                </tbody>
              </table>
            </div>
            {knowledgeEvidence && (
              <div className="evidence-panel">
                <div className="metric-caption">{knowledgeEvidence.documentName || knowledgeEvidence.documentId} 的引用片段</div>
                <div className="audit-facts">
                  {(knowledgeEvidence.citationSnippets || []).map((snippet) => (
                    <span key={snippet}>{snippet}</span>
                  ))}
                  {(knowledgeEvidence.citationSnippets || []).length === 0 && <span>暂无可引用片段</span>}
                </div>
              </div>
            )}
          </div>
        </section>

        <section className="admin-card">
          <div className="admin-card-header">
            <div className="admin-title-line">
              <Activity size={18} color="#16a34a" />
              <h3>智能体效果自动化评测</h3>
            </div>
          </div>
          <div className="admin-card-body">
            <p className="admin-desc">
              针对知识检索召回率、回答合理性进行批量测试打分。
            </p>
            <button className="admin-btn success" onClick={() => handleAction("执行自动化评测", runAgentEvaluation)}>
              <PlayCircle size={16} /> 运行全量评测
            </button>

            {evaluation ? (
              <div className="metric-panel">
                <div className="metric-caption">最新评测批次 {evaluation.batchNo}</div>
                <div className="metric-grid">
                  <div>
                    <strong>{formatRate(evaluation.retrievalHitRate)}</strong>
                    <span>知识召回率</span>
                  </div>
                  <div>
                    <strong>{formatRate(evaluation.recommendationReasonableRate)}</strong>
                    <span>任务匹配率</span>
                  </div>
                  <div>
                    <strong>{evaluation.totalCount || 0}</strong>
                    <span>测试用例数</span>
                  </div>
                </div>
              </div>
            ) : (
              <div className="empty-panel">暂无评测记录，请点击上方按钮运行评测。</div>
            )}
          </div>
        </section>

        <section className="admin-card full-width">
          <div className="admin-card-header">
            <div className="admin-title-line">
              <CreditCard size={18} color="#ea580c" />
              <h3>交易订单监控</h3>
            </div>
          </div>
          <div className="admin-card-body">
            <div className="table-wrap">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>订单号</th>
                    <th>额度包</th>
                    <th>类型</th>
                    <th>金额</th>
                    <th>状态</th>
                    <th>创建时间</th>
                  </tr>
                </thead>
                <tbody>
                  {orders.map((order) => (
                    <tr key={order.id || order.orderId}>
                      <td className="mono">{order.orderId}</td>
                      <td>{order.productName || order.goodsName || order.productId || order.goodsId || "-"}</td>
                      <td>
                        <span className={`badge ${order.marketType === 1 ? "badge-orange" : "badge-gray"}`}>
                          {order.marketType === 1 ? "拼团" : "单独购买"}
                        </span>
                      </td>
                      <td>￥{order.payAmount || order.totalAmount}</td>
                      <td><span className="badge badge-blue">{order.status}</span></td>
                      <td>{order.orderTime ? order.orderTime.replace("T", " ") : ""}</td>
                    </tr>
                  ))}
                  {orders.length === 0 && <tr><td colSpan="6" className="empty-cell">暂无真实订单数据，请在前台发起购买</td></tr>}
                </tbody>
              </table>
            </div>
          </div>
        </section>

        <section className="admin-card full-width">
          <div className="admin-card-header">
            <div className="admin-title-line">
              <ShieldCheck size={18} color="#0f766e" />
              <h3>交易只读审计</h3>
            </div>
          </div>
          <div className="admin-card-body">
            <p className="admin-desc">
              只展示后台订单、支付、退款和额度流水事实，不提供补发、退款或人工补偿操作。
            </p>
            <div className="table-wrap">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>订单号</th>
                    <th>额度包</th>
                    <th>核对结论</th>
                    <th>说明</th>
                    <th>可发额度</th>
                    <th>需回滚</th>
                    <th>创建时间</th>
                    <th>事实快照</th>
                  </tr>
                </thead>
                <tbody>
                  {tradeAuditItems.map((item, index) => (
                    <tr key={`${item.orderId || item.payOrderId || "audit"}-${index}`}>
                      <td className="mono">{item.orderId || "-"}</td>
                      <td>{item.goodsName || item.goodsId || "-"}</td>
                      <td>
                        <span className={`badge ${auditBadgeClass(item.conclusion)}`}>
                          {item.settlementLabel || item.conclusion || "-"}
                        </span>
                      </td>
                      <td>{item.settlementDetail || item.message || "-"}</td>
                      <td>
                        <span className={`badge ${item.quotaGrantAllowed ? "badge-green" : "badge-gray"}`}>
                          {item.quotaGrantAllowed ? "允许" : "不允许"}
                        </span>
                      </td>
                      <td>
                        <span className={`badge ${item.refundRollbackRequired ? "badge-orange" : "badge-gray"}`}>
                          {item.refundRollbackRequired ? "需要" : "无需"}
                        </span>
                      </td>
                      <td>{formatDateTime(item.orderCreateTime)}</td>
                      <td>
                        <div className="audit-facts">
                          {(item.facts || []).slice(0, 4).map((fact) => (
                            <span key={fact}>{fact}</span>
                          ))}
                        </div>
                      </td>
                    </tr>
                  ))}
                  {tradeAuditItems.length === 0 && <tr><td colSpan="8" className="empty-cell">暂无交易审计结果</td></tr>}
                </tbody>
              </table>
            </div>
          </div>
        </section>

        <section className="admin-card full-width">
          <div className="admin-card-header">
            <div className="admin-title-line">
              <RotateCcw size={18} color="#7c3aed" />
              <h3>售后退款后台</h3>
            </div>
          </div>
          <div className="admin-card-body">
            <div className="table-wrap">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>退款单号</th>
                    <th>订单号</th>
                    <th>用户</th>
                    <th>金额</th>
                    <th>状态</th>
                    <th>原因</th>
                    <th>创建时间</th>
                  </tr>
                </thead>
                <tbody>
                  {refunds.map((refund) => (
                    <tr key={refund.id || refund.refundId}>
                      <td className="mono">{refund.refundId}</td>
                      <td className="mono">{refund.orderId}</td>
                      <td>{refund.userId}</td>
                      <td>￥{refund.refundAmount || 0}</td>
                      <td><span className="badge badge-purple">{refund.refundStatus}</span></td>
                      <td>{refund.refundReason || "-"}</td>
                      <td>{refund.createTime ? refund.createTime.replace("T", " ") : ""}</td>
                    </tr>
                  ))}
                  {refunds.length === 0 && <tr><td colSpan="7" className="empty-cell">暂无退款单</td></tr>}
                </tbody>
              </table>
            </div>
          </div>
        </section>

        <section className="admin-card full-width">
          <div className="admin-card-header">
            <div className="admin-title-line">
              <RefreshCw size={18} color="#2563eb" />
              <h3>支付生产运维</h3>
            </div>
          </div>
          <div className="admin-card-body">
            <div className="form-grid">
              <label>
                支付渠道
                <select value={paymentOps.payChannel} onChange={(event) => updatePaymentOps("payChannel", event.target.value)}>
                  <option value="ALIPAY">ALIPAY</option>
                  <option value="WECHAT_PAY">WECHAT_PAY</option>
                </select>
              </label>
              <label>
                账单日期
                <input type="date" value={paymentOps.billDate} onChange={(event) => updatePaymentOps("billDate", event.target.value)} />
              </label>
              <label>
                退款订单号
                <input value={paymentOps.refundOrderId} onChange={(event) => updatePaymentOps("refundOrderId", event.target.value)} placeholder="本地订单号" />
              </label>
              <label>
                渠道错误码
                <input value={paymentOps.gatewayCode} onChange={(event) => updatePaymentOps("gatewayCode", event.target.value)} placeholder="如 SYSTEMERROR" />
              </label>
            </div>
            <div className="admin-actions payment-actions">
              <button className="admin-btn outline" onClick={() => runPaymentOps("下载解析账单", () => downloadPaymentBill({
                payChannel: paymentOps.payChannel,
                billDate: paymentOps.billDate || undefined,
                billType: "trade",
                downloadContent: false
              }))}>
                <Database size={16} /> 账单解析
              </button>
              <button className="admin-btn outline" onClick={() => runPaymentOps("查询退款", () => queryPaymentRefund({
                orderId: paymentOps.refundOrderId,
                payChannel: paymentOps.payChannel
              }))}>
                <RotateCcw size={16} /> 退款查询
              </button>
              <button className="admin-btn outline" onClick={() => runPaymentOps("刷新证书", () => refreshPaymentCertificate(paymentOps.payChannel))}>
                <RefreshCw size={16} /> 证书刷新
              </button>
              <button className="admin-btn outline" onClick={() => runPaymentOps("映射错误码", () => queryPaymentErrorMap(paymentOps.payChannel, paymentOps.gatewayCode))}>
                <AlertTriangle size={16} /> 错误码映射
              </button>
              <button className="admin-btn outline" onClick={() => runPaymentOps("交易一致性核查", () => queryTradeConsistency({
                orderId: paymentOps.refundOrderId || undefined,
                pageSize: 20
              }))}>
                <Activity size={16} /> 交易核查
              </button>
            </div>
            {paymentOpsResult && (
              <pre className="ops-result">{JSON.stringify(paymentOpsResult, null, 2)}</pre>
            )}
          </div>
        </section>

        <section className="admin-card full-width">
          <div className="admin-card-header">
            <div className="admin-title-line">
              <Boxes size={18} color="#0f766e" />
              <h3>活动渠道运营台</h3>
            </div>
          </div>
          <div className="admin-card-body ops-panel">
            <div className="ops-mini-grid">
              <div className="ops-block">
                <div className="admin-title-line ops-title"><Activity size={16} /><h4>活动配置</h4></div>
                <div className="table-wrap compact">
                  <table className="admin-table compact">
                    <thead><tr><th>活动</th><th>额度包</th><th>团价</th><th>状态</th><th>人群</th></tr></thead>
                    <tbody>
                      {(opsDashboard.activities || []).map((item) => (
                        <tr key={item.activityId}>
                          <td>{item.activityName || item.activityId}</td>
                          <td className="mono">{item.goodsId}</td>
                          <td>￥{item.groupPrice || 0}</td>
                          <td><span className={`badge ${item.enabled ? "badge-green" : "badge-gray"}`}>{item.enabled ? "启用" : "停用"}</span></td>
                          <td>{item.tagId || "-"}</td>
                        </tr>
                      ))}
                      {(opsDashboard.activities || []).length === 0 && <tr><td colSpan="5" className="empty-cell">暂无活动配置</td></tr>}
                    </tbody>
                  </table>
                </div>
              </div>

              <div className="ops-block">
                <div className="admin-title-line ops-title"><Tags size={16} /><h4>渠道额度包</h4></div>
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
                <div className="admin-title-line ops-title"><Tags size={16} /><h4>人群标签</h4></div>
                <div className="table-wrap compact">
                  <table className="admin-table compact">
                    <thead><tr><th>标签</th><th>人数</th><th>批次</th><th>状态</th></tr></thead>
                    <tbody>
                      {(opsDashboard.crowdTags || []).map((item) => (
                        <tr key={item.tagId}>
                          <td>{item.tagName || item.tagId}</td>
                          <td>{item.statistics || 0}</td>
                          <td className="mono">{item.latestBatchId || "-"}</td>
                          <td><span className="badge badge-blue">{item.latestJobStatus ?? "-"}</span></td>
                        </tr>
                      ))}
                      {(opsDashboard.crowdTags || []).length === 0 && <tr><td colSpan="4" className="empty-cell">暂无人群标签</td></tr>}
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

              <div className="ops-block wide">
                <div className="admin-title-line ops-title"><Bell size={16} /><h4>通知任务</h4></div>
                <div className="table-wrap compact">
                  <table className="admin-table compact">
                    <thead><tr><th>任务</th><th>分类</th><th>类型</th><th>次数</th><th>状态</th><th>更新时间</th></tr></thead>
                    <tbody>
                      {(opsDashboard.notifyTasks || []).map((item) => (
                        <tr key={item.uuid}>
                          <td className="mono">{item.uuid}</td>
                          <td>{item.notifyCategory}</td>
                          <td>{item.notifyType}</td>
                          <td>{item.notifyCount || 0}</td>
                          <td><span className="badge badge-purple">{item.notifyStatus}</span></td>
                          <td>{item.updateTime ? item.updateTime.replace("T", " ") : ""}</td>
                        </tr>
                      ))}
                      {(opsDashboard.notifyTasks || []).length === 0 && <tr><td colSpan="6" className="empty-cell">暂无通知任务</td></tr>}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section className="admin-card full-width">
          <div className="admin-card-header">
            <div className="admin-title-line">
              <Settings size={18} color="#0f766e" />
              <h3>运营规则配置</h3>
            </div>
          </div>
          <div className="admin-card-body">
            <div className="table-wrap">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>规则分组</th>
                    <th>配置项</th>
                    <th>配置值</th>
                    <th>来源</th>
                    <th>更新时间</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  {rules.map((rule) => (
                    <tr key={rule.ruleKey}>
                      <td><span className="badge badge-green">{rule.ruleGroup}</span></td>
                      <td className="mono">{rule.ruleKey}</td>
                      <td>
                        <input
                          className="rule-value-input"
                          value={rule.ruleValue || ""}
                          onChange={(event) => updateRuleDraft(rule.ruleKey, event.target.value)}
                        />
                      </td>
                      <td>{rule.description || "-"}</td>
                      <td>{rule.updateTime ? rule.updateTime.replace("T", " ") : ""}</td>
                      <td>
                        <button className="admin-btn outline" onClick={() => saveRule(rule)}>
                          <Save size={16} /> 保存
                        </button>
                      </td>
                    </tr>
                  ))}
                  {rules.length === 0 && <tr><td colSpan="6" className="empty-cell">暂无运营规则</td></tr>}
                </tbody>
              </table>
            </div>
          </div>
        </section>
      </div>
    </div>
  );
}
