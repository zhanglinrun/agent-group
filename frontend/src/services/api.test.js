import { beforeEach, describe, expect, it, vi } from "vitest";
import { Buffer } from "node:buffer";

import {
  cacheMcpTools,
  callMcpTool,
  clearUserAuth,
  clearAdminAuth,
  deleteAgentAdminConfig,
  deleteKnowledgeDocument,
  discoverMcpTools,
  enableAgentAdminConfig,
  enableMcpServer,
  exportAgentAdminState,
  exportMcpState,
  generateWorkspaceImage,
  getKnowledgeDocumentFullContent,
  getKnowledgeFragments,
  importAgentAdminState,
  importMcpState,
  queryMcpHealth,
  queryAgentAdminConfigs,
  queryAgentAdminRuntimeSnapshot,
  queryAgentAdminStatistics,
  queryAcademicRunDetail,
  queryAcademicSessionDetail,
  queryAcademicSessions,
  queryMcpServers,
  queryMcpTools,
  queryWorkspaceDataCatalog,
  queryWorkspaceDataHistory,
  queryWorkspaceImageHistory,
  queryWorkspaceMragHistory,
  registerMcpServer,
  requestAcademicResumeStream,
  requestAcademicStream,
  runWorkspaceData,
  runWorkspaceMrag,
  saveAdminAuth,
  saveUserAuth,
  uploadKnowledgeWebUrl,
  upsertAgentAdminConfig
} from "./api";

function createStorage() {
  const values = new Map();
  return {
    getItem: vi.fn((key) => values.get(key) || null),
    setItem: vi.fn((key, value) => values.set(key, String(value))),
    removeItem: vi.fn((key) => values.delete(key)),
    clear: vi.fn(() => values.clear())
  };
}

function jsonResponse(payload = { code: "0000", data: {} }) {
  return {
    ok: true,
    status: 200,
    headers: {
      get: (name) => name.toLowerCase() === "content-type" ? "application/json" : ""
    },
    json: vi.fn(async () => payload),
    text: vi.fn(async () => JSON.stringify(payload))
  };
}

function streamResponse(chunks = []) {
  const encoder = new TextEncoder();
  let index = 0;
  const reader = {
    read: vi.fn(async () => {
      if (index >= chunks.length) {
        return { done: true };
      }
      return { done: false, value: encoder.encode(chunks[index++]) };
    }),
    cancel: vi.fn(async () => {}),
    releaseLock: vi.fn()
  };
  return {
    ok: true,
    status: 200,
    headers: {
      get: (name) => name.toLowerCase() === "content-type" ? "text/event-stream" : ""
    },
    body: {
      getReader: () => reader
    }
  };
}

describe("mcp admin api client", () => {
  beforeEach(() => {
    const sessionStorage = createStorage();
    const localStorage = createStorage();
    globalThis.localStorage = localStorage;
    globalThis.window = {
      sessionStorage,
      btoa: (value) => Buffer.from(value, "utf8").toString("base64")
    };
    globalThis.fetch = vi.fn(async () => jsonResponse());
    clearAdminAuth();
    clearUserAuth();
  });

  it("adds admin basic auth when querying mcp servers", async () => {
    saveAdminAuth("ops", "secret");

    await queryMcpServers();

    expect(fetch).toHaveBeenCalledWith("/api/v1/mcp/admin/servers", expect.objectContaining({
      method: "GET",
      headers: expect.objectContaining({
        Authorization: "Basic b3BzOnNlY3JldA=="
      })
    }));
  });

  it("wires register enable cache and discover requests", async () => {
    saveAdminAuth("ops", "secret");

    await registerMcpServer({ serverId: "research", endpoint: "http://localhost:8090/mcp" });
    await enableMcpServer("research", false);
    await cacheMcpTools("research", { tools: [{ name: "web_fetch" }] });
    await discoverMcpTools("research", { cache: true });

    expect(fetch).toHaveBeenNthCalledWith(1, "/api/v1/mcp/admin/servers", expect.objectContaining({
      method: "POST",
      body: JSON.stringify({ serverId: "research", endpoint: "http://localhost:8090/mcp" }),
      headers: expect.objectContaining({ "Content-Type": "application/json" })
    }));
    expect(fetch).toHaveBeenNthCalledWith(2, "/api/v1/mcp/admin/servers/research/enabled", expect.objectContaining({
      method: "POST",
      body: JSON.stringify({ enabled: false })
    }));
    expect(fetch).toHaveBeenNthCalledWith(3, "/api/v1/mcp/admin/servers/research/tools/cache", expect.objectContaining({
      method: "POST",
      body: JSON.stringify({ tools: [{ name: "web_fetch" }] })
    }));
    expect(fetch).toHaveBeenNthCalledWith(4, "/api/v1/mcp/admin/servers/research/tools/discover", expect.objectContaining({
      method: "POST",
      body: JSON.stringify({ cache: true })
    }));
  });

  it("builds mcp tool query parameters", async () => {
    saveAdminAuth("ops", "secret");

    await queryMcpTools({ serverId: "data-source", enabledOnly: true });

    expect(fetch).toHaveBeenCalledWith(
      "/api/v1/mcp/admin/tools?serverId=data-source&enabledOnly=true",
      expect.objectContaining({ method: "GET" })
    );
  });

  it("wires mcp health export import and tool call APIs", async () => {
    saveAdminAuth("ops", "secret");

    await queryMcpHealth();
    await exportMcpState();
    await importMcpState({ snapshot: { servers: [] }, replace: true });
    await callMcpTool("research.web_fetch", { arguments: { query: "agent" } });

    expect(fetch).toHaveBeenNthCalledWith(1, "/api/v1/mcp/admin/health", expect.objectContaining({
      method: "GET"
    }));
    expect(fetch).toHaveBeenNthCalledWith(2, "/api/v1/mcp/admin/export", expect.objectContaining({
      method: "GET"
    }));
    expect(fetch).toHaveBeenNthCalledWith(3, "/api/v1/mcp/admin/import", expect.objectContaining({
      method: "POST",
      body: JSON.stringify({ snapshot: { servers: [] }, replace: true })
    }));
    expect(fetch).toHaveBeenNthCalledWith(4, "/api/v1/mcp/admin/tools/research.web_fetch/call", expect.objectContaining({
      method: "POST",
      body: JSON.stringify({ arguments: { query: "agent" } })
    }));
  });

  it("wires agent admin config requests with admin basic auth", async () => {
    saveAdminAuth("ops", "secret");

    await queryAgentAdminConfigs({ category: "model", enabledOnly: true });
    await upsertAgentAdminConfig({
      configId: "default-model",
      category: "model",
      name: "Default Model"
    });
    await enableAgentAdminConfig("default-model", false);
    await deleteAgentAdminConfig("default-model");
    await exportAgentAdminState();
    await importAgentAdminState({
      replace: false,
      configs: [{ configId: "trade-prompt", category: "system_prompt" }]
    });
    await queryAgentAdminStatistics();
    await queryAgentAdminRuntimeSnapshot();

    const authMatcher = expect.objectContaining({
      Authorization: "Basic b3BzOnNlY3JldA=="
    });
    expect(fetch).toHaveBeenNthCalledWith(1, "/api/v1/agent/admin/configs?category=model&enabledOnly=true", expect.objectContaining({
      method: "GET",
      headers: authMatcher
    }));
    expect(fetch).toHaveBeenNthCalledWith(2, "/api/v1/agent/admin/configs", expect.objectContaining({
      method: "POST",
      headers: expect.objectContaining({
        Authorization: "Basic b3BzOnNlY3JldA==",
        "Content-Type": "application/json"
      }),
      body: JSON.stringify({
        configId: "default-model",
        category: "model",
        name: "Default Model"
      })
    }));
    expect(fetch).toHaveBeenNthCalledWith(3, "/api/v1/agent/admin/configs/default-model/enabled", expect.objectContaining({
      method: "POST",
      headers: expect.objectContaining({
        Authorization: "Basic b3BzOnNlY3JldA==",
        "Content-Type": "application/json"
      }),
      body: JSON.stringify({ enabled: false })
    }));
    expect(fetch).toHaveBeenNthCalledWith(4, "/api/v1/agent/admin/configs/default-model", expect.objectContaining({
      method: "DELETE",
      headers: authMatcher
    }));
    expect(fetch).toHaveBeenNthCalledWith(5, "/api/v1/agent/admin/export", expect.objectContaining({
      method: "GET",
      headers: authMatcher
    }));
    expect(fetch).toHaveBeenNthCalledWith(6, "/api/v1/agent/admin/import", expect.objectContaining({
      method: "POST",
      headers: expect.objectContaining({
        Authorization: "Basic b3BzOnNlY3JldA==",
        "Content-Type": "application/json"
      }),
      body: JSON.stringify({
        replace: false,
        configs: [{ configId: "trade-prompt", category: "system_prompt" }]
      })
    }));
    expect(fetch).toHaveBeenNthCalledWith(7, "/api/v1/agent/admin/statistics", expect.objectContaining({
      method: "GET",
      headers: authMatcher
    }));
    expect(fetch).toHaveBeenNthCalledWith(8, "/api/v1/agent/admin/runtime-snapshot", expect.objectContaining({
      method: "GET",
      headers: authMatcher
    }));
  });

  it("sends output style with academic stream requests", async () => {
    saveUserAuth({ token: "user-token" });
    fetch.mockResolvedValue(streamResponse());

    requestAcademicStream({
      sessionId: "S1001",
      question: "讲一下项目亮点",
      taskType: "deep",
      webSearchEnabled: true,
      outputStyle: "interview"
    });
    requestAcademicResumeStream("S1001", {}, true, "report");

    expect(fetch).toHaveBeenNthCalledWith(1, "/api/v1/academic/stream", expect.objectContaining({
      method: "POST",
      headers: expect.objectContaining({
        Authorization: "Bearer user-token",
        "Content-Type": "application/json"
      }),
      body: JSON.stringify({
        sessionId: "S1001",
        question: "讲一下项目亮点",
        taskType: "deep",
        fileId: "",
        imageUrl: "",
        imageName: "",
        webSearchEnabled: true,
        outputStyle: "interview"
      })
    }));
    expect(fetch).toHaveBeenNthCalledWith(2, "/api/v1/academic/resume", expect.objectContaining({
      method: "POST",
      body: JSON.stringify({
        sessionId: "S1001",
        webSearchEnabled: true,
        outputStyle: "report"
      })
    }));
  });

  it("wires workspace image generation and history APIs with user token", async () => {
    saveUserAuth({ token: "user-token" });

    await generateWorkspaceImage({
      sessionId: "S1001",
      prompt: "生成拼团活动主图",
      size: "1024x1024",
      batchCount: 2
    });
    await queryWorkspaceImageHistory({ sessionId: "S1001", limit: 8 });

    expect(fetch).toHaveBeenNthCalledWith(1, "/api/v1/academic/workspace/image/generate", expect.objectContaining({
      method: "POST",
      headers: expect.objectContaining({
        Authorization: "Bearer user-token",
        "Content-Type": "application/json"
      }),
      body: JSON.stringify({
        sessionId: "S1001",
        prompt: "生成拼团活动主图",
        mode: "generate",
        size: "1024x1024",
        batchCount: 2,
        sourceFileIds: [],
        sourceImageUrls: [],
        maskImageUrls: []
      })
    }));
    expect(fetch).toHaveBeenNthCalledWith(
      2,
      "/api/v1/academic/workspace/image/history?sessionId=S1001&limit=8",
      expect.objectContaining({
        method: "GET",
        headers: expect.objectContaining({
          Authorization: "Bearer user-token"
        })
      })
    );
  });

  it("wires academic session and run detail APIs with user token", async () => {
    saveUserAuth({ token: "user-token" });

    await queryAcademicSessions(12);
    await queryAcademicSessionDetail("AS 1001");
    await queryAcademicRunDetail("RUN 1001");

    expect(fetch).toHaveBeenNthCalledWith(
      1,
      "/api/v1/academic/sessions?limit=12",
      expect.objectContaining({
        method: "GET",
        headers: expect.objectContaining({
          Authorization: "Bearer user-token"
        })
      })
    );
    expect(fetch).toHaveBeenNthCalledWith(
      2,
      "/api/v1/academic/sessions/AS%201001",
      expect.objectContaining({
        method: "GET",
        headers: expect.objectContaining({
          Authorization: "Bearer user-token"
        })
      })
    );
    expect(fetch).toHaveBeenNthCalledWith(
      3,
      "/api/v1/academic/runs/RUN%201001",
      expect.objectContaining({
        method: "GET",
        headers: expect.objectContaining({
          Authorization: "Bearer user-token"
        })
      })
    );
  });

  it("wires workspace data run and history APIs with user token", async () => {
    saveUserAuth({ token: "user-token" });

    await runWorkspaceData({
      sessionId: "D1001",
      question: "count paid orders",
      rows: [{ pay_status: "PAY_SUCCESS", count: 12 }],
      columns: ["pay_status", "count"],
      modelCodeList: ["trade_order"]
    });
    await queryWorkspaceDataCatalog();
    await queryWorkspaceDataHistory({ sessionId: "D1001", limit: 5 });

    expect(fetch).toHaveBeenNthCalledWith(1, "/api/v1/academic/workspace/data/run", expect.objectContaining({
      method: "POST",
      headers: expect.objectContaining({
        Authorization: "Bearer user-token",
        "Content-Type": "application/json"
      }),
      body: JSON.stringify({
        sessionId: "D1001",
        question: "count paid orders",
        rows: [{ pay_status: "PAY_SUCCESS", count: 12 }],
        columns: ["pay_status", "count"],
        modelCodeList: ["trade_order"],
        schemaInfo: [],
        businessKnowledge: "",
        dbType: "mysql",
        useVector: true,
        useElastic: false,
        topK: 5,
        maxSteps: 10,
        includeTableRag: true,
        includeNl2Sql: true,
        includeAnalysis: true,
        metadata: {}
      })
    }));
    expect(fetch).toHaveBeenNthCalledWith(
      2,
      "/api/v1/academic/workspace/data/catalog",
      expect.objectContaining({
        method: "GET",
        headers: expect.objectContaining({
          Authorization: "Bearer user-token"
        })
      })
    );
    expect(fetch).toHaveBeenNthCalledWith(
      3,
      "/api/v1/academic/workspace/data/history?sessionId=D1001&limit=5",
      expect.objectContaining({
        method: "GET",
        headers: expect.objectContaining({
          Authorization: "Bearer user-token"
        })
      })
    );
  });

  it("wires workspace mrag run and history APIs with user token", async () => {
    saveUserAuth({ token: "user-token" });

    await runWorkspaceMrag({
      sessionId: "M1001",
      question: "cross check paid orders",
      imageUrls: ["https://example.com/funnel.png"],
      modelCodeList: ["trade_order"]
    });
    await queryWorkspaceMragHistory({ sessionId: "M1001", limit: 6 });

    expect(fetch).toHaveBeenNthCalledWith(1, "/api/v1/academic/workspace/mrag/run", expect.objectContaining({
      method: "POST",
      headers: expect.objectContaining({
        Authorization: "Bearer user-token",
        "Content-Type": "application/json"
      }),
      body: JSON.stringify({
        sessionId: "M1001",
        question: "cross check paid orders",
        text: "",
        imageUrls: ["https://example.com/funnel.png"],
        fileUrls: [],
        modelCodeList: ["trade_order"],
        sourceTypes: [],
        topK: 5,
        maxResults: 5,
        includeMultimodal: true,
        includeTableRag: true,
        includeDeepSearch: true,
        useVector: true,
        useElastic: false,
        metadata: {}
      })
    }));
    expect(fetch).toHaveBeenNthCalledWith(
      2,
      "/api/v1/academic/workspace/mrag/history?sessionId=M1001&limit=6",
      expect.objectContaining({
        method: "GET",
        headers: expect.objectContaining({
          Authorization: "Bearer user-token"
        })
      })
    );
  });

  it("wires knowledge fragment query with admin basic auth", async () => {
    saveAdminAuth("ops", "secret");

    await getKnowledgeFragments("DOC 1001");

    expect(fetch).toHaveBeenCalledWith(
      "/api/v1/knowledge/document/fragments?documentId=DOC%201001",
      expect.objectContaining({
        method: "GET",
        headers: expect.objectContaining({
          Authorization: "Basic b3BzOnNlY3JldA=="
        })
      })
    );
  });

  it("wires knowledge web import full content and delete APIs with admin basic auth", async () => {
    saveAdminAuth("ops", "secret");

    await uploadKnowledgeWebUrl({
      url: "https://example.com/article",
      goodsId: "global",
      documentName: "Article"
    });
    await getKnowledgeDocumentFullContent("DOC 1001");
    await deleteKnowledgeDocument("DOC 1001");

    expect(fetch).toHaveBeenNthCalledWith(1, "/api/v1/knowledge/document/upload-web-url", expect.objectContaining({
      method: "POST",
      headers: expect.objectContaining({
        Authorization: "Basic b3BzOnNlY3JldA==",
        "Content-Type": "application/json"
      }),
      body: JSON.stringify({
        url: "https://example.com/article",
        goodsId: "global",
        documentName: "Article",
        documentType: "MRAG Web Page",
        knowledgeVersion: ""
      })
    }));
    expect(fetch).toHaveBeenNthCalledWith(
      2,
      "/api/v1/knowledge/document/full-content?documentId=DOC%201001",
      expect.objectContaining({
        method: "GET",
        headers: expect.objectContaining({
          Authorization: "Basic b3BzOnNlY3JldA=="
        })
      })
    );
    expect(fetch).toHaveBeenNthCalledWith(
      3,
      "/api/v1/knowledge/document/DOC%201001",
      expect.objectContaining({
        method: "DELETE",
        headers: expect.objectContaining({
          Authorization: "Basic b3BzOnNlY3JldA=="
        })
      })
    );
  });
});
