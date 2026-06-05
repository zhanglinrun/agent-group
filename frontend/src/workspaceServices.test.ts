import { describe, expect, it } from "vitest";

import {
  buildWorkspaceDataRunPayload,
  buildWorkspaceDataCatalogDraft,
  buildWorkspaceImageGeneratePayload,
  buildKnowledgeBaseCatalog,
  buildWorkspaceStreamDraft,
  knowledgeBaseCatalogKey,
  normalizeWorkspaceHistoryItems,
  visibleAgentExecutionModes,
  visibleCapabilityMatrix,
  visibleToolCatalogGroups,
  visibleToolRuntimeReadiness,
  workspaceAcceptsFile,
  workspaceCapabilityStatus,
  workspaceDisplayProfile,
  workspaceServiceProfile,
  workspaceToolReadiness,
  workspaceSupportsHistory
} from "./workspaceServices";

describe("workspace service profiles", () => {
  it("maps product workspaces to stable task types and tools", () => {
    expect(workspaceServiceProfile("agent").primaryTools).toContain("code_interpreter");
    expect(workspaceServiceProfile("image").taskType).toBe("image");
    expect(workspaceServiceProfile("image").runEndpoint).toBe("/api/v1/academic/workspace/image/generate");
    expect(workspaceServiceProfile("data").primaryTools).toContain("nl2sql");
    expect(workspaceServiceProfile("data").runEndpoint).toBe("/api/v1/academic/workspace/data/run");
    expect(workspaceServiceProfile("mrag").primaryTools).toContain("multimodal_agent");
    expect(workspaceServiceProfile("mrag").runEndpoint).toBe("/api/v1/academic/workspace/mrag/run");
    expect(workspaceServiceProfile("trade").taskType).toBe("trade-audit");
    expect(workspaceServiceProfile("trade").primaryTools).toContain("trade_audit");
    expect(workspaceServiceProfile("trade").primaryTools).toContain("nl2sql");
    expect(workspaceServiceProfile("trade").runEndpoint).toBe("/api/v1/academic/stream");
    expect(workspaceServiceProfile("missing").id).toBe("agent");
  });

  it("keeps attachment rules explicit per workspace", () => {
    expect(workspaceAcceptsFile("trade")).toBe(false);
    expect(workspaceAcceptsFile("image")).toBe(true);
    expect(workspaceAcceptsFile("agent", "manual-skills")).toBe(true);
  });

  it("derives capability status from backend capability payload", () => {
    const status = workspaceCapabilityStatus("data", {
      reactorToolEnabled: true,
      manualSkillCount: 14,
      academicTools: [
        { name: "data_analysis" },
        { name: "table_rag" },
        { name: "nl2sql" }
      ]
    });

    expect(status.find((item) => item.key === "primary-tools")?.label).toBe("核心工具 3/4");
    expect(status.find((item) => item.key === "reactor-tool")?.active).toBe(true);
    expect(status.find((item) => item.key === "manual-skills")?.label).toBe("技能 14");
  });

  it("prefers backend workspace profile when capabilities include one", () => {
    const profile = workspaceDisplayProfile("image", {
      workspaceProfiles: [
        {
          id: "image",
          primaryTools: ["image_generation", "custom_render"],
          outputKinds: ["image", "preview"],
          runEndpoint: "/api/custom/image"
        }
      ]
    });
    const status = workspaceCapabilityStatus("image", {
      workspaceProfiles: [
        {
          id: "image",
          primaryTools: ["image_generation", "custom_render"],
          availableTools: ["image_generation"],
          missingTools: ["custom_render"],
          outputKinds: ["image", "preview"]
        }
      ]
    });

    expect(profile.primaryTools).toEqual(["image_generation", "custom_render"]);
    expect(profile.outputKinds).toEqual(["image", "preview"]);
    expect(profile.runEndpoint).toBe("/api/custom/image");
    expect(status.find((item) => item.key === "primary-tools")?.label).toBe("核心工具 1/2");
  });

  it("summarizes workspace tool readiness from backend workspace profile", () => {
    expect(workspaceToolReadiness("trade", {
      workspaceProfiles: [
        {
          id: "trade",
          primaryTools: ["trade_audit", "data_analysis", "report_tool"],
          availableTools: ["trade_audit", "data_analysis"],
          missingTools: ["report_tool"],
          outputKinds: ["order", "quota", "audit-report"]
        }
      ],
      toolRuntimeReadiness: [
        {
          name: "trade_audit",
          status: "ready",
          inputFields: ["question", "orderId"],
          outputKinds: ["order", "quota"],
          workspaces: ["trade"]
        },
        {
          name: "report_tool",
          status: "missing",
          inputFields: ["title", "content"],
          outputKinds: ["report"],
          workspaces: ["trade"]
        }
      ]
    })).toMatchObject({
      status: "partial",
      statusLabel: "部分就绪",
      readyTools: ["trade_audit", "data_analysis"],
      missingTools: ["report_tool"],
      requiredTools: ["trade_audit", "data_analysis", "report_tool"],
      inputFields: ["question", "orderId", "title", "content"],
      outputKinds: ["order", "quota", "audit-report", "report"],
      actions: [
        "补齐 report_tool 工具运行时",
        "检查后端能力接口的 workspaceProfiles 配置"
      ]
    });
  });

  it("falls back to academic tool list when detailed readiness is not available", () => {
    const readiness = workspaceToolReadiness("image", {
      academicTools: [
        { name: "image_generation" },
        { name: "file_tool" }
      ]
    });

    expect(readiness.status).toBe("partial");
    expect(readiness.readyTools).toEqual(["image_generation", "file_tool"]);
    expect(readiness.missingTools).toEqual(["multimodal_agent"]);
    expect(readiness.actions[0]).toBe("补齐 multimodal_agent 工具运行时");
  });

  it("builds stream draft with workspace defaults", () => {
    expect(buildWorkspaceStreamDraft({ workspaceId: "mrag" })).toEqual({
      taskType: "mrag",
      question: "请继续处理当前工作区任务",
      fileId: "",
      imageUrl: "",
      imageName: ""
    });
    expect(buildWorkspaceStreamDraft({ workspaceId: "trade" }).taskType).toBe("trade-audit");
    expect(buildWorkspaceStreamDraft({
      workspaceId: "image",
      agentId: "chat",
      question: "  生成图  ",
      fileId: "F1"
    }).taskType).toBe("chat");
  });

  it("builds data workspace payload from structured draft inputs", () => {
    expect(buildWorkspaceDataRunPayload({
      sessionId: "D1",
      question: "count paid orders",
      rowsJson: "[{\"pay_status\":\"PAY_SUCCESS\",\"count\":12}]",
      columnsText: "pay_status, count\namount",
      modelCodeText: "trade_order；quota_flow",
      schemaInfoJson: "[{\"table\":\"trade_order\"}]",
      businessKnowledge: " group orders settle before quota grant "
    })).toEqual({
      sessionId: "D1",
      question: "count paid orders",
      rows: [{ pay_status: "PAY_SUCCESS", count: 12 }],
      columns: ["pay_status", "count", "amount"],
      modelCodeList: ["trade_order", "quota_flow"],
      schemaInfo: [{ table: "trade_order" }],
      businessKnowledge: "group orders settle before quota grant"
    });
  });

  it("builds data workspace draft from backend catalog", () => {
    const draft = buildWorkspaceDataCatalogDraft({
      defaultModelCodeList: ["trade_order", "user_quota_flow"],
      models: [
        {
          modelCode: "trade_order",
          tableName: "trade_order",
          displayName: "交易订单",
          columns: [
            { name: "order_id", type: "varchar", description: "订单编号" },
            { name: "pay_amount", type: "decimal", description: "支付金额", metric: true }
          ]
        },
        {
          modelCode: "user_quota_flow",
          tableName: "user_quota_flow",
          displayName: "额度流水",
          columns: [
            { name: "order_id", type: "varchar", description: "订单编号" },
            { name: "quota_amount", type: "decimal", description: "额度变动", metric: true }
          ]
        }
      ]
    });

    expect(draft.modelCodeText).toBe("trade_order, user_quota_flow");
    expect(draft.columnsText).toBe("order_id, pay_amount, quota_amount");
    expect(JSON.parse(draft.schemaInfoJson)[0]).toMatchObject({
      modelCode: "trade_order",
      displayName: "交易订单"
    });
    expect(draft.businessKnowledge).toContain("拼团支付成功");
  });

  it("rejects invalid data workspace JSON arrays", () => {
    expect(() => buildWorkspaceDataRunPayload({
      sessionId: "D1",
      question: "count paid orders",
      rowsJson: "{\"bad\":true}"
    })).toThrow("表格行 必须是 JSON 数组");
  });

  it("builds image workspace payload with bounded batch size", () => {
    expect(buildWorkspaceImageGeneratePayload({
      sessionId: "IMG1",
      prompt: " make a cover ",
      mode: "generate",
      size: "1536x1024",
      batchCount: 9
    })).toEqual({
      sessionId: "IMG1",
      prompt: "make a cover",
      mode: "generate",
      size: "1536x1024",
      batchCount: 4,
      sourceFileIds: [],
      sourceImageUrls: [],
      maskImageUrls: []
    });
  });

  it("requires a reference image for image edit mode", () => {
    expect(() => buildWorkspaceImageGeneratePayload({
      sessionId: "IMG2",
      prompt: "edit style",
      mode: "edit"
    })).toThrow("图生图需要先上传参考图");

    expect(buildWorkspaceImageGeneratePayload({
      sessionId: "IMG2",
      prompt: "edit style",
      mode: "edit",
      sourceFileIds: ["F1"],
      maskImageUrls: "https://example.com/mask-a.png\nhttps://example.com/mask-b.png"
    })).toMatchObject({
      mode: "edit",
      maskImageUrls: ["https://example.com/mask-a.png", "https://example.com/mask-b.png"]
    });
  });

  it("normalizes visible tool catalog groups", () => {
    expect(visibleToolCatalogGroups({
      toolCatalog: {
        categoryGroups: [
          { key: "analysis", count: 3, tools: ["data_analysis", "table_rag"] },
          { key: "image", count: 1, tools: ["image_generation"] },
          { key: "", count: 9, tools: [] }
        ]
      }
    }, 1)).toEqual([
      { key: "analysis", count: 3, tools: ["data_analysis", "table_rag"] }
    ]);
    expect(visibleToolCatalogGroups({})).toEqual([]);
  });

  it("normalizes visible tool runtime readiness", () => {
    expect(visibleToolRuntimeReadiness({
      toolRuntimeReadiness: [
        {
          name: "data_analysis",
          status: "ready",
          category: "data",
          source: "runtime",
          requiredArguments: ["task"],
          inputFields: ["task", "rows", "columns"],
          outputKinds: ["table", "summary"],
          workspaces: ["data", "trade"],
          message: "registered"
        },
        {
          name: "code_interpreter",
          status: "missing",
          category: "code",
          source: "port",
          requiredArguments: ["task"],
          inputFields: ["task", "language", "code"],
          outputKinds: ["code", "file"],
          workspaces: ["agent"],
          message: "external port is not configured",
          hint: "启动工具服务"
        },
        { name: "", status: "ready" }
      ]
    }, 2)).toEqual([
      {
        name: "data_analysis",
        status: "ready",
        category: "data",
        source: "runtime",
        requiredArguments: ["task"],
        inputFields: ["task", "rows", "columns"],
        outputKinds: ["table", "summary"],
        workspaces: ["data", "trade"],
        message: "registered",
        hint: ""
      },
      {
        name: "code_interpreter",
        status: "missing",
        category: "code",
        source: "port",
        requiredArguments: ["task"],
        inputFields: ["task", "language", "code"],
        outputKinds: ["code", "file"],
        workspaces: ["agent"],
        message: "external port is not configured",
        hint: "启动工具服务"
      }
    ]);
    expect(visibleToolRuntimeReadiness({})).toEqual([]);
  });

  it("normalizes capability matrix and execution modes for frontend display", () => {
    expect(visibleCapabilityMatrix({
      capabilityMatrix: [
        {
          key: "multi-agent",
          label: "多智能体协同",
          status: "ready",
          summary: "ReAct、Plan Execute、Flow 已接入",
          evidence: ["chat uses ReAct", "deep uses Plan Execute", "flow_delta events"],
          gaps: [],
          dynamicReplan: {
            enabled: true,
            executionModes: ["deep"],
            streamEvents: ["plan_delta:replan", "flow_delta:REPLANNED"],
            historyEvidence: ["planner history versions"]
          }
        },
        {
          key: "mcp",
          label: "MCP 管理",
          status: "degraded",
          summary: "支持服务注册",
          evidence: ["streamable_http"],
          gaps: ["未缓存外部工具"]
        }
      ]
    }, 1)).toEqual([
      {
        key: "multi-agent",
        label: "多智能体协同",
        status: "ready",
        summary: "ReAct、Plan Execute、Flow 已接入",
        evidence: ["chat uses ReAct", "deep uses Plan Execute", "flow_delta events"],
        gaps: [],
        dynamicReplan: {
          enabled: true,
          executionModes: ["deep"],
          streamEvents: ["plan_delta:replan", "flow_delta:REPLANNED"],
          historyEvidence: ["planner history versions"]
        }
      }
    ]);

    expect(visibleAgentExecutionModes({
      agentExecutionModes: [
        { agentId: "chat", name: "对话助手", family: "react", executionMode: "ReAct", summary: "通用问答" },
        {
          agentId: "deep",
          name: "深度研究",
          family: "plan-execute",
          executionMode: "Plan Execute",
          summary: "动态重规划",
          replanEnabled: true,
          replanEvidence: ["plan_update/replan stream event", "flow_delta:REPLANNED"]
        }
      ]
    })).toEqual([
      { agentId: "chat", name: "对话助手", family: "react", executionMode: "ReAct", summary: "通用问答" },
      {
        agentId: "deep",
        name: "深度研究",
        family: "plan-execute",
        executionMode: "Plan Execute",
        summary: "动态重规划",
        replanEnabled: true,
        replanEvidence: ["plan_update/replan stream event", "flow_delta:REPLANNED"]
      }
    ]);
  });

  it("normalizes workspace history items across workspace APIs", () => {
    expect(workspaceSupportsHistory("image")).toBe(true);
    expect(workspaceSupportsHistory("trade")).toBe(true);

    expect(normalizeWorkspaceHistoryItems("image", [
      {
        artifactId: "ART1",
        sessionId: "S1",
        runId: "R1",
        title: "cover",
        fileName: "cover.png",
        previewUrl: "/artifact/preview/ART1"
      }
    ])).toEqual([
      {
        id: "ART1",
        workspaceId: "image",
        sessionId: "S1",
        runId: "R1",
        title: "cover",
        summary: "cover.png",
        status: "SUCCESS",
        createdAt: "",
        durationMillis: 0,
        artifactUrl: "/artifact/preview/ART1",
        artifactName: "cover.png"
      }
    ]);

    expect(normalizeWorkspaceHistoryItems("data", [
      {
        runId: "RUN1",
        sessionId: "D1",
        question: "count paid orders",
        summary: "12 paid orders",
        status: "SUCCESS",
        finishedAt: "2026-06-05T10:20:30",
        durationMillis: 1200
      }
    ])[0]).toMatchObject({
      id: "RUN1",
      workspaceId: "data",
      sessionId: "D1",
      title: "count paid orders",
      summary: "12 paid orders",
      createdAt: "2026-06-05T10:20:30",
      durationMillis: 1200
    });

    expect(normalizeWorkspaceHistoryItems("image", [
      {
        requestId: "IMGREQ1",
        sessionId: "S2",
        runId: "R2",
        prompt: "生成三张活动海报",
        summary: "生成成功",
        status: "SUCCESS",
        mode: "edit",
        size: "1536x1024",
        batchCount: 3,
        sourceImageCount: 2,
        finishedAt: "2026-06-05T11:20:30",
        images: [
          {
            artifactId: "ART2",
            fileName: "poster-1.png",
            previewUrl: "/artifact/preview/ART2"
          }
        ]
      }
    ])[0]).toMatchObject({
      id: "IMGREQ1",
      workspaceId: "image",
      sessionId: "S2",
      runId: "R2",
      title: "生成三张活动海报",
      summary: "图生图 · 3 张 · 2 张参考图 · 1536x1024",
      status: "SUCCESS",
      createdAt: "2026-06-05T11:20:30",
      artifactUrl: "/artifact/preview/ART2",
      artifactName: "poster-1.png"
    });

    const tradeAuditHistory = normalizeWorkspaceHistoryItems("trade", [
      {
        sessionId: "AS1001",
        taskType: "trade-audit",
        title: "\u6838\u67e5\u62fc\u56e2\u8ba2\u5355",
        lastMessage: "\u652f\u4ed8\u6210\u529f\u4f46\u7b49\u5f85\u6210\u56e2",
        updateTime: "2026-06-05T12:30:30"
      }
    ]);
    expect(tradeAuditHistory[0]).toMatchObject({
      id: "AS1001",
      workspaceId: "trade",
      sessionId: "AS1001",
      title: "\u6838\u67e5\u62fc\u56e2\u8ba2\u5355",
      summary: "\u652f\u4ed8\u6210\u529f\u4f46\u7b49\u5f85\u6210\u56e2",
      status: "AGENT_AUDIT",
      createdAt: "2026-06-05T12:30:30"
    });

    expect(normalizeWorkspaceHistoryItems("trade", [
      {
        id: 1001,
        orderId: "O1001",
        productName: "Agent 额度包",
        marketType: 1,
        status: "PAY_SUCCESS",
        displayStatus: "支付成功，等待成团",
        payAmount: 19.9,
        orderTime: "2026-06-05T12:20:30"
      }
    ])[0]).toMatchObject({
      id: "O1001",
      workspaceId: "trade",
      title: "Agent 额度包",
      summary: "拼团订单 · 支付成功，等待成团 · 支付 19.9",
      status: "支付成功，等待成团",
      createdAt: "2026-06-05T12:20:30"
    });
  });

  it("groups knowledge documents into catalog items", () => {
    const catalog = buildKnowledgeBaseCatalog([
      {
        documentId: "DOC1",
        documentType: "MRAG Knowledge",
        knowledgeVersion: "v1",
        documentStatus: "ENABLED",
        fragmentCount: 3,
        updateTime: "2026-06-05T10:00:00"
      },
      {
        documentId: "DOC2",
        documentType: "MRAG Knowledge",
        knowledgeVersion: "v1",
        documentStatus: "EMBEDDING_FAILED",
        fragmentCount: 2,
        updateTime: "2026-06-05T11:00:00"
      }
    ]);

    expect(knowledgeBaseCatalogKey({ documentType: "MRAG Knowledge", knowledgeVersion: "v1" })).toBe("MRAG Knowledge::v1");
    expect(catalog).toEqual([
      {
        id: "MRAG Knowledge::v1",
        name: "MRAG Knowledge",
        version: "v1",
        documentType: "MRAG Knowledge",
        documentCount: 2,
        fragmentCount: 5,
        enabledCount: 1,
        failedCount: 1,
        latestUpdate: "2026-06-05T11:00:00"
      }
    ]);
  });
});
