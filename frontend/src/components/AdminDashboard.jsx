import { useEffect, useState } from "react";
import { Activity, AlertTriangle, Database, LogOut, PlayCircle, RefreshCw, ShoppingCart, Upload } from "lucide-react";
import AdminAuthBar from "./AdminAuthBar";
import {
  compensateKnowledgeVector,
  getKnowledgeDocuments,
  getLatestGuideEvaluation,
  queryUserOrderList,
  rebuildKnowledgeVector,
  runGuideEvaluation,
  uploadKnowledgeDocument
} from "../services/api";

async function fetchAdminData() {
  const [docsResult, evalResult, ordersResult] = await Promise.allSettled([
    getKnowledgeDocuments(),
    getLatestGuideEvaluation(),
    queryUserOrderList()
  ]);

  return {
    docsResult,
    evalResult,
    ordersResult
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

export default function AdminDashboard() {
  const [loadingMsg, setLoadingMsg] = useState("");
  const [documents, setDocuments] = useState([]);
  const [evaluation, setEvaluation] = useState(null);
  const [orders, setOrders] = useState([]);
  const [errorMsg, setErrorMsg] = useState("");

  const loadData = async () => {
    setErrorMsg("");
    const { docsResult, evalResult, ordersResult } = await fetchAdminData();

    if (docsResult.status === "fulfilled" && docsResult.value.code === "0000") {
      setDocuments(docsResult.value.data || []);
    }
    if (evalResult.status === "fulfilled" && evalResult.value.code === "0000") {
      setEvaluation(evalResult.value.data);
    }
    if (ordersResult.status === "fulfilled" && ordersResult.value.code === "0000") {
      setOrders(ordersResult.value.data?.orderList || []);
    }

    const errors = [resultError(docsResult), resultError(evalResult), resultError(ordersResult)].filter(Boolean);
    if (errors.length > 0) {
      setErrorMsg([...new Set(errors)].join("；"));
    }
  };

  useEffect(() => {
    let active = true;
    fetchAdminData().then(({ docsResult, evalResult, ordersResult }) => {
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
      const errors = [resultError(docsResult), resultError(evalResult), resultError(ordersResult)].filter(Boolean);
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
        alert(`${actionName}失败：${res.info}`);
      }
    } catch (error) {
      alert(`${actionName}异常：${error.message || "请求失败"}`);
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

  return (
    <div className="admin-dashboard">
      <header className="admin-header">
        <div>
          <h2>系统管理后台</h2>
          <span>企业级 AI 导购控制台</span>
        </div>
        <a className="admin-exit" href="/">
          <LogOut size={16} /> 返回前台
        </a>
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
              管理本地及 pgvector 中的非结构化文档，更新导购话术、商品规则等上下文。
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
                    <th>解析段落</th>
                  </tr>
                </thead>
                <tbody>
                  {documents.map((doc) => (
                    <tr key={doc.documentId || doc.documentName}>
                      <td>{doc.documentName}</td>
                      <td><span className="badge badge-blue">{doc.documentType || "Doc"}</span></td>
                      <td><span className={`badge ${doc.documentStatus === "EMBEDDED" ? "badge-green" : "badge-yellow"}`}>{doc.documentStatus}</span></td>
                      <td>{doc.fragmentCount || 0}</td>
                    </tr>
                  ))}
                  {documents.length === 0 && <tr><td colSpan="4" className="empty-cell">暂无数据</td></tr>}
                </tbody>
              </table>
            </div>
          </div>
        </section>

        <section className="admin-card">
          <div className="admin-card-header">
            <div className="admin-title-line">
              <Activity size={18} color="#16a34a" />
              <h3>导购效果自动化评测</h3>
            </div>
          </div>
          <div className="admin-card-body">
            <p className="admin-desc">
              针对 RAG 检索召回率、大模型推荐合理性进行批量 Case 测试打分。
            </p>
            <button className="admin-btn success" onClick={() => handleAction("执行自动化评测", runGuideEvaluation)}>
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
                    <span>推荐合理率</span>
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
              <ShoppingCart size={18} color="#ea580c" />
              <h3>交易订单监控</h3>
            </div>
          </div>
          <div className="admin-card-body">
            <div className="table-wrap">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>订单号</th>
                    <th>商品名称</th>
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
                      <td>{order.productName}</td>
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
      </div>
    </div>
  );
}
