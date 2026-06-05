import { describe, expect, it } from "vitest";

import {
  eventArtifacts,
  mergeArtifacts,
  mergeResultPanels,
  replayEventsToArtifacts,
  replayEventsToResultPanels,
  runDetailToResultPanels,
  toUiArtifact,
  toolResultArtifacts,
  toolResultPanels
} from "./taskArtifacts";

describe("task artifact projection", () => {
  it("normalizes artifact delta payloads", () => {
    expect(toUiArtifact({
      artifactId: "artifact-1",
      fileName: "report.md",
      downloadUrl: "/api/download/report.md",
      artifactType: "REPORT"
    })).toMatchObject({
      id: "artifact-1",
      title: "report.md",
      type: "REPORT",
      fileName: "report.md",
      downloadUrl: "/api/download/report.md"
    });
  });

  it("extracts file refs from tool result payloads", () => {
    const artifacts = toolResultArtifacts({
      event: "tool_result",
      data: {
        invocationId: "tool-1",
        toolCallId: "call-1",
        toolName: "report_tool",
        fileRefs: [{ fileId: "file-1", fileName: "chart.png", downloadUrl: "/files/chart.png" }],
        structuredOutput: {
          fileRefs: [{ fileId: "file-2", fileName: "analysis.csv", downloadUrl: "/files/analysis.csv" }]
        }
      }
    });

    expect(artifacts.map((item) => item.fileName)).toEqual(["chart.png", "analysis.csv"]);
    expect(artifacts[0]).toMatchObject({
      toolName: "report_tool",
      toolInvocationId: "tool-1",
      toolCallId: "call-1"
    });
  });

  it("keeps explicit artifact source when file refs already include it", () => {
    const [artifact] = toolResultArtifacts({
      event: "tool_result",
      data: {
        invocationId: "parent-tool",
        toolName: "report_tool",
        structuredOutput: {
          artifactRefs: [
            {
              artifactId: "artifact-1",
              fileName: "audit.md",
              downloadUrl: "/files/audit.md",
              toolName: "trade_audit",
              toolInvocationId: "audit-tool"
            }
          ]
        }
      }
    });

    expect(artifact).toMatchObject({
      toolName: "trade_audit",
      toolInvocationId: "audit-tool",
      fileName: "audit.md"
    });
  });

  it("extracts artifact refs from non-tool run events", () => {
    const artifacts = eventArtifacts({
      event: "run_done",
      data: {
        invocationId: "report-1",
        toolName: "report_tool",
        artifactRefs: [
          { artifactId: "artifact-1", fileName: "final-report.md", downloadUrl: "/files/final-report.md" }
        ],
        resultMap: JSON.stringify({
          artifactRefs: [
            { artifactId: "artifact-1", fileName: "final-report.md", downloadUrl: "/files/final-report.md" }
          ]
        })
      }
    });

    expect(artifacts).toHaveLength(1);
    expect(artifacts[0]).toMatchObject({
      fileName: "final-report.md",
      toolName: "report_tool",
      toolInvocationId: "report-1"
    });
  });

  it("extracts fileInfo and fileList payloads from tool events", () => {
    const artifacts = toolResultArtifacts({
      event: "tool_result",
      data: {
        invocationId: "code-1",
        toolName: "code_interpreter",
        resultMap: {
          fileInfo: [
            {
              displayName: "code-output.md",
              domainUrl: "/tool/files/code-output.md",
              size: 512,
              mimeType: "text/markdown",
              resourceKey: "code-output-resource"
            }
          ],
          resultMap: {
            fileList: [
              {
                fileName: "chart.png",
                previewUrl: "/tool/files/chart.png",
                downloadUrl: "/tool/files/chart.png",
                contentType: "image/png"
              }
            ]
          }
        }
      }
    });

    expect(artifacts.map((item) => item.fileName)).toEqual(["code-output.md", "chart.png"]);
    expect(artifacts[0]).toMatchObject({
      id: "code-output-resource",
      fileSize: 512,
      contentType: "text/markdown",
      toolName: "code_interpreter",
      toolInvocationId: "code-1"
    });
  });

  it("extracts primary file fields from run event result maps", () => {
    const [artifact] = eventArtifacts({
      event: "run_done",
      data: {
        toolName: "report_tool",
        resultMap: {
          primaryFileName: "summary.md",
          ossUrl: "/files/summary.md",
          fileSize: "1024",
          mimeType: "text/markdown"
        }
      }
    });

    expect(artifact).toMatchObject({
      fileName: "summary.md",
      downloadUrl: "/files/summary.md",
      previewUrl: "/files/summary.md",
      fileSize: 1024,
      contentType: "text/markdown",
      toolName: "report_tool"
    });
  });

  it("deduplicates artifacts by stable id", () => {
    const first = toUiArtifact({ fileId: "file-1", fileName: "report.md", downloadUrl: "/files/report.md" });
    const second = toUiArtifact({ fileId: "file-1", fileName: "report.md", downloadUrl: "/files/report.md" });

    expect(mergeArtifacts([first], [second])).toHaveLength(1);
  });

  it("replays artifact deltas and tool result refs", () => {
    const artifacts = replayEventsToArtifacts([
      {
        events: [
          { event: "artifact_delta", data: { artifactId: "artifact-1", fileName: "report.md" } },
          {
            event: "tool_result",
            data: {
              structuredOutput: {
                fileRefs: [{ fileId: "file-2", fileName: "chart.png", downloadUrl: "/files/chart.png" }]
              }
            }
          }
        ]
      }
    ]);

    expect(artifacts.map((item) => item.fileName)).toEqual(["report.md", "chart.png"]);
  });

  it("replays artifact refs carried by run events", () => {
    const artifacts = replayEventsToArtifacts([
      {
        events: [
          {
            event: "run_done",
            data: {
              toolName: "report_tool",
              artifactRefs: [
                { artifactId: "artifact-1", fileName: "run-report.md", downloadUrl: "/files/run-report.md" }
              ]
            }
          }
        ]
      }
    ]);

    expect(artifacts.map((item) => item.fileName)).toEqual(["run-report.md"]);
  });

  it("projects data analysis structured output into result panels", () => {
    const panels = toolResultPanels({
      event: "tool_result",
      data: {
        invocationId: "tool-1",
        toolName: "data_analysis",
        structuredOutput: {
          title: "交易漏斗",
          summary: "rows=2, columns=2",
          metadata: {
            columns: ["status", "count"],
            sampleRows: [
              { status: "PAY_SUCCESS", count: 10 },
              { status: "GROUP_SETTLED", count: 8 }
            ],
            numericStats: {
              count: { min: "8", max: "10", avg: "9" }
            }
          }
        }
      }
    });

    expect(panels).toHaveLength(1);
    expect(panels[0]).toMatchObject({
      id: "tool-1",
      kind: "data",
      title: "交易漏斗",
      columns: ["status", "count"]
    });
    expect(panels[0].rows).toHaveLength(2);
  });

  it("projects nl2sql candidates and schema matches", () => {
    const [sqlPanel] = toolResultPanels({
      event: "tool_result",
      data: {
        invocationId: "sql-1",
        toolName: "nl2sql",
        structuredOutput: {
          metadata: {
            candidates: [{ query: "查询成团订单", sql: "select * from trade_order" }]
          }
        }
      }
    });
    const [schemaPanel] = toolResultPanels({
      event: "tool_result",
      data: {
        invocationId: "schema-1",
        toolName: "table_rag",
        structuredOutput: {
          metadata: {
            matches: [{ modelCode: "trade_order", score: 0.91 }]
          }
        }
      }
    });

    expect(sqlPanel.kind).toBe("sql");
    expect(sqlPanel.candidates[0].sql).toContain("trade_order");
    expect(schemaPanel.kind).toBe("schema");
    expect(schemaPanel.matches[0].modelCode).toBe("trade_order");
  });

  it("projects deep search documents into source panels", () => {
    const [panel] = toolResultPanels({
      event: "tool_result",
      data: {
        invocationId: "search-1",
        toolName: "deep_search",
        structuredOutput: {
          title: "agent runtime",
          content: "final answer",
          metadata: {
            documents: [
              { title: "Spring AI docs", url: "https://docs.spring.io/spring-ai", content: "tool calling docs" }
            ]
          }
        }
      }
    });

    expect(panel.kind).toBe("search");
    expect(panel.sources[0]).toMatchObject({
      title: "Spring AI docs",
      url: "https://docs.spring.io/spring-ai",
      content: "tool calling docs"
    });
  });

  it("projects web fetch content and URL into web panels", () => {
    const [panel] = toolResultPanels({
      event: "tool_result",
      data: {
        invocationId: "web-1",
        toolName: "web_fetch",
        structuredOutput: {
          title: "Example",
          summary: "page summary",
          content: "page readable text",
          metadata: {
            finalUrl: "https://example.com/final",
            statusCode: 200
          }
        }
      }
    });

    expect(panel.kind).toBe("web");
    expect(panel.url).toBe("https://example.com/final");
    expect(panel.content).toBe("page readable text");
  });

  it("projects file tool refs into file panels", () => {
    const [panel] = toolResultPanels({
      event: "tool_result",
      data: {
        invocationId: "file-1",
        toolName: "file_tool",
        structuredOutput: {
          content: "created file",
          fileRefs: [
            { fileId: "report-1", fileName: "report.md", downloadUrl: "/files/report.md" }
          ]
        }
      }
    });

    expect(panel.kind).toBe("file");
    expect(panel.fileRefs[0].fileName).toBe("report.md");
  });

  it("unwraps callback result payloads before projection", () => {
    const [panel] = toolResultPanels({
      event: "tool_result",
      data: {
        invocationId: "wrapped-1",
        resultJson: JSON.stringify({
          success: true,
          toolName: "report_tool",
          result: {
            summary: "report generated",
            fileRefs: [
              { artifactId: "A1001", fileName: "trade-audit.md", downloadUrl: "/files/trade-audit.md" }
            ]
          }
        })
      }
    });

    expect(panel.kind).toBe("file");
    expect(panel.fileRefs[0].fileName).toBe("trade-audit.md");
  });

  it("projects trade audit findings into audit panels", () => {
    const [panel] = toolResultPanels({
      event: "tool_result",
      data: {
        invocationId: "audit-1",
        toolName: "trade_audit",
        structuredOutput: {
          title: "O1001",
          summary: "trade facts checked",
          content: "# Trade Audit Report",
          metadata: {
            findings: [
              {
                severity: "INFO",
                code: "PAID_WAITING_GROUP_SETTLEMENT",
                message: "Payment succeeded, but group settlement has not completed."
              }
            ]
          }
        }
      }
    });

    expect(panel.kind).toBe("audit");
    expect(panel.title).toBe("O1001");
    expect(panel.content).toContain("Trade Audit Report");
    expect(panel.findings[0].code).toBe("PAID_WAITING_GROUP_SETTLEMENT");
  });

  it("projects quota usage snapshots into quota panels", () => {
    const [panel] = toolResultPanels({
      event: "tool_result",
      data: {
        invocationId: "quota-1",
        toolName: "quota_usage",
        structuredOutput: {
          title: "额度对账快照",
          summary: "recorded quota usage",
          metadata: {
            taskType: "trade-audit",
            model: "test-model",
            estimatedConsumedQuota: 2,
            remainingQuota: 98,
            usedQuota: 12,
            frozenQuota: 0
          }
        }
      }
    });

    expect(panel.kind).toBe("quota");
    expect(panel.metadata).toMatchObject({
      estimatedConsumedQuota: 2,
      remainingQuota: 98,
      usedQuota: 12,
      frozenQuota: 0
    });
  });

  it("deduplicates and replays result panels", () => {
    const first = toolResultPanels({
      event: "tool_result",
      data: {
        invocationId: "panel-1",
        toolName: "data_analysis",
        structuredOutput: { summary: "rows=1" }
      }
    });

    expect(mergeResultPanels(first, first)).toHaveLength(1);
    expect(replayEventsToResultPanels([{ events: [{ event: "tool_result", data: {
      invocationId: "panel-2",
      toolName: "nl2sql",
      structuredOutput: { metadata: { candidates: [{ sql: "select 1" }] } }
    } }] }])).toHaveLength(1);
  });

  it("projects run detail tool invocations into result panels", () => {
    const panels = runDetailToResultPanels({
      toolInvocations: [
        {
          invocationId: "audit-1",
          toolName: "trade_audit",
          resultSummary: "checked",
          structuredOutput: {
            title: "Order O1001",
            metadata: {
              findings: [{ severity: "WARN", code: "WAITING_GROUP", message: "waiting group settlement" }]
            }
          }
        },
        {
          invocationId: "report-1",
          toolName: "report_tool",
          resultJson: JSON.stringify({
            result: {
              summary: "report generated"
            }
          }),
          artifactRefs: [
            { artifactId: "artifact-1", fileName: "audit-report.md", downloadUrl: "/files/audit-report.md" }
          ]
        },
        {
          invocationId: "data-1",
          toolName: "data_analysis",
          structuredOutput: {
            metadata: {
              columns: ["status", "count"],
              sampleRows: [{ status: "PAY_SUCCESS", count: 2 }]
            }
          }
        }
      ],
      artifacts: [
        {
          artifactId: "artifact-1",
          fileName: "audit-report.md",
          downloadUrl: "/files/audit-report.md",
          toolInvocationId: "report-1"
        }
      ]
    });

    expect(panels.map((panel) => panel.kind)).toEqual(["audit", "file", "data"]);
    expect(panels[1].fileRefs[0].fileName).toBe("audit-report.md");
    expect(panels[2].rows[0].status).toBe("PAY_SUCCESS");
  });

  it("projects code and script execution outputs into code panels", () => {
    const [codePanel] = toolResultPanels({
      event: "tool_result",
      data: {
        invocationId: "code-1",
        toolName: "code_interpreter",
        structuredOutput: {
          title: "run python",
          summary: "ok",
          content: "done",
          metadata: {
            language: "python",
            exitCode: 0,
            stdout: "42",
            stderr: "",
            code: "print(42)"
          }
        }
      }
    });
    const [scriptPanel] = toolResultPanels({
      event: "tool_result",
      data: {
        invocationId: "script-1",
        toolName: "script_runner",
        structuredOutput: {
          title: "data-analysis/run",
          metadata: {
            runtime: "node",
            exitCode: 0,
            stdout: "chart generated"
          }
        }
      }
    });

    expect(codePanel.kind).toBe("code");
    expect(codePanel.metadata.stdout).toBe("42");
    expect(scriptPanel.kind).toBe("code");
    expect(scriptPanel.metadata.runtime).toBe("node");
  });

  it("projects reference resultMap code output into code panels", () => {
    const [panel] = toolResultPanels({
      event: "tool_result",
      data: {
        invocationId: "code-result-map-1",
        toolName: "code_interpreter",
        resultMap: {
          codeOutput: "sum=6",
          code: "print(sum([1,2,3]))",
          explain: "calculated list sum",
          fileInfo: [
            {
              resourceKey: "code-artifact-1",
              displayName: "result.csv",
              domainUrl: "/tool/files/result.csv",
              mimeType: "text/csv"
            }
          ]
        }
      }
    });

    expect(panel.kind).toBe("code");
    expect(panel.content).toBe("sum=6");
    expect(panel.metadata.codeOutput).toBe("sum=6");
    expect(panel.metadata.code).toBe("print(sum([1,2,3]))");
    expect(panel.fileRefs[0].fileName).toBe("result.csv");
  });

  it("projects reference resultMap data output into data panels", () => {
    const [panel] = toolResultPanels({
      event: "tool_result",
      data: {
        invocationId: "data-result-map-1",
        toolName: "data_analysis",
        resultMap: {
          summary: "rows=2",
          data: "two order status rows",
          columns: ["status", "count"],
          sampleRows: [
            { status: "PAY_SUCCESS", count: 10 },
            { status: "GROUP_SETTLED", count: 8 }
          ],
          resultMap: {
            fileList: [
              {
                fileId: "chart-1",
                fileName: "status-chart.png",
                previewUrl: "/tool/files/status-chart.png",
                contentType: "image/png"
              }
            ]
          }
        }
      }
    });

    expect(panel.kind).toBe("data");
    expect(panel.summary).toBe("rows=2");
    expect(panel.content).toBe("two order status rows");
    expect(panel.columns).toEqual(["status", "count"]);
    expect(panel.rows[0].status).toBe("PAY_SUCCESS");
    expect(panel.fileRefs[0].fileName).toBe("status-chart.png");
  });

  it("projects image generation files into image panels", () => {
    const [panel] = toolResultPanels({
      event: "tool_result",
      data: {
        invocationId: "image-1",
        toolName: "image_generation",
        structuredOutput: {
          summary: "generated 1 image",
          metadata: {
            prompt: "group-buy agent poster",
            size: "1024x1024"
          },
          fileRefs: [
            { artifactId: "img-1", fileName: "poster.png", previewUrl: "/files/poster.png", downloadUrl: "/files/poster.png" }
          ]
        }
      }
    });

    expect(panel.kind).toBe("image");
    expect(panel.fileRefs[0].fileName).toBe("poster.png");
    expect(panel.metadata.size).toBe("1024x1024");
  });

  it("projects multimodal analysis into multimodal panels", () => {
    const [panel] = toolResultPanels({
      event: "tool_result",
      data: {
        invocationId: "mrag-1",
        toolName: "multimodal_agent",
        structuredOutput: {
          title: "cross check",
          content: "image and file evidence are consistent",
          metadata: {
            task: "cross check paid orders",
            imageCount: 1,
            fileCount: 2
          }
        }
      }
    });

    expect(panel.kind).toBe("multimodal");
    expect(panel.content).toContain("consistent");
    expect(panel.metadata.imageCount).toBe(1);
  });
});
