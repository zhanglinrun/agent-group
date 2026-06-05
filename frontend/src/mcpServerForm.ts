export type McpTransport = "streamable_http" | "sse" | "stdio";

export interface McpServerFormState {
  serverId: string;
  name: string;
  endpoint: string;
  transport: McpTransport | string;
  enabled: boolean;
  timeoutSeconds: string;
  headersText: string;
  command: string;
  argsText: string;
  envText: string;
  baseUri: string;
  sseEndpoint: string;
  openConnectionOnStartup: boolean;
}

export const MCP_TRANSPORT_OPTIONS: Array<{ value: McpTransport; label: string }> = [
  { value: "streamable_http", label: "Streamable HTTP" },
  { value: "sse", label: "SSE" },
  { value: "stdio", label: "STDIO" }
];

export const DEFAULT_MCP_SERVER_FORM: McpServerFormState = {
  serverId: "research",
  name: "Research Tools",
  endpoint: "http://localhost:8090/mcp",
  transport: "streamable_http",
  enabled: true,
  timeoutSeconds: "120",
  headersText: "",
  command: "",
  argsText: "",
  envText: "",
  baseUri: "",
  sseEndpoint: "",
  openConnectionOnStartup: true
};

export function normalizeMcpTransport(value: unknown): McpTransport {
  const text = String(value || "")
    .trim()
    .toLowerCase()
    .replace(/-/g, "_");
  if (text === "sse" || text === "stdio" || text === "streamable_http") {
    return text;
  }
  return "streamable_http";
}

export function buildMcpServerPayload(form: Partial<McpServerFormState> = {}) {
  const transport = normalizeMcpTransport(form.transport);
  const serverId = cleanText(form.serverId);
  const name = cleanText(form.name);
  const endpoint = resolveEndpoint(cleanText(form.endpoint), serverId, transport);
  const metadata = buildMetadata(form, transport);

  if (!serverId) {
    throw new Error("MCP server id is required");
  }
  if (!endpoint) {
    throw new Error("MCP endpoint is required");
  }

  return pruneEmpty({
    serverId,
    name,
    endpoint,
    transport,
    enabled: form.enabled !== false,
    metadata
  });
}

function buildMetadata(form: Partial<McpServerFormState>, transport: McpTransport) {
  const metadata: Record<string, unknown> = {};
  const timeoutSeconds = positiveNumber(form.timeoutSeconds);
  if (timeoutSeconds) {
    metadata.timeoutSeconds = timeoutSeconds;
  }

  const headers = parseJsonObject(form.headersText, "headers");
  if (Object.keys(headers).length) {
    metadata.headers = headers;
  }

  if (transport === "streamable_http") {
    metadata.openConnectionOnStartup = form.openConnectionOnStartup !== false;
  }

  if (transport === "stdio") {
    const command = cleanText(form.command);
    if (!command) {
      throw new Error("STDIO command is required");
    }
    metadata.command = command;
    const args = parseStringList(form.argsText);
    if (args.length) {
      metadata.args = args;
    }
    const env = parseJsonObject(form.envText, "env");
    if (Object.keys(env).length) {
      metadata.env = env;
    }
  }

  if (transport === "sse") {
    const baseUri = cleanText(form.baseUri);
    const sseEndpoint = cleanText(form.sseEndpoint);
    if (baseUri) {
      metadata.baseUri = baseUri;
    }
    if (sseEndpoint) {
      metadata.sseEndpoint = sseEndpoint;
    }
  }

  return metadata;
}

function resolveEndpoint(endpoint: string, serverId: string, transport: McpTransport) {
  if (endpoint) {
    return endpoint;
  }
  return transport === "stdio" && serverId ? `stdio://${serverId}` : "";
}

function positiveNumber(value: unknown) {
  const text = cleanText(value);
  if (!text) {
    return 0;
  }
  const parsed = Number(text);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : 0;
}

function parseJsonObject(value: unknown, label: string): Record<string, unknown> {
  const text = cleanText(value);
  if (!text) {
    return {};
  }
  const parsed = JSON.parse(text);
  if (!parsed || Array.isArray(parsed) || typeof parsed !== "object") {
    throw new Error(`${label} must be a JSON object`);
  }
  return parsed as Record<string, unknown>;
}

function parseStringList(value: unknown) {
  const text = cleanText(value);
  if (!text) {
    return [];
  }
  if (text.startsWith("[")) {
    const parsed = JSON.parse(text);
    if (!Array.isArray(parsed)) {
      throw new Error("args must be a JSON array or newline list");
    }
    return parsed.map((item) => cleanText(item)).filter(Boolean);
  }
  return text.split(/\r?\n/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function pruneEmpty<T extends Record<string, unknown>>(value: T): T {
  return Object.fromEntries(
    Object.entries(value).filter(([, item]) => {
      if (item == null || item === "") {
        return false;
      }
      if (typeof item === "object" && !Array.isArray(item)) {
        return Object.keys(item).length > 0;
      }
      return true;
    })
  ) as T;
}

function cleanText(value: unknown) {
  return value == null ? "" : String(value).trim();
}
