import { beforeEach, describe, expect, it, vi } from "vitest";
import { Buffer } from "node:buffer";

import {
  cacheMcpTools,
  callMcpTool,
  applyAgentWorkspacePatch,
  bindAgentWorkspaceFile,
  clearUserAuth,
  clearAdminAuth,
  createPayment,
  createAgentWorkspace,
  deleteAgentAdminConfig,
  deleteUserAgentMemory,
  disableUserAgentMemory,
  discoverMcpTools,
  enableAgentAdminConfig,
  enableMcpServer,
  exportAgentAdminState,
  exportMcpState,
  generateWorkspaceImage,
  getUserAuth,
  getUserModelConfig,
  login,
  importAgentAdminState,
  importMcpState,
  modelConfigReady,
  queryMcpHealth,
  queryTradeConsistency,
  queryAgentCapabilities,
  queryAgentAdminConfigs,
  queryAgentAdminRuntimeSnapshot,
  queryAgentAdminStatistics,
  queryAgentWorkspace,
  queryAgentWorkspaces,
  queryAgentRunDetail,
  queryAgentSessionDetail,
  queryAgentSessions,
  queryUserAgentMemories,
  queryMcpServers,
  queryMcpTools,
  queryWorkspaceDataCatalog,
  queryWorkspaceDataHistory,
  queryWorkspaceImageHistory,
  register,
  registerMcpServer,
  proposeAgentWorkspacePatch,
  requestAgentResumeStream,
  requestAgentStream,
  runWorkspaceData,
  saveAdminAuth,
  saveModelConfig,
  saveUserAuth,
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

function errorJsonResponse(status, payload = {}) {
  return {
    ok: false,
    status,
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

describe("auth api client", () => {
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

  it("stores docAI style access token login responses", async () => {
    globalThis.fetch = vi.fn(async () => jsonResponse({
      code: 200,
      data: {
        accessToken: "access-token",
        refreshToken: "refresh-token",
        user: {
          id: "U10001",
          username: "alice",
          role: "USER"
        }
      }
    }));

    const res = await login("alice", "Password1!");

    expect(res.data.token).toBe("access-token");
    expect(res.data.userId).toBe("U10001");
    expect(getUserAuth().token).toBe("access-token");
  });

  it("stores current register responses and normalizes user auth", async () => {
    globalThis.fetch = vi.fn(async () => jsonResponse({
      code: "0000",
      data: {
        token: "register-token",
        userId: "U20002",
        username: "bob"
      }
    }));

    const res = await register({ username: "bob", password: "123456", nickname: "", email: "" });

    expect(res.data.accessToken).toBe("register-token");
    expect(getUserAuth().username).toBe("bob");
  });

  it("clears stale user auth when a protected request returns 401", async () => {
    saveUserAuth({ token: "expired-token", userId: "U1", username: "demo" });
    globalThis.fetch = vi.fn(async () => errorJsonResponse(401, { info: "登录已失效，请重新登录" }));

    await expect(queryAgentSessions()).rejects.toThrow("登录已失效");

    expect(getUserAuth()).toBeNull();
  });
});

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

  it("wires trade consistency audit endpoint", async () => {
    saveAdminAuth("ops", "secret");

    await queryTradeConsistency({ orderId: "O10001", userId: "U10001", pageSize: 5 });

    expect(fetch).toHaveBeenCalledWith("/api/v1/trade/order/admin/audit", expect.objectContaining({
      method: "POST",
      headers: expect.objectContaining({
        Authorization: "Basic b3BzOnNlY3JldA==",
        "Content-Type": "application/json"
      }),
      body: JSON.stringify({ orderId: "O10001", userId: "U10001", pageSize: 5 })
    }));
  });

  it("adds user auth when querying agent capabilities", async () => {
    saveUserAuth({ token: "user-token" });

    await queryAgentCapabilities();

    expect(fetch).toHaveBeenCalledWith("/api/v1/agent/capabilities", expect.objectContaining({
      method: "GET",
      headers: expect.objectContaining({
        Authorization: "Bearer user-token"
      })
    }));
  });

  it("wires agent workspace APIs with user auth", async () => {
    saveUserAuth({ token: "user-token" });

    await createAgentWorkspace({ title: "AMR Paper" });
    await queryAgentWorkspaces(10);
    await queryAgentWorkspace("AP1001");
    await bindAgentWorkspaceFile("AP1001", { fileId: "FILE1001", folderType: "draftManuscripts" });
    await proposeAgentWorkspacePatch("AP1001", { fileId: "FILE1001", afterText: "new intro" });
    await applyAgentWorkspacePatch("AP1001", "PATCH1001");

    const userAuthMatcher = expect.objectContaining({ Authorization: "Bearer user-token" });
    expect(fetch).toHaveBeenNthCalledWith(1, "/api/v1/agent/workspaces", expect.objectContaining({
      method: "POST",
      headers: expect.objectContaining({
        Authorization: "Bearer user-token",
        "Content-Type": "application/json"
      }),
      body: JSON.stringify({ title: "AMR Paper" })
    }));
    expect(fetch).toHaveBeenNthCalledWith(2, "/api/v1/agent/workspaces?limit=10", expect.objectContaining({
      method: "GET",
      headers: userAuthMatcher
    }));
    expect(fetch).toHaveBeenNthCalledWith(3, "/api/v1/agent/workspaces/AP1001", expect.objectContaining({
      method: "GET",
      headers: userAuthMatcher
    }));
    expect(fetch).toHaveBeenNthCalledWith(4, "/api/v1/agent/workspaces/AP1001/files", expect.objectContaining({
      method: "POST",
      headers: expect.objectContaining({
        Authorization: "Bearer user-token",
        "Content-Type": "application/json"
      })
    }));
    expect(fetch).toHaveBeenNthCalledWith(5, "/api/v1/agent/workspaces/AP1001/patches", expect.objectContaining({
      method: "POST",
      headers: expect.objectContaining({
        Authorization: "Bearer user-token",
        "Content-Type": "application/json"
      })
    }));
    expect(fetch).toHaveBeenNthCalledWith(6, "/api/v1/agent/workspaces/AP1001/patches/PATCH1001/apply", expect.objectContaining({
      method: "POST",
      headers: userAuthMatcher
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
      configs: [{ configId: "general-prompt", category: "system_prompt" }]
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
        configs: [{ configId: "general-prompt", category: "system_prompt" }]
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

  it("sends web search flag with agent stream requests", async () => {
    saveUserAuth({ token: "user-token" });
    fetch.mockResolvedValue(streamResponse());

    requestAgentStream({
      sessionId: "S1001",
      question: "讲一下项目亮点",
      taskType: "deep",
      webSearchEnabled: true,
      continueTraceId: "TRACE-1"
    });
    requestAgentResumeStream("S1001", {}, true, "TRACE-1");

    expect(fetch).toHaveBeenNthCalledWith(1, "/api/v1/agent/stream", expect.objectContaining({
      method: "POST",
      headers: expect.objectContaining({
        Authorization: "Bearer user-token",
        "Content-Type": "application/json"
      }),
      body: JSON.stringify({
        sessionId: "S1001",
        projectId: "",
        threadId: "",
        question: "讲一下项目亮点",
        taskType: "deep",
        taskMode: "",
        fileId: "",
        selectedFileIds: [],
        imageUrl: "",
        imageName: "",
        webSearchEnabled: true,
        continueTraceId: "TRACE-1"
      })
    }));
    expect(fetch).toHaveBeenNthCalledWith(2, "/api/v1/agent/resume", expect.objectContaining({
      method: "POST",
      body: JSON.stringify({
        sessionId: "S1001",
        webSearchEnabled: true,
        continueTraceId: "TRACE-1"
      })
    }));
  });

  it("keeps custom model api key out of agent stream requests", async () => {
    saveUserAuth({ token: "user-token" });
    fetch.mockResolvedValue(streamResponse());

    requestAgentStream({
      sessionId: "S1002",
      question: "hello",
      taskType: "chat",
      modelConfig: {
        enabled: true,
        baseUrl: "https://example.com",
        apiKey: "sk-secret",
        model: "custom-model",
        keyMasked: "sk****cret"
      }
    });

    const body = JSON.parse(fetch.mock.calls[0][1].body);
    expect(body).not.toHaveProperty("llmApiKey");
    expect(body).not.toHaveProperty("llmBaseUrl");
    expect(body).not.toHaveProperty("llmModel");
  });

  it("reads and saves user model config with user token", async () => {
    saveUserAuth({ token: "user-token" });

    await getUserModelConfig();
    await saveModelConfig({
      enabled: true,
      textBaseUrl: "https://text.example.com",
      textApiKey: "sk-text-secret",
      textModel: "custom-text-model",
      imageBaseUrl: "https://image.example.com",
      imageApiKey: "sk-image-secret",
      imageModel: "custom-image-model"
    });

    expect(fetch).toHaveBeenNthCalledWith(1, "/api/v1/quota/model-config", expect.objectContaining({
      method: "GET",
      headers: expect.objectContaining({
        Authorization: "Bearer user-token"
      })
    }));
    expect(fetch).toHaveBeenNthCalledWith(2, "/api/v1/quota/model-config", expect.objectContaining({
      method: "POST",
      headers: expect.objectContaining({
        Authorization: "Bearer user-token",
        "Content-Type": "application/json"
      }),
      body: JSON.stringify({
        enabled: true,
        baseUrl: "https://text.example.com",
        apiKey: "sk-text-secret",
        model: "custom-text-model",
        textBaseUrl: "https://text.example.com",
        textApiKey: "sk-text-secret",
        textModel: "custom-text-model",
        imageBaseUrl: "https://image.example.com",
        imageApiKey: "sk-image-secret",
        imageModel: "custom-image-model"
      })
    }));
  });

  it("checks text and image model readiness independently", () => {
    expect(modelConfigReady({
      enabled: true,
      textBaseUrl: "https://text.example.com",
      textModel: "custom-text-model",
      textKeyMasked: "sk-t****cret"
    }, "text")).toBe(true);
    expect(modelConfigReady({
      enabled: true,
      textBaseUrl: "https://text.example.com",
      textModel: "custom-text-model",
      textKeyMasked: "sk-t****cret"
    }, "image")).toBe(false);
    expect(modelConfigReady({
      enabled: true,
      imageBaseUrl: "https://image.example.com",
      imageModel: "custom-image-model",
      imageKeyMasked: "sk-i****cret"
    }, "image")).toBe(true);
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

    expect(fetch).toHaveBeenNthCalledWith(1, "/api/v1/agent/workspaces/image/generate", expect.objectContaining({
      method: "POST",
      headers: expect.objectContaining({
        Authorization: "Bearer user-token",
        "Content-Type": "application/json"
      }),
      body: JSON.stringify({
        sessionId: "S1001",
        prompt: "生成拼团活动主图",
        mode: "generate",
        model: "gpt-image-2",
        quality: "auto",
        aspectRatio: "1:1",
        size: "1024x1024",
        batchCount: 2,
        sourceFileIds: [],
        sourceImageUrls: [],
        maskImageUrls: []
      })
    }));
    expect(fetch).toHaveBeenNthCalledWith(
      2,
      "/api/v1/agent/workspaces/image/history?sessionId=S1001&limit=8",
      expect.objectContaining({
        method: "GET",
        headers: expect.objectContaining({
          Authorization: "Bearer user-token"
        })
      })
    );
  });

  it("uses saved image model as workspace image default", async () => {
    saveUserAuth({ token: "user-token" });
    fetch
      .mockResolvedValueOnce(jsonResponse({
        code: "0000",
        data: {
          enabled: true,
          baseUrl: "https://text.example.com",
          textBaseUrl: "https://text.example.com",
          imageBaseUrl: "https://image.example.com",
          model: "custom-text-model",
          textModel: "custom-text-model",
          imageModel: "custom-image-model",
          keyMasked: "sk-t****cret",
          textKeyMasked: "sk-t****cret",
          imageKeyMasked: "sk-i****cret"
        }
      }))
      .mockResolvedValueOnce(jsonResponse());

    await saveModelConfig({
      enabled: true,
      textBaseUrl: "https://text.example.com",
      textApiKey: "",
      textModel: "custom-text-model",
      imageBaseUrl: "https://image.example.com",
      imageApiKey: "",
      imageModel: "custom-image-model",
      textKeyMasked: "sk-t****cret",
      imageKeyMasked: "sk-i****cret"
    });
    await generateWorkspaceImage({
      sessionId: "S1002",
      prompt: "论文框架图"
    });

    expect(JSON.parse(fetch.mock.calls[1][1].body).model).toBe("custom-image-model");
  });

  it("wires agent session and run detail APIs with user token", async () => {
    saveUserAuth({ token: "user-token" });

    await queryAgentSessions(12);
    await queryAgentSessionDetail("AS 1001");
    await queryAgentRunDetail("RUN 1001");
    await queryUserAgentMemories();
    await disableUserAgentMemory("output style");
    await deleteUserAgentMemory("output style");

    expect(fetch).toHaveBeenNthCalledWith(
      1,
      "/api/v1/agent/sessions?limit=12",
      expect.objectContaining({
        method: "GET",
        headers: expect.objectContaining({
          Authorization: "Bearer user-token"
        })
      })
    );
    expect(fetch).toHaveBeenNthCalledWith(
      2,
      "/api/v1/agent/sessions/AS%201001",
      expect.objectContaining({
        method: "GET",
        headers: expect.objectContaining({
          Authorization: "Bearer user-token"
        })
      })
    );
    expect(fetch).toHaveBeenNthCalledWith(
      3,
      "/api/v1/agent/runs/RUN%201001",
      expect.objectContaining({
        method: "GET",
        headers: expect.objectContaining({
          Authorization: "Bearer user-token"
        })
      })
    );
    expect(fetch).toHaveBeenNthCalledWith(
      4,
      "/api/v1/agent/memories",
      expect.objectContaining({
        method: "GET",
        headers: expect.objectContaining({
          Authorization: "Bearer user-token"
        })
      })
    );
    expect(fetch).toHaveBeenNthCalledWith(
      5,
      "/api/v1/agent/memories/output%20style",
      expect.objectContaining({
        method: "DELETE",
        headers: expect.objectContaining({
          Authorization: "Bearer user-token"
        })
      })
    );
    expect(fetch).toHaveBeenNthCalledWith(
      6,
      "/api/v1/agent/memories/output%20style/remove",
      expect.objectContaining({
        method: "DELETE",
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
      question: "compare experiment metrics",
      rows: [{ metric_name: "accuracy", metric_value: 92.4 }],
      columns: ["metric_name", "metric_value"],
      modelCodeList: ["experiment_result"]
    });
    await queryWorkspaceDataCatalog();
    await queryWorkspaceDataHistory({ sessionId: "D1001", limit: 5 });

    expect(fetch).toHaveBeenNthCalledWith(1, "/api/v1/agent/workspaces/data/run", expect.objectContaining({
      method: "POST",
      headers: expect.objectContaining({
        Authorization: "Bearer user-token",
        "Content-Type": "application/json"
      }),
      body: JSON.stringify({
        sessionId: "D1001",
        question: "compare experiment metrics",
        rows: [{ metric_name: "accuracy", metric_value: 92.4 }],
        columns: ["metric_name", "metric_value"],
        modelCodeList: ["experiment_result"],
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
        includeTradeAudit: false,
        auditOrderId: "",
        auditTeamId: "",
        auditKeyword: "",
        metadata: {}
      })
    }));
    expect(fetch).toHaveBeenNthCalledWith(
      2,
      "/api/v1/agent/workspaces/data/catalog",
      expect.objectContaining({
        method: "GET",
        headers: expect.objectContaining({
          Authorization: "Bearer user-token"
        })
      })
    );
    expect(fetch).toHaveBeenNthCalledWith(
      3,
      "/api/v1/agent/workspaces/data/history?sessionId=D1001&limit=5",
      expect.objectContaining({
        method: "GET",
        headers: expect.objectContaining({
          Authorization: "Bearer user-token"
        })
      })
    );
  });

  it("creates an Alipay payment with user token", async () => {
    saveUserAuth({ token: "user-token" });

    await createPayment("O10001", {
      returnUrl: "http://localhost:5174/?paymentReturn=1&orderId=O10001"
    });

    expect(fetch).toHaveBeenCalledWith("/api/v1/trade/payment/create", expect.objectContaining({
      method: "POST",
      headers: expect.objectContaining({
        Authorization: "Bearer user-token",
        "Content-Type": "application/json"
      }),
      body: JSON.stringify({
        orderId: "O10001",
        payChannel: "ALIPAY",
        notifyUrl: "",
        returnUrl: "http://localhost:5174/?paymentReturn=1&orderId=O10001"
      })
    }));
  });
});
