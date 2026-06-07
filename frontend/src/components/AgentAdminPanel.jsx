import { useEffect, useMemo, useState } from "react";
import { Check, Download, Loader2, Plus, RotateCcw, Settings, Trash2, Upload } from "lucide-react";
import {
  deleteAgentAdminConfig,
  enableAgentAdminConfig,
  exportAgentAdminState,
  importAgentAdminState,
  normalizeApiMessage,
  queryAgentAdminConfigs,
  queryAgentAdminRuntimeSnapshot,
  queryAgentAdminStatistics,
  upsertAgentAdminConfig
} from "../services/api";

const CATEGORIES = [
  { key: "", label: "全部" },
  { key: "agent_client", label: "Client" },
  { key: "model", label: "Model" },
  { key: "api", label: "API" },
  { key: "system_prompt", label: "Prompt" },
  { key: "advisor", label: "Advisor" },
  { key: "rag_order", label: "RAG" },
  { key: "tool", label: "Tool" },
  { key: "mcp_tool", label: "MCP" },
  { key: "draw_config", label: "Draw" }
];

const EMPTY_FORM = {
  configId: "",
  category: "model",
  name: "",
  description: "",
  content: "",
  orderNo: 0,
  enabled: true
};

const EMPTY_RUNTIME_MAP = {};

const RUNTIME_SECTION_LABELS = {
  agentClients: "Client",
  models: "Model",
  apis: "API",
  systemPrompts: "Prompt",
  advisors: "Advisor",
  ragOrders: "RAG",
  tools: "Tool",
  mcpTools: "MCP",
  drawConfigs: "Draw"
};

function apiSucceeded(res) {
  return res?.code === "0000" || res?.code === 200 || res?.code === "200";
}

function toForm(config = {}) {
  return {
    configId: config.configId || "",
    category: config.category || "model",
    name: config.name || "",
    description: config.description || "",
    content: config.content || "",
    orderNo: Number(config.orderNo || 0),
    enabled: config.enabled !== false
  };
}

export default function AgentAdminPanel({
  adminForm,
  setAdminForm,
  onSaveAdminAuth,
  onCapabilitiesRefresh
}) {
  const [category, setCategory] = useState("");
  const [configs, setConfigs] = useState([]);
  const [statistics, setStatistics] = useState(null);
  const [runtimeSnapshot, setRuntimeSnapshot] = useState(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [loading, setLoading] = useState(false);
  const [actionKey, setActionKey] = useState("");
  const [error, setError] = useState("");
  const [exportPreview, setExportPreview] = useState("");
  const [importPayload, setImportPayload] = useState(JSON.stringify({
    replace: false,
    configs: [
      {
        configId: "general-agent-system-prompt",
        category: "system_prompt",
        name: "General agent system prompt",
        content: "Use backend facts for account, quota and task state. Keep user-facing answers focused on the active conversation.",
        enabled: true
      }
    ]
  }, null, 2));

  const enabledCount = useMemo(
    () => configs.filter((config) => config.enabled !== false).length,
    [configs]
  );
  const runtimeSections = runtimeSnapshot?.runtimeSections || EMPTY_RUNTIME_MAP;
  const runtimePolicies = runtimeSnapshot?.runtimePolicies || EMPTY_RUNTIME_MAP;
  const assemblyPlan = Array.isArray(runtimeSnapshot?.assemblyPlan) ? runtimeSnapshot.assemblyPlan : [];
  const codeInterpreterPolicy = runtimePolicies.codeInterpreter || {};
  const scriptRunnerPolicy = runtimePolicies.scriptRunner || {};
  const runtimeSectionItems = useMemo(
    () => Object.entries(RUNTIME_SECTION_LABELS).map(([key, label]) => ({
      key,
      label,
      count: Array.isArray(runtimeSections[key]) ? runtimeSections[key].length : 0
    })),
    [runtimeSections]
  );

  const loadState = async (nextCategory = category) => {
    setLoading(true);
    setError("");
    try {
      const [configsRes, statsRes, snapshotRes] = await Promise.all([
        queryAgentAdminConfigs({ category: nextCategory, enabledOnly: false }),
        queryAgentAdminStatistics(),
        queryAgentAdminRuntimeSnapshot()
      ]);
      if (!apiSucceeded(configsRes)) {
        throw new Error(normalizeApiMessage(configsRes?.info || configsRes?.message, "配置读取失败"));
      }
      if (!apiSucceeded(statsRes)) {
        throw new Error(normalizeApiMessage(statsRes?.info || statsRes?.message, "统计读取失败"));
      }
      if (!apiSucceeded(snapshotRes)) {
        throw new Error(normalizeApiMessage(snapshotRes?.info || snapshotRes?.message, "运行快照读取失败"));
      }
      setConfigs(configsRes.data || []);
      setStatistics(statsRes.data || null);
      setRuntimeSnapshot(snapshotRes.data || null);
    } catch (loadError) {
      setError(normalizeApiMessage(loadError.message, "Agent 配置后台读取失败"));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadState(category).catch(() => {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [category]);

  const saveConfig = async (event) => {
    event.preventDefault();
    setError("");
    setActionKey("save");
    try {
      if (!form.configId.trim() || !form.category.trim()) {
        throw new Error("请填写配置标识和分类");
      }
      const res = await upsertAgentAdminConfig({
        ...form,
        orderNo: Number(form.orderNo || 0),
        metadata: {}
      });
      if (!apiSucceeded(res)) {
        throw new Error(normalizeApiMessage(res?.info || res?.message, "配置保存失败"));
      }
      setForm(EMPTY_FORM);
      await loadState(category);
      await onCapabilitiesRefresh?.();
    } catch (saveError) {
      setError(normalizeApiMessage(saveError.message, "配置保存失败"));
    } finally {
      setActionKey("");
    }
  };

  const toggleConfig = async (config) => {
    const configId = String(config?.configId || "");
    if (!configId) return;
    setActionKey(`toggle-${configId}`);
    setError("");
    try {
      const res = await enableAgentAdminConfig(configId, config.enabled === false);
      if (!apiSucceeded(res)) {
        throw new Error(normalizeApiMessage(res?.info || res?.message, "配置状态更新失败"));
      }
      await loadState(category);
      await onCapabilitiesRefresh?.();
    } catch (toggleError) {
      setError(normalizeApiMessage(toggleError.message, "配置状态更新失败"));
    } finally {
      setActionKey("");
    }
  };

  const removeConfig = async (config) => {
    const configId = String(config?.configId || "");
    if (!configId) return;
    setActionKey(`delete-${configId}`);
    setError("");
    try {
      const res = await deleteAgentAdminConfig(configId);
      if (!apiSucceeded(res)) {
        throw new Error(normalizeApiMessage(res?.info || res?.message, "配置删除失败"));
      }
      await loadState(category);
      await onCapabilitiesRefresh?.();
    } catch (deleteError) {
      setError(normalizeApiMessage(deleteError.message, "配置删除失败"));
    } finally {
      setActionKey("");
    }
  };

  const exportState = async () => {
    setActionKey("export");
    setError("");
    try {
      const res = await exportAgentAdminState();
      if (!apiSucceeded(res)) {
        throw new Error(normalizeApiMessage(res?.info || res?.message, "配置导出失败"));
      }
      setExportPreview(JSON.stringify(res.data || {}, null, 2));
    } catch (exportError) {
      setError(normalizeApiMessage(exportError.message, "配置导出失败"));
    } finally {
      setActionKey("");
    }
  };

  const importState = async () => {
    setActionKey("import");
    setError("");
    try {
      const payload = JSON.parse(importPayload || "{}");
      const res = await importAgentAdminState(payload);
      if (!apiSucceeded(res)) {
        throw new Error(normalizeApiMessage(res?.info || res?.message, "配置导入失败"));
      }
      setExportPreview(JSON.stringify(res.data || {}, null, 2));
      await loadState(category);
      await onCapabilitiesRefresh?.();
    } catch (importError) {
      setError(normalizeApiMessage(importError.message, "配置导入失败，请检查 JSON 格式"));
    } finally {
      setActionKey("");
    }
  };

  return (
    <section className="mcp-panel agent-admin-panel">
      <div className="mcp-panel-head">
        <div>
          <strong>Agent 配置后台</strong>
          <span>管理模型、API、系统提示词、Advisor、RAG 顺序和绘图配置</span>
        </div>
        <div className="mcp-panel-stats">
          <span>启用 <b>{enabledCount}/{configs.length}</b></span>
          <span>分类 <b>{statistics?.categoryCount || 0}</b></span>
          <span>运行 <b>{runtimeSnapshot?.enabledCount || 0}</b></span>
          <button type="button" onClick={() => loadState(category).catch(() => {})} disabled={loading}>
            {loading ? <Loader2 size={14} className="spin" /> : <RotateCcw size={14} />}
            刷新
          </button>
          <button
            type="button"
            onClick={() => setExportPreview(JSON.stringify(runtimeSnapshot || {}, null, 2))}
            disabled={!runtimeSnapshot}
          >
            <Settings size={14} />
            快照
          </button>
          <button type="button" onClick={exportState} disabled={actionKey === "export"}>
            {actionKey === "export" ? <Loader2 size={14} className="spin" /> : <Download size={14} />}
            导出
          </button>
          <button type="button" onClick={importState} disabled={actionKey === "import"}>
            {actionKey === "import" ? <Loader2 size={14} className="spin" /> : <Upload size={14} />}
            导入
          </button>
        </div>
      </div>

      <div className="mcp-auth-row">
        <input
          value={adminForm.username}
          onChange={(event) => setAdminForm({ ...adminForm, username: event.target.value })}
          placeholder="运营账号"
        />
        <input
          value={adminForm.password}
          onChange={(event) => setAdminForm({ ...adminForm, password: event.target.value })}
          type="password"
          placeholder="运营密码"
        />
        <button type="button" onClick={onSaveAdminAuth}>
          <Check size={14} />
          保存授权
        </button>
      </div>

      {error && <div className="mcp-error">{error}</div>}

      {runtimeSnapshot && (
        <div className="agent-runtime-snapshot">
          <div className="agent-runtime-summary">
            <span>启用配置 <b>{runtimeSnapshot.enabledCount || 0}</b></span>
            <span>停用配置 <b>{runtimeSnapshot.disabledCount || 0}</b></span>
            <span>敏感信息 <b>{runtimeSnapshot.sensitiveMasked ? "已脱敏" : "未脱敏"}</b></span>
          </div>
          <div className="agent-runtime-sections">
            {runtimeSectionItems.map((item) => (
              <span key={item.key}>
                <b>{item.label}</b>
                {item.count}
              </span>
            ))}
          </div>
          <div className="agent-runtime-active">
            {(runtimeSnapshot.enabledConfigs || []).slice(0, 5).map((item) => (
              <span key={`${item.category}-${item.configId}`}>
                <b>{item.name || item.configId}</b>
                {item.category} / {item.contentPreview || "无内容"}
              </span>
            ))}
          </div>
          {assemblyPlan.length > 0 && (
            <div className="agent-runtime-assembly">
              {assemblyPlan.map((stage) => (
                <span key={stage.stageKey || stage.stageNo} className={stage.enabled ? "ready" : "empty"}>
                  <b>{stage.stageNo}. {stage.stageName || stage.stageKey}</b>
                  {stage.itemCount || 0} 项
                  <small>{stage.operatorHint || ""}</small>
                </span>
              ))}
            </div>
          )}
          <div className="agent-runtime-policies">
            <span>
              <b>代码权限</b>
              {codeInterpreterPolicy.defaultPermissionProfile || "analysis"}
            </span>
            <span>
              <b>可选档位</b>
              {(codeInterpreterPolicy.allowedPermissionProfiles || []).join(" / ") || "analysis / workspace"}
            </span>
            <span>
              <b>脚本运行</b>
              {scriptRunnerPolicy.registeredSkillOnly === false ? "开放脚本" : "注册技能脚本"}
            </span>
          </div>
        </div>
      )}

      <div className="agent-admin-tabs">
        {CATEGORIES.map((item) => (
          <button
            type="button"
            key={item.key || "all"}
            className={category === item.key ? "active" : ""}
            onClick={() => setCategory(item.key)}
          >
            {item.label}
          </button>
        ))}
      </div>

      <div className="mcp-panel-grid">
        <form className="mcp-editor" onSubmit={saveConfig}>
          <div className="mcp-editor-head">
            <strong>配置编辑</strong>
            <label>
              <input
                type="checkbox"
                checked={form.enabled}
                onChange={(event) => setForm({ ...form, enabled: event.target.checked })}
              />
              启用
            </label>
          </div>
          <div className="mcp-form-grid">
            <input
              value={form.configId}
              onChange={(event) => setForm({ ...form, configId: event.target.value })}
              placeholder="配置标识"
              required
            />
            <select
              value={form.category}
              onChange={(event) => setForm({ ...form, category: event.target.value })}
            >
              {CATEGORIES.filter((item) => item.key).map((item) => (
                <option key={item.key} value={item.key}>{item.key}</option>
              ))}
            </select>
            <input
              value={form.name}
              onChange={(event) => setForm({ ...form, name: event.target.value })}
              placeholder="配置名称"
            />
            <input
              value={form.orderNo}
              onChange={(event) => setForm({ ...form, orderNo: event.target.value })}
              type="number"
              placeholder="排序"
            />
          </div>
          <input
            value={form.description}
            onChange={(event) => setForm({ ...form, description: event.target.value })}
            placeholder="说明"
          />
          <textarea
            value={form.content}
            onChange={(event) => setForm({ ...form, content: event.target.value })}
            rows={7}
            spellCheck={false}
            placeholder="配置内容"
          />
          <button className="mcp-primary-btn" type="submit" disabled={actionKey === "save"}>
            {actionKey === "save" ? <Loader2 size={15} className="spin" /> : <Plus size={15} />}
            保存配置
          </button>
        </form>

        <div className="mcp-list-block">
          <div className="mcp-list-title">配置列表</div>
          {configs.length === 0 && <p className="mcp-empty">暂无配置</p>}
          {configs.map((config) => (
            <div className="mcp-server-row agent-config-row" key={config.configId}>
              <div>
                <b>{config.name || config.configId}</b>
                <span>{config.configId} / {config.category}</span>
                <small>{config.description || config.content}</small>
              </div>
              <div>
                <em>{config.enabled === false ? "停用" : "启用"}</em>
                <button type="button" onClick={() => setForm(toForm(config))}>
                  <Settings size={13} />
                  编辑
                </button>
                <button
                  type="button"
                  className={config.enabled !== false ? "enabled" : ""}
                  onClick={() => toggleConfig(config)}
                  disabled={actionKey === `toggle-${config.configId}`}
                >
                  {config.enabled === false ? "启用" : "停用"}
                </button>
                <button
                  type="button"
                  onClick={() => removeConfig(config)}
                  disabled={actionKey === `delete-${config.configId}`}
                >
                  <Trash2 size={13} />
                  删除
                </button>
              </div>
            </div>
          ))}
        </div>
      </div>

      <div className="agent-admin-import">
        <div>
          <strong>配置导入</strong>
          <span>粘贴导出的配置快照，支持增量导入或 replace 覆盖导入</span>
        </div>
        <textarea
          value={importPayload}
          onChange={(event) => setImportPayload(event.target.value)}
          rows={7}
          spellCheck={false}
        />
      </div>

      {exportPreview && (
        <pre className="agent-admin-export">{exportPreview}</pre>
      )}
    </section>
  );
}
