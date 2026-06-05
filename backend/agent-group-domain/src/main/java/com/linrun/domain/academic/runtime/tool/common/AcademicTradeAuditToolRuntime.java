package com.linrun.domain.academic.runtime.tool.common;

import com.linrun.domain.academic.runtime.tool.AcademicToolCallCommand;
import com.linrun.domain.academic.runtime.tool.AcademicToolDefinition;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolFileRef;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputNames;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolStructuredOutput;
import com.linrun.domain.academic.runtime.tool.port.AcademicReportPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicTradeAuditPort;
import com.linrun.types.exception.AppException;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AcademicTradeAuditToolRuntime {

    private static final int DEFAULT_ORDER_LIMIT = 8;
    private static final int DEFAULT_FLOW_LIMIT = 20;

    private final AcademicTradeAuditPort tradeAuditPort;
    private final AcademicReportPort reportPort;

    public AcademicTradeAuditToolRuntime(AcademicTradeAuditPort tradeAuditPort) {
        this(tradeAuditPort, null);
    }

    public AcademicTradeAuditToolRuntime(AcademicTradeAuditPort tradeAuditPort,
                                         AcademicReportPort reportPort) {
        this.tradeAuditPort = tradeAuditPort;
        this.reportPort = reportPort;
    }

    public static AcademicToolDefinition definition() {
        return AcademicToolDefinition.builder(AcademicToolOutputNames.TRADE_AUDIT)
                .description("Read backend order, payment, group-buy, refund and quota facts for trade audit.")
                .category("trade")
                .source("local")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "orderId", Map.of("type", "string", "description", "Trade order id to audit."),
                                "teamId", Map.of("type", "string", "description", "Group-buy team id to inspect."),
                                "keyword", Map.of("type", "string", "description", "Optional order keyword when order id is absent."),
                                "recentOrderLimit", Map.of("type", "integer", "description", "Recent order count when order id is absent."),
                                "recentFlowLimit", Map.of("type", "integer", "description", "Recent quota flow count."),
                                "includeRecentFlows", Map.of("type", "boolean", "description", "Whether to include recent quota flows.")),
                        "required", List.of()))
                .requiredArguments(List.of())
                .enabled(true)
                .build();
    }

    public AcademicToolStructuredOutput call(AcademicToolCallCommand command) {
        if (tradeAuditPort == null) {
            throw new AppException("TRADE_AUDIT_0001", "trade audit port is not configured");
        }
        Map<String, Object> arguments = command == null ? Map.of() : command.getArguments();
        AcademicTradeAuditPort.AcademicTradeAuditRequest request =
                new AcademicTradeAuditPort.AcademicTradeAuditRequest(
                        firstPresent(text(arguments.get("userId")), command == null ? "" : command.getUserId()),
                        text(arguments.get("orderId")),
                        text(arguments.get("teamId")),
                        text(arguments.get("keyword")),
                        Math.max(1, integer(arguments.get("recentOrderLimit"), DEFAULT_ORDER_LIMIT)),
                        Math.max(1, integer(arguments.get("recentFlowLimit"), DEFAULT_FLOW_LIMIT)),
                        bool(arguments.get("includeRecentFlows"), true));

        AcademicTradeAuditPort.AcademicTradeAuditResult result = tradeAuditPort.audit(request);
        if (result == null) {
            throw new AppException("TRADE_AUDIT_0002", "trade audit returned empty result");
        }
        if (!result.success()) {
            throw new AppException("TRADE_AUDIT_0003", firstPresent(result.errorMessage(), "trade audit failed"));
        }

        Map<String, Object> metadata = new LinkedHashMap<>(result.metadata() == null ? Map.of() : result.metadata());
        metadata.put("snapshot", result.snapshot() == null ? Map.of() : result.snapshot());
        metadata.put("findings", result.findings() == null ? List.of() : result.findings());
        metadata.put("findingCount", result.findings() == null ? 0 : result.findings().size());
        metadata.put("reportFormat", "markdown");
        String reportContent = markdownReport(request, result);
        List<AcademicToolFileRef> fileRefs = materializeReport(command, request, result, reportContent, metadata);

        return AcademicToolStructuredOutput.builder(AcademicToolOutputNames.TRADE_AUDIT)
                .title(firstPresent(request.orderId(), request.teamId(), "trade audit"))
                .summary(result.summary())
                .content(reportContent)
                .metadata(metadata)
                .fileRefs(fileRefs)
                .build();
    }

    private List<AcademicToolFileRef> materializeReport(AcademicToolCallCommand command,
                                                        AcademicTradeAuditPort.AcademicTradeAuditRequest request,
                                                        AcademicTradeAuditPort.AcademicTradeAuditResult auditResult,
                                                        String reportContent,
                                                        Map<String, Object> metadata) {
        if (reportPort == null) {
            metadata.put("reportMaterialized", false);
            metadata.put("reportMaterializeReason", "report port is not configured");
            return List.of();
        }
        try {
            String title = firstPresent(request.orderId(), request.teamId(), "trade audit");
            AcademicReportPort.AcademicReportResult reportResult = reportPort.generate(
                    new AcademicReportPort.AcademicReportRequest(
                            command == null ? "" : command.getRequestId(),
                            "Generate a markdown trade audit report from verified backend facts.\n\n" + reportContent,
                            "Trade Audit Report - " + title,
                            firstPresent(auditResult.summary(), "trade facts checked"),
                            List.of(Map.<String, Object>of("heading", "Trade Audit Report", "content", reportContent)),
                            List.of(reportContent),
                            List.of(),
                            reportFileName(request),
                            "markdown",
                            "markdown",
                            false));
            if (reportResult == null || !reportResult.success()) {
                metadata.put("reportMaterialized", false);
                metadata.put("reportMaterializeReason",
                        reportResult == null ? "report tool returned empty result" : reportResult.errorMessage());
                return List.of();
            }
            metadata.put("reportMaterialized", true);
            metadata.put("reportProvider", "report_tool");
            metadata.put("reportSummary", reportResult.summary());
            return reportResult.fileRefs() == null ? List.of() : new ArrayList<>(reportResult.fileRefs());
        } catch (Exception e) {
            metadata.put("reportMaterialized", false);
            metadata.put("reportMaterializeReason", e.getMessage());
            return List.of();
        }
    }

    private String markdownReport(AcademicTradeAuditPort.AcademicTradeAuditRequest request,
                                  AcademicTradeAuditPort.AcademicTradeAuditResult result) {
        Map<String, Object> metadata = result.metadata() == null ? Map.of() : result.metadata();
        Map<String, Object> snapshot = result.snapshot() == null ? Map.of() : result.snapshot();
        List<Map<String, Object>> findings = result.findings() == null ? List.of() : result.findings();
        StringBuilder report = new StringBuilder();
        report.append("# Trade Audit Report\n\n");
        report.append("## Summary\n\n");
        report.append("- Result: ").append(firstPresent(result.summary(), "trade facts checked")).append('\n');
        report.append("- Highest severity: ").append(firstPresent(text(metadata.get("highestSeverity")), "INFO")).append('\n');
        report.append("- User id: ").append(firstPresent(request.userId(), text(snapshot.get("userId")), "unknown")).append('\n');
        if (StringUtils.hasText(request.orderId())) {
            report.append("- Order id: ").append(request.orderId()).append('\n');
        }
        if (StringUtils.hasText(request.teamId())) {
            report.append("- Team id: ").append(request.teamId()).append('\n');
        }
        report.append('\n');
        appendFactSection(report, "Trade order", snapshot.get("tradeOrder"));
        appendFactSection(report, "Payment order", snapshot.get("payOrder"));
        appendFactSection(report, "Group buy", groupBuyFacts(snapshot));
        appendFactSection(report, "Quota", quotaFacts(snapshot));
        report.append("## Findings\n\n");
        if (findings.isEmpty()) {
            report.append("- INFO NO_FINDING: No finding returned.\n");
        } else {
            for (Map<String, Object> finding : findings) {
                report.append("- ")
                        .append(firstPresent(text(finding.get("severity")), "INFO"))
                        .append(' ')
                        .append(firstPresent(text(finding.get("code")), "UNKNOWN"))
                        .append(": ")
                        .append(firstPresent(text(finding.get("message")), ""))
                        .append('\n');
            }
        }
        report.append("\n## Audit Rule\n\n");
        report.append("- Direct order can grant quota only after paid or deal done.\n");
        report.append("- Group-buy order can grant quota only after group settled or deal done.\n");
        report.append("- Refunded order with previous quota grant must have rollback flow.\n");
        return report.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> groupBuyFacts(Map<String, Object> snapshot) {
        Map<String, Object> facts = new LinkedHashMap<>();
        Object lock = snapshot.get("groupLock");
        if (lock instanceof Map<?, ?> map) {
            facts.put("lock", new LinkedHashMap<>((Map<String, Object>) map));
        }
        Object team = snapshot.get("groupTeam");
        if (team instanceof Map<?, ?> map) {
            facts.put("team", new LinkedHashMap<>((Map<String, Object>) map));
        }
        Object flags = snapshot.get("auditFlags");
        if (flags instanceof Map<?, ?> map) {
            facts.put("flags", new LinkedHashMap<>((Map<String, Object>) map));
        }
        return facts;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> quotaFacts(Map<String, Object> snapshot) {
        Map<String, Object> facts = new LinkedHashMap<>();
        Object account = snapshot.get("quotaAccount");
        if (account instanceof Map<?, ?> map) {
            facts.put("account", new LinkedHashMap<>((Map<String, Object>) map));
        }
        Object grant = snapshot.get("orderGrantFlow");
        if (grant instanceof Map<?, ?> map) {
            facts.put("grantFlow", new LinkedHashMap<>((Map<String, Object>) map));
        }
        Object rollback = snapshot.get("refundRollbackFlow");
        if (rollback instanceof Map<?, ?> map) {
            facts.put("rollbackFlow", new LinkedHashMap<>((Map<String, Object>) map));
        }
        Object recent = snapshot.get("recentQuotaFlows");
        if (recent instanceof List<?> list) {
            facts.put("recentFlows", list);
        }
        return facts;
    }

    @SuppressWarnings("unchecked")
    private void appendFactSection(StringBuilder report, String title, Object value) {
        report.append("## ").append(title).append("\n\n");
        if (value instanceof Map<?, ?> map && !map.isEmpty()) {
            ((Map<String, Object>) map).forEach((key, item) -> report.append("- ")
                    .append(key)
                    .append(": ")
                    .append(text(item))
                    .append('\n'));
        } else if (value instanceof List<?> list && !list.isEmpty()) {
            for (Object item : list) {
                report.append("- ").append(text(item)).append('\n');
            }
        } else {
            report.append("- No data.\n");
        }
        report.append('\n');
    }

    private boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = text(value);
        return StringUtils.hasText(text) ? Boolean.parseBoolean(text) : fallback;
    }

    private int integer(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(text(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String firstPresent(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String reportFileName(AcademicTradeAuditPort.AcademicTradeAuditRequest request) {
        String key = firstPresent(request.orderId(), request.teamId(), "latest");
        String slug = key.replaceAll("[^A-Za-z0-9._-]+", "-");
        if (!StringUtils.hasText(slug)) {
            slug = "latest";
        }
        return "trade-audit-" + slug + ".md";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
