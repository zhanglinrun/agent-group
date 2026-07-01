import { describe, expect, it } from "vitest";

import {
  buildWorkspaceDataRunPayload,
  buildWorkspaceDataCatalogDraft,
  buildWorkspaceImageGeneratePayload,
  buildWorkspaceStreamDraft,
  normalizeWorkspaceHistoryItems,
  visibleAgentExecutionModes,
  visibleCapabilityMatrix,
  visibleToolCatalogGroups,
  visibleToolRuntimeFamilyReadiness,
  visibleToolRuntimeReadiness,
  visibleWorkspaceRuntimeCoverage,
  workspaceAcceptsFile,
  workspaceCapabilityStatus,
  workspaceDisplayProfile,
  workspaceRuntimeCoverage,
  workspaceServiceProfile,
  workspaceToolReadiness,
  workspaceSupportsHistory
} from "./workspaceServices";

describe("workspace service profiles", () => {
  it("maps product workspaces to stable task types and tools", () => {
    expect(workspaceServiceProfile("agent").primaryTools).toContain("code_interpreter");
    expect(workspaceServiceProfile("image").taskType).toBe("image");
    expect(workspaceServiceProfile("image").runEndpoint).toBe("/api/v1/agent/workspaces/image/generate");
    expect(workspaceServiceProfile("data").primaryTools).toContain("nl2sql");
    expect(workspaceServiceProfile("data").runEndpoint).toBe("/api/v1/agent/workspaces/data/run");
    expect(workspaceServiceProfile("trade").taskType).toBe("trade-diagnosis");
    expect(workspaceServiceProfile("trade").primaryTools).toEqual(["trade_order_list", "trade_diagnosis"]);
    expect(workspaceServiceProfile("trade").runEndpoint).toBe("/api/v1/agent/stream");
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
      agentTools: [
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
    expect(workspaceToolReadiness("data", {
      workspaceProfiles: [
        {
          id: "data",
          primaryTools: ["data_analysis", "report_tool"],
          availableTools: ["data_analysis"],
          missingTools: ["report_tool"],
          outputKinds: ["order", "quota", "report"]
        }
      ],
      toolRuntimeReadiness: [
        {
          name: "data_analysis",
          status: "ready",
          inputFields: ["question", "orderId"],
          outputKinds: ["order", "quota"],
          workspaces: ["data"]
        },
        {
          name: "report_tool",
          status: "missing",
          inputFields: ["title", "content"],
          outputKinds: ["report"],
          workspaces: ["data"]
        }
      ]
    })).toMatchObject({
      status: "partial",
      statusLabel: "部分就绪",
      readyTools: ["data_analysis"],
      missingTools: ["report_tool"],
      requiredTools: ["data_analysis", "report_tool"],
      inputFields: ["question", "orderId", "title", "content"],
      outputKinds: ["order", "quota", "report"],
      actions: [
        "补齐 report_tool 工具运行时",
        "检查后端能力接口的 workspaceProfiles 配置"
      ]
    });
  });

  it("falls back to agent tool list when detailed readiness is not available", () => {
    const readiness = workspaceToolReadiness("image", {
      agentTools: [
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
    expect(buildWorkspaceStreamDraft({ workspaceId: "trade" }).taskType).toBe("trade-diagnosis");
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
      question: "compare experiment metrics",
      rowsJson: "[{\"metric_name\":\"accuracy\",\"metric_value\":92.4}]",
      columnsText: "metric_name, metric_value\ndataset",
      modelCodeText: "paper_metadata；experiment_result",
      schemaInfoJson: "[{\"table\":\"experiment_result\"}]",
      businessKnowledge: " compare metrics from paper experiments "
    })).toEqual({
      sessionId: "D1",
      question: "compare experiment metrics",
      rows: [{ metric_name: "accuracy", metric_value: 92.4 }],
      columns: ["metric_name", "metric_value", "dataset"],
      modelCodeList: ["paper_metadata", "experiment_result"],
      schemaInfo: [{ table: "experiment_result" }],
      businessKnowledge: "compare metrics from paper experiments"
    });
  });

  it("builds data workspace draft from backend catalog", () => {
    const draft = buildWorkspaceDataCatalogDraft({
      defaultModelCodeList: ["paper_metadata", "experiment_result"],
      models: [
        {
          modelCode: "paper_metadata",
          tableName: "paper_metadata",
          displayName: "论文元数据",
          columns: [
            { name: "paper_id", type: "varchar", description: "论文编号" },
            { name: "publish_year", type: "int", description: "发表年份", metric: true }
          ]
        },
        {
          modelCode: "experiment_result",
          tableName: "experiment_result",
          displayName: "实验结果",
          columns: [
            { name: "experiment_id", type: "varchar", description: "实验编号" },
            { name: "metric_value", type: "decimal", description: "指标数值", metric: true }
          ]
        }
      ]
    });

    expect(draft.modelCodeText).toBe("paper_metadata, experiment_result");
    expect(draft.columnsText).toBe("paper_id, publish_year, experiment_id, metric_value");
    expect(JSON.parse(draft.schemaInfoJson)[0]).toMatchObject({
      modelCode: "paper_metadata",
      displayName: "论文元数据"
    });
    expect(draft.businessKnowledge).toContain("论文元数据");
  });

  it("rejects invalid data workspace JSON arrays", () => {
    expect(() => buildWorkspaceDataRunPayload({
      sessionId: "D1",
      question: "compare experiment metrics",
      rowsJson: "{\"bad\":true}"
    })).toThrow("表格行 必须是 JSON 数组");
  });

  it("builds image workspace payload with bounded batch size", () => {
    expect(buildWorkspaceImageGeneratePayload({
      sessionId: "IMG1",
      prompt: " make a cover ",
      mode: "generate",
      size: "1536x1024",
      batchCount: 12
    })).toEqual({
      sessionId: "IMG1",
      prompt: "make a cover",
      mode: "generate",
      model: "gpt-image-2",
      quality: "auto",
      aspectRatio: "1:1",
      size: "1536x1024",
      batchCount: 10,
      sourceFileIds: [],
      sourceImageUrls: [],
      maskImageUrls: []
    });
  });

  it("uses reference images to choose image edit mode automatically", () => {
    expect(buildWorkspaceImageGeneratePayload({
      sessionId: "IMG2",
      prompt: "edit style",
      mode: "edit"
    })).toMatchObject({
      mode: "generate"
    });

    expect(buildWorkspaceImageGeneratePayload({
      sessionId: "IMG2",
      prompt: "edit style",
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

  it("normalizes workspace runtime coverage from tool catalog", () => {
    const capabilities = {
      toolCatalog: {
        workspaceCoverage: [
          {
            workspace: "data",
            status: "degraded",
            runEndpoint: "/api/v1/agent/workspaces/data/run",
            historyEndpoint: "/api/v1/agent/workspaces/data/history",
            availableTools: ["data_analysis", "table_rag"],
            missingTools: ["nl2sql"]
          }
        ]
      }
    };

    expect(visibleWorkspaceRuntimeCoverage(capabilities)).toEqual([
      {
        workspaceId: "data",
        status: "degraded",
        statusLabel: "部分覆盖",
        runReady: true,
        historyReady: true,
        availableTools: ["data_analysis", "table_rag"],
        missingTools: ["nl2sql"]
      }
    ]);
    expect(workspaceRuntimeCoverage("data", capabilities)).toMatchObject({
      status: "degraded",
      statusLabel: "部分覆盖",
      runReady: true,
      historyReady: true
    });
    expect(workspaceRuntimeCoverage("image", {})).toMatchObject({
      workspaceId: "image",
      status: "pending",
      runReady: true,
      historyReady: true,
      missingTools: ["image_generation", "multimodal_agent", "file_tool"]
    });
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

  it("groups tool runtime readiness by agent tool family", () => {
    const families = visibleToolRuntimeFamilyReadiness({
      toolRuntimeReadiness: [
        {
          name: "web_fetch",
          status: "ready",
          outputKinds: ["web-page"],
          workspaces: ["agent"]
        },
        {
          name: "deep_search",
          status: "missing",
          outputKinds: ["research"],
          workspaces: ["agent"]
        },
        {
          name: "data_analysis",
          status: "ready",
          outputKinds: ["table"],
          workspaces: ["data"]
        },
        {
          name: "table_rag",
          status: "ready",
          outputKinds: ["evidence"],
          workspaces: ["data"]
        },
        {
          name: "nl2sql",
          status: "ready",
          outputKinds: ["sql"],
          workspaces: ["data"]
        },
        {
          name: "report_tool",
          status: "missing",
          outputKinds: ["report"],
          workspaces: ["agent", "data"]
        }
      ]
    });

    expect(families.find((item) => item.key === "web")).toMatchObject({
      label: "网页抓取",
      status: "partial",
      readyCount: 1,
      totalCount: 2,
      missingTools: ["deep_search"],
      action: "补齐 deep_search 工具运行时"
    });
    expect(families.find((item) => item.key === "data")).toMatchObject({
      status: "ready",
      readyCount: 3,
      totalCount: 3,
      outputKinds: ["table", "evidence", "sql"],
      workspaces: ["data"]
    });
    expect(families.find((item) => item.key === "report")).toMatchObject({
      status: "missing",
      missingTools: ["report_tool"]
    });
    expect(visibleToolRuntimeFamilyReadiness(null)).toEqual([]);
  });

  it("prefers backend tool runtime family readiness", () => {
    const families = visibleToolRuntimeFamilyReadiness({
      toolRuntimeFamilies: [
        {
          key: "web",
          label: "网页抓取",
          status: "ready",
          statusLabel: "已就绪",
          readyCount: 2,
          totalCount: 2,
          tools: ["web_fetch", "deep_search"],
          missingTools: [],
          outputKinds: ["web", "reference"],
          workspaces: ["agent"],
          action: "核心工具已覆盖"
        }
      ],
      toolRuntimeReadiness: [
        { name: "web_fetch", status: "missing" }
      ]
    });

    expect(families).toEqual([
      {
        key: "web",
        label: "网页抓取",
        status: "ready",
        statusLabel: "已就绪",
        readyCount: 2,
        totalCount: 2,
        tools: ["web_fetch", "deep_search"],
        missingTools: [],
        outputKinds: ["web", "reference"],
        workspaces: ["agent"],
        action: "核心工具已覆盖"
      }
    ]);
  });

  it("normalizes capability matrix and execution modes for frontend display", () => {
    expect(visibleCapabilityMatrix({
      capabilityMatrix: [
        {
          key: "execution-strategy",
          label: "执行策略",
          status: "ready",
          summary: "ReAct、Plan-Execute、PPT Workflow 已接入",
          evidence: ["chat uses ReAct", "deep uses Plan-Execute", "flow_delta events"],
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
        key: "execution-strategy",
        label: "执行策略",
        status: "ready",
        summary: "ReAct、Plan-Execute、PPT Workflow 已接入",
        evidence: ["chat uses ReAct", "deep uses Plan-Execute", "flow_delta events"],
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
          executionMode: "Plan-Execute",
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
        executionMode: "Plan-Execute",
        summary: "动态重规划",
        replanEnabled: true,
        replanEvidence: ["plan_update/replan stream event", "flow_delta:REPLANNED"]
      }
    ]);
  });

  it("keeps trade quota settlement rules from capability matrix", () => {
    expect(visibleCapabilityMatrix({
      capabilityMatrix: [
        {
          key: "trade-quota",
          label: "交易与额度闭环",
          status: "ready",
          summary: "额度发放由后端交易系统控制",
          evidence: ["拼团 GROUP_SETTLED/DEAL_DONE 后才可发放额度"],
          gaps: [],
          guardrails: ["前端和 Agent 不能直接决定额度到账", "拼团支付成功不等于额度到账"],
          settlementRules: [
            {
              key: "group-pay-success",
              scenario: "拼团名额已支付",
              requiredState: "PAY_SUCCESS",
              quotaGrantAllowed: false,
              operatorHint: "未成团前不能发放额度"
            },
            {
              key: "group-settled",
              scenario: "拼团已成团",
              requiredState: "GROUP_SETTLED/DEAL_DONE",
              quotaGrantAllowed: true,
              operatorHint: "成团后核对额度流水"
            }
          ]
        }
      ]
    })).toEqual([
      {
        key: "trade-quota",
        label: "交易与额度闭环",
        status: "ready",
        summary: "额度发放由后端交易系统控制",
        evidence: ["拼团 GROUP_SETTLED/DEAL_DONE 后才可发放额度"],
        gaps: [],
        guardrails: ["前端和 Agent 不能直接决定额度到账", "拼团支付成功不等于额度到账"],
        settlementRules: [
          {
            key: "group-pay-success",
            scenario: "拼团名额已支付",
            requiredState: "PAY_SUCCESS",
            quotaGrantAllowed: false,
            operatorHint: "未成团前不能发放额度"
          },
          {
            key: "group-settled",
            scenario: "拼团已成团",
            requiredState: "GROUP_SETTLED/DEAL_DONE",
            quotaGrantAllowed: true,
            operatorHint: "成团后核对额度流水"
          }
        ]
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
});
