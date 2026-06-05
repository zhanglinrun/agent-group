import { describe, expect, it } from "vitest";

import {
  DEFAULT_MCP_SERVER_FORM,
  buildMcpServerPayload,
  normalizeMcpTransport
} from "./mcpServerForm";

describe("mcp server form payload", () => {
  it("normalizes transport aliases", () => {
    expect(normalizeMcpTransport("streamable-http")).toBe("streamable_http");
    expect(normalizeMcpTransport("SSE")).toBe("sse");
    expect(normalizeMcpTransport("stdio")).toBe("stdio");
    expect(normalizeMcpTransport("missing")).toBe("streamable_http");
  });

  it("builds streamable http registration payload", () => {
    expect(buildMcpServerPayload({
      ...DEFAULT_MCP_SERVER_FORM,
      headersText: "{\"Authorization\":\"Bearer demo\"}",
      openConnectionOnStartup: false
    })).toEqual({
      serverId: "research",
      name: "Research Tools",
      endpoint: "http://localhost:8090/mcp",
      transport: "streamable_http",
      enabled: true,
      metadata: {
        timeoutSeconds: 120,
        headers: { Authorization: "Bearer demo" },
        openConnectionOnStartup: false
      }
    });
  });

  it("builds sse registration payload", () => {
    expect(buildMcpServerPayload({
      serverId: "search",
      name: "Search MCP",
      endpoint: "http://localhost:8091/sse",
      transport: "sse",
      enabled: true,
      timeoutSeconds: "30",
      baseUri: "http://localhost:8091",
      sseEndpoint: "/sse"
    })).toEqual({
      serverId: "search",
      name: "Search MCP",
      endpoint: "http://localhost:8091/sse",
      transport: "sse",
      enabled: true,
      metadata: {
        timeoutSeconds: 30,
        baseUri: "http://localhost:8091",
        sseEndpoint: "/sse"
      }
    });
  });

  it("builds stdio registration payload with command args and env", () => {
    expect(buildMcpServerPayload({
      serverId: "local-tool",
      name: "Local Tool",
      endpoint: "",
      transport: "stdio",
      enabled: true,
      timeoutSeconds: "45",
      command: "npx",
      argsText: "-y\n@demo/mcp-server",
      envText: "{\"NODE_ENV\":\"test\"}"
    })).toEqual({
      serverId: "local-tool",
      name: "Local Tool",
      endpoint: "stdio://local-tool",
      transport: "stdio",
      enabled: true,
      metadata: {
        timeoutSeconds: 45,
        command: "npx",
        args: ["-y", "@demo/mcp-server"],
        env: { NODE_ENV: "test" }
      }
    });
  });

  it("rejects invalid metadata inputs", () => {
    expect(() => buildMcpServerPayload({
      serverId: "bad",
      endpoint: "http://localhost:8090/mcp",
      headersText: "[]"
    })).toThrow("headers must be a JSON object");

    expect(() => buildMcpServerPayload({
      serverId: "stdio",
      transport: "stdio"
    })).toThrow("STDIO command is required");
  });
});
