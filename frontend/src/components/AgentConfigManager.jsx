import { useEffect, useState } from "react";
import { Bot, Plus, RefreshCw, Save, Server, Sparkles, X } from "lucide-react";
import {
  enableMcpServer,
  normalizeApiMessage,
  queryAdminSkills,
  queryLlmAdminConfig,
  queryMcpServers,
  registerMcpServer,
  saveLlmAdminConfig,
  setAdminSkillEnabled
} from "../services/api";

const EMPTY_LLM_FORM = {
  chat: { apiKey: "", baseUrl: "", model: "" },
  embedding: { apiKey: "", baseUrl: "", model: "" },
  image: { apiKey: "", baseUrl: "", model: "" }
};

const MODEL_GROUPS = [
  { key: "chat", label: "文本模型", hint: "Agent 对话用，对应 .env 的 AGENT_GROUP_LLM_*" },
  { key: "embedding", label: "嵌入模型", hint: "知识库向量化用，key/url 共用文本模型" },
  { key: "image", label: "图像模型", hint: "图像生成用，可填 DashScope 或兼容接口" }
];

export default function AgentConfigManager({ section = "llmConfig", authVersion }) {
  const [llm, setLlm] = useState({ chat: {}, embedding: {}, image: {}, persisted: EMPTY_LLM_FORM, overrideFile: "" });
  const [llmForm, setLlmForm] = useState(EMPTY_LLM_FORM);
  const [llmEditing, setLlmEditing] = useState(false);
  const [llmSaving, setLlmSaving] = useState(false);
  const [skills, setSkills] = useState([]);
  const [mcpServers, setMcpServers] = useState([]);
  const [mcpForm, setMcpForm] = useState({ serverId: "", name: "", endpoint: "", transport: "streamable_http" });
  const [mcpAdding, setMcpAdding] = useState(false);
  const [mcpSaving, setMcpSaving] = useState(false);
  const [message, setMessage] = useState("");

  const loadAll = async () => {
    setMessage("");
    try {
      const [llmRes, skillsRes, mcpRes] = await Promise.all([
        queryLlmAdminConfig(),
        queryAdminSkills(),
        queryMcpServers()
      ]);
      if (llmRes?.code === "0000" && llmRes.data) {
        setLlm(llmRes.data);
        const persisted = llmRes.data.persisted || {};
        setLlmForm({
          chat: { apiKey: persisted.chat?.apiKey || "", baseUrl: persisted.chat?.baseUrl || "", model: persisted.chat?.model || "" },
          embedding: { apiKey: "", baseUrl: "", model: persisted.embedding?.model || "" },
          image: { apiKey: persisted.image?.apiKey || "", baseUrl: persisted.image?.baseUrl || "", model: persisted.image?.model || "" }
        });
      }
      if (skillsRes?.code === "0000") setSkills(skillsRes.data || []);
      if (mcpRes?.code === "0000") setMcpServers(mcpRes.data || []);
      const errors = [
        llmRes?.code !== "0000" ? normalizeApiMessage(llmRes) : "",
        skillsRes?.code !== "0000" ? normalizeApiMessage(skillsRes) : "",
        mcpRes?.code !== "0000" ? normalizeApiMessage(mcpRes) : ""
      ].filter(Boolean);
      if (errors.length) setMessage([...new Set(errors)].join("；"));
    } catch (err) {
      setMessage(normalizeApiMessage(err) || "加载失败，请先登录运营端");
    }
  };

  useEffect(() => {
    loadAll();
  }, [authVersion]);

  const updateLlmField = (group, key, value) => setLlmForm((prev) => ({
    ...prev,
    [group]: { ...prev[group], [key]: value }
  }));

  const saveLlm = async () => {
    setMessage("");
    setLlmSaving(true);
    try {
      const res = await saveLlmAdminConfig({
        chat: llmForm.chat,
        embedding: { model: llmForm.embedding.model },
        image: llmForm.image
      });
      if (res?.code === "0000") {
        setLlmEditing(false);
        await loadAll();
        setMessage("模型配置已保存，重启后端后生效");
      } else {
        setMessage(normalizeApiMessage(res) || "保存失败");
      }
    } catch (err) {
      setMessage(normalizeApiMessage(err) || "保存失败，请先登录运营端");
    } finally {
      setLlmSaving(false);
    }
  };

  const cancelLlmEdit = () => {
    setLlmEditing(false);
    const persisted = llm.persisted || {};
    setLlmForm({
      chat: { apiKey: persisted.chat?.apiKey || "", baseUrl: persisted.chat?.baseUrl || "", model: persisted.chat?.model || "" },
      embedding: { apiKey: "", baseUrl: "", model: persisted.embedding?.model || "" },
      image: { apiKey: persisted.image?.apiKey || "", baseUrl: persisted.image?.baseUrl || "", model: persisted.image?.model || "" }
    });
  };

  const toggleSkill = async (skill) => {
    setMessage("");
    try {
      const res = await setAdminSkillEnabled(skill.name, !skill.enabled);
      if (res?.code === "0000") {
        setSkills((prev) => prev.map((s) => (s.name === skill.name ? { ...s, enabled: !skill.enabled } : s)));
      } else {
        setMessage(normalizeApiMessage(res) || "切换失败");
      }
    } catch (err) {
      setMessage(normalizeApiMessage(err) || "切换失败，请先登录运营端");
    }
  };

  const toggleMcp = async (server) => {
    setMessage("");
    try {
      const res = await enableMcpServer(server.serverId, !server.enabled);
      if (res?.code === "0000") {
        setMcpServers((prev) => prev.map((s) => (s.serverId === server.serverId ? { ...s, enabled: !server.enabled } : s)));
      } else {
        setMessage(normalizeApiMessage(res) || "切换失败");
      }
    } catch (err) {
      setMessage(normalizeApiMessage(err) || "切换失败，请先登录运营端");
    }
  };

  const updateMcpField = (key, value) => setMcpForm((prev) => ({ ...prev, [key]: value }));

  const submitMcp = async () => {
    setMessage("");
    if (!mcpForm.serverId || !mcpForm.endpoint) {
      setMessage("请填写服务编号和地址");
      return;
    }
    setMcpSaving(true);
    try {
      const res = await registerMcpServer({
        serverId: mcpForm.serverId.trim(),
        name: mcpForm.name.trim() || mcpForm.serverId.trim(),
        endpoint: mcpForm.endpoint.trim(),
        transport: mcpForm.transport,
        enabled: true
      });
      if (res?.code === "0000") {
        setMcpAdding(false);
        setMcpForm({ serverId: "", name: "", endpoint: "", transport: "streamable_http" });
        await loadMcpServers();
        setMessage("MCP 服务已添加，重启后端后生效");
      } else {
        setMessage(normalizeApiMessage(res) || "添加失败");
      }
    } catch (err) {
      setMessage(normalizeApiMessage(err) || "添加失败，请先登录运营端");
    } finally {
      setMcpSaving(false);
    }
  };

  const cancelMcpAdd = () => {
    setMcpAdding(false);
    setMcpForm({ serverId: "", name: "", endpoint: "", transport: "streamable_http" });
  };

  const loadMcpServers = async () => {
    try {
      const res = await queryMcpServers();
      if (res?.code === "0000") setMcpServers(res.data || []);
    } catch {
      // 静默
    }
  };

  const titleMap = { llmConfig: "模型配置", skills: "技能 Skills", mcp: "MCP 服务" };
  const iconMap = { llmConfig: Sparkles, skills: Sparkles, mcp: Server };

  const TitleIcon = iconMap[section] || Bot;

  return (
    <div className="ops-block">
      <div className="admin-title-line ops-title">
        <TitleIcon size={16} />
        <h4>{titleMap[section] || "智能体配置"}</h4>
        <button className="admin-btn outline small" onClick={loadAll} title="刷新"><RefreshCw size={14} /></button>
      </div>

      {message && <div className="admin-tip">{message}</div>}

      {section === "llmConfig" && (
        <div className="admin-card nested">
          <div className="admin-card-header">
            <div className="admin-title-line"><Sparkles size={16} color="#0f766e" /><h5>默认模型配置</h5></div>
            {!llmEditing && (
              <button className="admin-btn outline small" onClick={() => setLlmEditing(true)}>编辑</button>
            )}
          </div>
          <div className="admin-card-body">
            {MODEL_GROUPS.map((group) => {
              const effective = llm[group.key] || {};
              const formGroup = llmForm[group.key] || {};
              const showKeyUrl = group.key !== "embedding";
              return (
                <div key={group.key} className="admin-inline-discount" style={{ marginBottom: 10 }}>
                  <div className="admin-title-line" style={{ gridColumn: "1 / -1" }}>
                    <h5 style={{ margin: 0 }}>{group.label}</h5>
                    <span className="admin-hint" style={{ marginLeft: 8 }}>{group.hint}</span>
                  </div>
                  {showKeyUrl && (
                    <>
                      <label className="admin-field">
                        <span>API Key</span>
                        {llmEditing ? (
                          <input value={formGroup.apiKey} onChange={(e) => updateLlmField(group.key, "apiKey", e.target.value)} placeholder="留空则不覆盖 .env" />
                        ) : (
                          <input value={effective.apiKey || (llm.persisted?.[group.key]?.apiKey) || ""} readOnly placeholder="未配置" />
                        )}
                      </label>
                      <label className="admin-field">
                        <span>Base URL</span>
                        {llmEditing ? (
                          <input value={formGroup.baseUrl} onChange={(e) => updateLlmField(group.key, "baseUrl", e.target.value)} placeholder="如 https://dashscope.aliyuncs.com/compatible-mode/v1" />
                        ) : (
                          <input value={effective.baseUrl || ""} readOnly placeholder="未配置" />
                        )}
                      </label>
                    </>
                  )}
                  <label className="admin-field">
                    <span>Model</span>
                    {llmEditing ? (
                      <input value={formGroup.model} onChange={(e) => updateLlmField(group.key, "model", e.target.value)} placeholder={group.key === "chat" ? "如 qwen-plus" : group.key === "embedding" ? "如 text-embedding-v3" : "如 qwen-image-plus"} />
                    ) : (
                      <input value={effective.model || ""} readOnly placeholder="未配置" />
                    )}
                  </label>
                </div>
              );
            })}
            {llmEditing && (
              <div className="admin-form-actions">
                <button className="admin-btn primary" onClick={saveLlm} disabled={llmSaving}>
                  <Save size={14} /> {llmSaving ? "保存中…" : "保存"}
                </button>
                <button className="admin-btn outline" onClick={cancelLlmEdit}>取消</button>
              </div>
            )}
            <div className="admin-hint">当前生效值从后端环境读取，后台填写后保存并重启后端生效；留空表示不覆盖 .env。嵌入模型与文本模型共用同一组 API Key / Base URL。</div>
          </div>
        </div>
      )}

      {section === "skills" && (
        <div className="admin-card nested">
          <div className="admin-card-header">
            <div className="admin-title-line"><Sparkles size={16} color="#0f766e" /><h5>技能列表</h5></div>
          </div>
          <div className="admin-card-body">
            <div className="table-wrap compact">
              <table className="admin-table compact">
                <thead><tr><th>技能</th><th>说明</th><th>状态</th><th>操作</th></tr></thead>
                <tbody>
                  {skills.map((skill) => (
                    <tr key={skill.name}>
                      <td className="admin-cell-primary">{skill.name}</td>
                      <td className="admin-cell-content">{skill.description || "-"}</td>
                      <td><span className={`badge ${skill.enabled ? "badge-green" : "badge-gray"}`}>{skill.enabled ? "启用" : "停用"}</span></td>
                      <td className="action-cell">
                        <button className="admin-btn outline small" onClick={() => toggleSkill(skill)}>{skill.enabled ? "停用" : "启用"}</button>
                      </td>
                    </tr>
                  ))}
                  {skills.length === 0 && <tr><td colSpan="4" className="empty-cell">暂无技能</td></tr>}
                </tbody>
              </table>
            </div>
            <div className="admin-hint">技能从 skills 目录加载，禁用后新会话不再暴露该技能给 Agent。</div>
          </div>
        </div>
      )}

      {section === "mcp" && (
        <div className="admin-card nested">
          <div className="admin-card-header">
            <div className="admin-title-line"><Server size={16} color="#0f766e" /><h5>MCP 服务</h5></div>
            {!mcpAdding && (
              <button className="admin-btn primary small" onClick={() => setMcpAdding(true)}><Plus size={14} /> 添加 MCP 服务</button>
            )}
          </div>
          <div className="admin-card-body">
            {mcpAdding && (
              <div className="admin-form-panel">
                <div className="admin-title-line">
                  <h5>添加 MCP 服务</h5>
                  <button className="admin-btn outline small" onClick={cancelMcpAdd}><X size={14} /></button>
                </div>
                <div className="admin-hint">填写 MCP 服务信息，保存后写入 data/mcp-admin-state.json，重启后端后自动加载。</div>
                <div className="admin-form-grid">
                  <label className="admin-field">
                    <span>服务编号</span>
                    <input value={mcpForm.serverId} onChange={(e) => updateMcpField("serverId", e.target.value)} placeholder="如 deepsearch" />
                  </label>
                  <label className="admin-field">
                    <span>名称</span>
                    <input value={mcpForm.name} onChange={(e) => updateMcpField("name", e.target.value)} placeholder="留空则用服务编号" />
                  </label>
                  <label className="admin-field">
                    <span>地址</span>
                    <input value={mcpForm.endpoint} onChange={(e) => updateMcpField("endpoint", e.target.value)} placeholder="如 http://127.0.0.1:9000/sse" />
                  </label>
                  <label className="admin-field">
                    <span>传输方式</span>
                    <select value={mcpForm.transport} onChange={(e) => updateMcpField("transport", e.target.value)}>
                      <option value="streamable_http">streamable_http</option>
                      <option value="sse">sse</option>
                      <option value="stdio">stdio</option>
                    </select>
                  </label>
                </div>
                <div className="admin-form-actions">
                  <button className="admin-btn primary" onClick={submitMcp} disabled={mcpSaving}>
                    <Save size={14} /> {mcpSaving ? "保存中…" : "保存"}
                  </button>
                  <button className="admin-btn outline" onClick={cancelMcpAdd}>取消</button>
                </div>
              </div>
            )}
            <div className="table-wrap compact">
              <table className="admin-table compact">
                <thead><tr><th>服务</th><th>地址</th><th>传输</th><th>工具数</th><th>状态</th><th>操作</th></tr></thead>
                <tbody>
                  {mcpServers.map((server) => (
                    <tr key={server.serverId}>
                      <td>
                        <div className="admin-cell-primary">{server.name || server.serverId}</div>
                        <div className="admin-cell-sub mono">{server.serverId}</div>
                      </td>
                      <td className="admin-cell-content mono">{server.endpoint || "-"}</td>
                      <td><span className="badge badge-blue">{server.transport || "-"}</span></td>
                      <td className="mono">{server.toolCount ?? 0}</td>
                      <td><span className={`badge ${server.enabled ? "badge-green" : "badge-gray"}`}>{server.enabled ? "启用" : "停用"}</span></td>
                      <td className="action-cell">
                        <button className="admin-btn outline small" onClick={() => toggleMcp(server)}>{server.enabled ? "停用" : "启用"}</button>
                      </td>
                    </tr>
                  ))}
                  {mcpServers.length === 0 && <tr><td colSpan="6" className="empty-cell">暂无 MCP 服务，点击右上角添加</td></tr>}
                </tbody>
              </table>
            </div>
            <div className="admin-hint">添加后需重启后端才会加载并发现工具；启停对已加载的服务即时生效。</div>
          </div>
        </div>
      )}
    </div>
  );
}
