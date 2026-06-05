type UnknownMap = Record<string, unknown>;

export type McpRuntimeStatus = "empty" | "disabled" | "ready" | "degraded";

export interface McpRuntimeMetric {
  key: string;
  label: string;
  value: string;
  tone?: "normal" | "good" | "warn";
}

export interface McpRuntimeSummary {
  status: McpRuntimeStatus;
  statusLabel: string;
  title: string;
  metrics: McpRuntimeMetric[];
  alerts: string[];
  actions: string[];
}

export interface McpRuntimeSummaryInput {
  servers?: unknown[];
  tools?: unknown[];
  health?: unknown;
}

export type McpToolAvailabilityState = "callable" | "server-missing" | "server-disabled" | "tool-disabled" | "cache-expired";

export interface McpToolAvailability {
  callable: boolean;
  state: McpToolAvailabilityState;
  label: string;
  className: string;
}

function asObject(value: unknown): UnknownMap {
  return value && typeof value === "object" && !Array.isArray(value)
    ? value as UnknownMap
    : {};
}

function asArray(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

function text(value: unknown): string {
  return String(value ?? "").trim();
}

function bool(value: unknown): boolean {
  if (typeof value === "boolean") return value;
  const normalized = text(value).toLowerCase();
  return normalized === "true" || normalized === "1" || normalized === "yes";
}

function notFalse(value: unknown): boolean {
  if (typeof value === "boolean") return value;
  const normalized = text(value).toLowerCase();
  return !["false", "0", "no"].includes(normalized);
}

function numberValue(value: unknown): number {
  const parsed = Number(value ?? 0);
  return Number.isFinite(parsed) ? parsed : 0;
}

function numberOr(value: unknown, fallback: number): number {
  if (!text(value)) {
    return fallback;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function normalizeStatus(value: unknown, fallback: McpRuntimeStatus): McpRuntimeStatus {
  const normalized = text(value).toLowerCase();
  if (normalized === "ready" || normalized === "degraded" || normalized === "disabled" || normalized === "empty") {
    return normalized;
  }
  return fallback;
}

function serverName(server: UnknownMap): string {
  return text(server.name) || text(server.serverId) || "未命名服务";
}

function unique(values: string[]): string[] {
  return [...new Set(values.map((item) => item.trim()).filter(Boolean))];
}

function statusLabel(status: McpRuntimeStatus): string {
  return ({
    empty: "未配置",
    disabled: "已停用",
    ready: "可用",
    degraded: "需处理"
  } as Record<McpRuntimeStatus, string>)[status];
}

function titleForStatus(status: McpRuntimeStatus): string {
  return ({
    empty: "还没有可用的 MCP 服务",
    disabled: "MCP 服务已配置但未启用",
    ready: "MCP 运行时已就绪",
    degraded: "MCP 运行时需要处理"
  } as Record<McpRuntimeStatus, string>)[status];
}

function cacheMetric(servers: UnknownMap[]): string {
  const fresh = servers.filter((server) => ["fresh", "unbounded"].includes(text(server.cacheStatus))).length;
  const expired = servers.filter((server) => text(server.cacheStatus) === "expired").length;
  const empty = servers.filter((server) => text(server.cacheStatus) === "empty" || !text(server.cacheStatus)).length;
  const parts = [
    fresh ? `${fresh} 有效` : "",
    expired ? `${expired} 过期` : "",
    empty ? `${empty} 未缓存` : ""
  ].filter(Boolean);
  return parts.length ? parts.join(" / ") : "无缓存信息";
}

function transportMetric(servers: UnknownMap[]): string {
  const transports = unique(servers.map((server) => text(server.transport) || "streamable_http"));
  return transports.length ? transports.slice(0, 3).join(" / ") : "-";
}

export function resolveMcpToolAvailability(tool: unknown, servers: unknown[] = []): McpToolAvailability {
  const toolData = asObject(tool);
  const serverId = text(toolData.serverId) || text(toolData.qualifiedName).split(".")[0];
  const server = servers.map(asObject).find((item) => text(item.serverId) === serverId);
  if (!serverId || !server) {
    return { callable: false, state: "server-missing", label: "服务未同步", className: "missing" };
  }
  if (!notFalse(server.enabled)) {
    return { callable: false, state: "server-disabled", label: "服务已停用", className: "disabled" };
  }
  if (!notFalse(toolData.enabled)) {
    return { callable: false, state: "tool-disabled", label: "工具已停用", className: "disabled" };
  }
  if (text(server.cacheStatus) === "expired" || bool(server.cacheExpired)) {
    return { callable: false, state: "cache-expired", label: "缓存过期，需重新发现", className: "expired" };
  }
  return { callable: true, state: "callable", label: "可供 Agent 使用", className: "enabled" };
}

function fallbackStatus(input: {
  serverCount: number;
  enabledServerCount: number;
  readyServerCount: number;
  degradedServerCount: number;
  enabledToolCount: number;
  expiredCacheCount: number;
  emptyEnabledCacheCount: number;
}): McpRuntimeStatus {
  if (input.serverCount === 0) return "empty";
  if (input.enabledServerCount === 0) return "disabled";
  if (
    input.degradedServerCount > 0
    || input.readyServerCount < input.enabledServerCount
    || input.enabledToolCount === 0
    || input.expiredCacheCount > 0
    || input.emptyEnabledCacheCount > 0
  ) {
    return "degraded";
  }
  return "ready";
}

export function buildMcpRuntimeSummary(input: McpRuntimeSummaryInput = {}): McpRuntimeSummary {
  const health = asObject(input.health);
  const servers = asArray(input.servers).map(asObject);
  const tools = asArray(input.tools).map(asObject);
  const healthServers = asArray(health.servers).map(asObject);
  const serverChecks = healthServers.length ? healthServers : servers;

  const serverCount = numberOr(health.serverCount, servers.length || serverChecks.length);
  const toolCount = numberOr(health.toolCount, tools.length);
  const enabledServerCount = numberOr(
    health.enabledServerCount,
    servers.filter((server) => bool(server.enabled)).length || serverChecks.filter((server) => bool(server.enabled)).length
  );
  const enabledToolCount = numberOr(
    health.enabledToolCount,
    tools.filter((tool) => bool(tool.enabled)).length
  );
  const readyServerCount = numberOr(
    health.readyServerCount,
    serverChecks.filter((server) => text(server.status) === "ready").length
  );
  const degradedServerCount = numberOr(
    health.degradedServerCount,
    serverChecks.filter((server) => text(server.status) === "degraded").length
  );
  const enabledServers = serverChecks.filter((server) => bool(server.enabled));
  const expiredCacheServers = enabledServers.filter((server) => text(server.cacheStatus) === "expired");
  const emptyCacheServers = enabledServers.filter((server) => {
    const cacheStatus = text(server.cacheStatus);
    return cacheStatus === "empty" || (!cacheStatus && numberValue(server.toolCount) === 0);
  });
  const noEnabledToolServers = enabledServers.filter((server) => (
    numberValue(server.toolCount) > 0 && numberValue(server.enabledToolCount) === 0
  ));

  const fallback = fallbackStatus({
    serverCount,
    enabledServerCount,
    readyServerCount,
    degradedServerCount,
    enabledToolCount,
    expiredCacheCount: expiredCacheServers.length,
    emptyEnabledCacheCount: emptyCacheServers.length
  });
  const status = normalizeStatus(health.overallStatus, fallback);

  const alerts = unique([
    serverCount === 0 ? "还没有注册 MCP 服务" : "",
    serverCount > 0 && enabledServerCount === 0 ? "所有 MCP 服务都处于停用状态" : "",
    expiredCacheServers.length ? `${expiredCacheServers.map(serverName).slice(0, 3).join("、")} 工具缓存已过期` : "",
    emptyCacheServers.length ? `${emptyCacheServers.map(serverName).slice(0, 3).join("、")} 尚未缓存工具` : "",
    noEnabledToolServers.length ? `${noEnabledToolServers.map(serverName).slice(0, 3).join("、")} 没有启用工具` : "",
    enabledServerCount > 0 && enabledToolCount === 0 ? "当前没有可供 Agent 使用的 MCP 工具" : ""
  ]);

  const actions = unique([
    serverCount === 0 ? "先注册一个 MCP 服务" : "",
    serverCount > 0 && enabledServerCount === 0 ? "启用至少一个 MCP 服务" : "",
    expiredCacheServers.length || emptyCacheServers.length ? "重新发现并缓存工具" : "",
    enabledServerCount > 0 && enabledToolCount === 0 ? "启用需要开放给 Agent 的工具" : "",
    status === "ready" ? "可以试调用工具或交给 Agent 使用" : ""
  ]);

  return {
    status,
    statusLabel: statusLabel(status),
    title: titleForStatus(status),
    metrics: [
      {
        key: "servers",
        label: "服务",
        value: `${enabledServerCount}/${serverCount}`,
        tone: status === "ready" ? "good" : serverCount === 0 || enabledServerCount === 0 ? "warn" : "normal"
      },
      {
        key: "tools",
        label: "工具",
        value: `${enabledToolCount}/${toolCount}`,
        tone: enabledToolCount > 0 ? "good" : "warn"
      },
      {
        key: "ready",
        label: "可用服务",
        value: `${readyServerCount}/${enabledServerCount || 0}`,
        tone: readyServerCount === enabledServerCount && enabledServerCount > 0 ? "good" : "warn"
      },
      {
        key: "cache",
        label: "缓存",
        value: cacheMetric(serverChecks),
        tone: expiredCacheServers.length || emptyCacheServers.length ? "warn" : "good"
      },
      {
        key: "transport",
        label: "接入",
        value: transportMetric(serverChecks)
      }
    ],
    alerts,
    actions
  };
}
