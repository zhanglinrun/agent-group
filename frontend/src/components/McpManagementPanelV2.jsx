import { Activity, Check, Download, Globe2, Loader2, Play, Plus, RotateCcw, Settings, Upload } from "lucide-react";
import { MCP_TRANSPORT_OPTIONS, normalizeMcpTransport } from "../mcpServerForm";

const CACHE_STATUS_LABELS = {
  empty: "未缓存",
  fresh: "缓存有效",
  unbounded: "长期有效",
  expired: "缓存过期"
};

function formatCacheAge(seconds) {
  const value = Number(seconds || 0);
  if (!Number.isFinite(value) || value <= 0) return "";
  if (value < 60) return `${Math.floor(value)} 秒`;
  if (value < 3600) return `${Math.floor(value / 60)} 分钟`;
  return `${Math.floor(value / 3600)} 小时`;
}

function cacheStatusText(server) {
  const status = String(server?.cacheStatus || "empty");
  const label = CACHE_STATUS_LABELS[status] || status;
  const age = formatCacheAge(server?.cacheAgeSeconds);
  return age ? `${label} · ${age}` : label;
}

export default function McpManagementPanelV2({
  adminForm,
  setAdminForm,
  serverForm,
  setServerForm,
  servers,
  tools,
  loading,
  actionKey,
  error,
  health,
  exportPayload,
  importPayload,
  setImportPayload,
  toolCallName,
  setToolCallName,
  toolCallPayload,
  setToolCallPayload,
  toolCallResult,
  cacheServerId,
  setCacheServerId,
  toolPayload,
  setToolPayload,
  onSaveAdminAuth,
  onRefresh,
  onRegister,
  onToggleServer,
  onDiscoverTools,
  onCacheTools,
  onCheckHealth,
  onExportState,
  onImportState,
  onCallTool
}) {
  const enabledServerCount = (servers || []).filter((server) => server.enabled).length;
  const enabledToolCount = (tools || []).filter((tool) => tool.enabled).length;
  const transport = normalizeMcpTransport(serverForm.transport);
  const healthStatus = health?.overallStatus || "unknown";
  const callableTools = (tools || []).filter((tool) => tool.enabled !== false);

  return (
    <section className="mcp-panel">
      <div className="mcp-panel-head">
        <div>
          <strong>MCP 管理</strong>
          <span>注册服务、发现工具、缓存工具，并支持 HTTP、SSE、STDIO 三种接入方式</span>
        </div>
        <div className="mcp-panel-stats">
          <span>服务 <b>{enabledServerCount}/{servers.length}</b></span>
          <span>工具 <b>{enabledToolCount}/{tools.length}</b></span>
          <button type="button" onClick={() => onRefresh().catch(() => {})} disabled={loading}>
            {loading ? <Loader2 size={14} className="spin" /> : <RotateCcw size={14} />}
            刷新
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

      <div className="mcp-ops-grid">
        <div className="mcp-ops-card">
          <div className="mcp-ops-head">
            <strong>运行自检</strong>
            <span className={`mcp-health-pill ${healthStatus}`}>{healthStatus}</span>
          </div>
          <div className="mcp-health-stats">
            <span>服务 <b>{health?.enabledServerCount ?? enabledServerCount}/{health?.serverCount ?? servers.length}</b></span>
            <span>工具 <b>{health?.enabledToolCount ?? enabledToolCount}/{health?.toolCount ?? tools.length}</b></span>
            <span>可用 <b>{health?.readyServerCount ?? 0}</b></span>
          </div>
          <button type="button" onClick={onCheckHealth} disabled={actionKey === "health"}>
            {actionKey === "health" ? <Loader2 size={14} className="spin" /> : <Activity size={14} />}
            健康检查
          </button>
          {Array.isArray(health?.servers) && health.servers.length > 0 && (
            <div className="mcp-health-list">
              {health.servers.slice(0, 4).map((server) => (
                <span key={server.serverId} className={server.status}>
                  {server.serverId} · {server.status} · {server.toolCount || 0} 个工具 · {cacheStatusText(server)}
                </span>
              ))}
            </div>
          )}
        </div>

        <div className="mcp-ops-card">
          <div className="mcp-ops-head">
            <strong>配置迁移</strong>
            <span>导出后可直接作为导入 JSON</span>
          </div>
          <div className="mcp-ops-actions">
            <button type="button" onClick={onExportState} disabled={actionKey === "export"}>
              {actionKey === "export" ? <Loader2 size={14} className="spin" /> : <Download size={14} />}
              导出
            </button>
            <button type="button" onClick={onImportState} disabled={actionKey === "import"}>
              {actionKey === "import" ? <Loader2 size={14} className="spin" /> : <Upload size={14} />}
              导入
            </button>
          </div>
          <textarea
            value={importPayload}
            onChange={(event) => setImportPayload(event.target.value)}
            rows={6}
            spellCheck={false}
            placeholder="粘贴导出的 MCP 配置 JSON"
          />
          {exportPayload && <small>最近已导出 {exportPayload.length} 个字符</small>}
        </div>

        <div className="mcp-ops-card">
          <div className="mcp-ops-head">
            <strong>工具试调用</strong>
            <span>用于验证工具入参和返回结构</span>
          </div>
          <select value={toolCallName} onChange={(event) => setToolCallName(event.target.value)}>
            <option value="">选择工具</option>
            {callableTools.map((tool) => (
              <option key={tool.qualifiedName || `${tool.serverId}.${tool.toolName}`} value={tool.qualifiedName || `${tool.serverId}.${tool.toolName}`}>
                {tool.qualifiedName || tool.toolName}
              </option>
            ))}
          </select>
          <textarea
            value={toolCallPayload}
            onChange={(event) => setToolCallPayload(event.target.value)}
            rows={4}
            spellCheck={false}
            placeholder='{"arguments":{}}'
          />
          <button type="button" onClick={onCallTool} disabled={actionKey === "call-tool" || !toolCallName}>
            {actionKey === "call-tool" ? <Loader2 size={14} className="spin" /> : <Play size={14} />}
            试调用
          </button>
          {toolCallResult && <pre>{toolCallResult}</pre>}
        </div>
      </div>

      <div className="mcp-panel-grid">
        <form className="mcp-editor" onSubmit={onRegister}>
          <div className="mcp-editor-head">
            <strong>服务注册</strong>
            <label>
              <input
                type="checkbox"
                checked={serverForm.enabled}
                onChange={(event) => setServerForm({ ...serverForm, enabled: event.target.checked })}
              />
              启用
            </label>
          </div>
          <div className="mcp-form-grid">
            <input
              value={serverForm.serverId}
              onChange={(event) => setServerForm({ ...serverForm, serverId: event.target.value })}
              placeholder="服务标识"
              required
            />
            <input
              value={serverForm.name}
              onChange={(event) => setServerForm({ ...serverForm, name: event.target.value })}
              placeholder="服务名称"
            />
            <select
              value={transport}
              onChange={(event) => setServerForm({ ...serverForm, transport: event.target.value })}
            >
              {MCP_TRANSPORT_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </select>
            <input
              value={serverForm.endpoint}
              onChange={(event) => setServerForm({ ...serverForm, endpoint: event.target.value })}
              placeholder={transport === "stdio" ? "可留空，自动使用 stdio://serverId" : "服务地址"}
              required={transport !== "stdio"}
            />
            <input
              value={serverForm.timeoutSeconds}
              onChange={(event) => setServerForm({ ...serverForm, timeoutSeconds: event.target.value })}
              type="number"
              min="1"
              placeholder="超时秒数"
            />
            <input
              value={serverForm.toolCacheTtlSeconds}
              onChange={(event) => setServerForm({ ...serverForm, toolCacheTtlSeconds: event.target.value })}
              type="number"
              min="1"
              placeholder="工具缓存有效期秒数"
            />
          </div>

          {transport === "streamable_http" && (
            <label className="mcp-inline-check">
              <input
                type="checkbox"
                checked={serverForm.openConnectionOnStartup !== false}
                onChange={(event) => setServerForm({ ...serverForm, openConnectionOnStartup: event.target.checked })}
              />
              启动时预连接
            </label>
          )}

          {transport === "sse" && (
            <div className="mcp-form-grid">
              <input
                value={serverForm.baseUri}
                onChange={(event) => setServerForm({ ...serverForm, baseUri: event.target.value })}
                placeholder="SSE baseUri"
              />
              <input
                value={serverForm.sseEndpoint}
                onChange={(event) => setServerForm({ ...serverForm, sseEndpoint: event.target.value })}
                placeholder="SSE endpoint，如 /sse"
              />
            </div>
          )}

          {transport === "stdio" && (
            <>
              <input
                value={serverForm.command}
                onChange={(event) => setServerForm({ ...serverForm, command: event.target.value })}
                placeholder="STDIO command，如 npx"
                required
              />
              <textarea
                value={serverForm.argsText}
                onChange={(event) => setServerForm({ ...serverForm, argsText: event.target.value })}
                rows={3}
                spellCheck={false}
                placeholder="STDIO args，每行一个参数或 JSON 数组"
              />
              <textarea
                value={serverForm.envText}
                onChange={(event) => setServerForm({ ...serverForm, envText: event.target.value })}
                rows={3}
                spellCheck={false}
                placeholder="STDIO env，JSON 对象"
              />
            </>
          )}

          <textarea
            value={serverForm.headersText}
            onChange={(event) => setServerForm({ ...serverForm, headersText: event.target.value })}
            rows={3}
            spellCheck={false}
            placeholder="请求头，JSON 对象"
          />

          <button className="mcp-primary-btn" type="submit" disabled={actionKey === "register"}>
            {actionKey === "register" ? <Loader2 size={15} className="spin" /> : <Plus size={15} />}
            注册或更新服务
          </button>
        </form>

        <form className="mcp-editor" onSubmit={onCacheTools}>
          <div className="mcp-editor-head">
            <strong>工具缓存</strong>
            <span>写入服务已发现工具</span>
          </div>
          <input
            value={cacheServerId}
            onChange={(event) => setCacheServerId(event.target.value)}
            placeholder="服务标识"
            list="mcp-server-options"
          />
          <datalist id="mcp-server-options">
            {(servers || []).map((server) => (
              <option key={server.serverId} value={server.serverId} />
            ))}
          </datalist>
          <textarea
            value={toolPayload}
            onChange={(event) => setToolPayload(event.target.value)}
            rows={7}
            spellCheck={false}
          />
          <button className="mcp-primary-btn" type="submit" disabled={actionKey === "cache-tools"}>
            {actionKey === "cache-tools" ? <Loader2 size={15} className="spin" /> : <Settings size={15} />}
            更新工具缓存
          </button>
        </form>
      </div>

      <div className="mcp-lists">
        <div className="mcp-list-block">
          <div className="mcp-list-title">服务</div>
          {(servers || []).length === 0 && <p className="mcp-empty">暂无服务</p>}
          {(servers || []).map((server) => (
            <div className="mcp-server-row" key={server.serverId}>
              <div>
                <b>{server.name || server.serverId}</b>
                <span>{server.serverId} · {server.transport || "streamable_http"}</span>
                <small>{server.endpoint}</small>
                <small>缓存 {cacheStatusText(server)}</small>
              </div>
              <div>
                <em>{server.toolCount || 0} 个工具</em>
                <button
                  type="button"
                  onClick={() => onDiscoverTools(server)}
                  disabled={actionKey === `discover-${server.serverId}`}
                >
                  {actionKey === `discover-${server.serverId}`
                    ? <Loader2 size={13} className="spin" />
                    : <Globe2 size={13} />}
                  发现工具
                </button>
                <button
                  type="button"
                  className={server.enabled ? "enabled" : ""}
                  onClick={() => onToggleServer(server)}
                  disabled={actionKey === `server-${server.serverId}`}
                >
                  {actionKey === `server-${server.serverId}` ? "处理中" : server.enabled ? "停用" : "启用"}
                </button>
              </div>
            </div>
          ))}
        </div>

        <div className="mcp-list-block">
          <div className="mcp-list-title">工具</div>
          {(tools || []).length === 0 && <p className="mcp-empty">暂无缓存工具</p>}
          <div className="mcp-tool-cloud">
            {(tools || []).map((tool) => (
              <span key={tool.qualifiedName || `${tool.serverId}.${tool.toolName}`} className={tool.enabled ? "enabled" : ""}>
                <b>{tool.qualifiedName || tool.toolName}</b>
                {tool.description && <small>{tool.description}</small>}
              </span>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}
