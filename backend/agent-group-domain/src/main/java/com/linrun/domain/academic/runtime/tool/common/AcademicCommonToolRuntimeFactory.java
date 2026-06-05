package com.linrun.domain.academic.runtime.tool.common;

import com.linrun.domain.academic.runtime.tool.AcademicToolRuntimeRegistry;
import com.linrun.domain.academic.runtime.tool.port.AcademicCodeInterpreterPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicDataAnalysisPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicDeepSearchPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicFileToolPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicImageGenerationPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicMultimodalAnalysisPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicNl2SqlPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicReportPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicScriptRunnerPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicTableRagPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicTradeAuditPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicWebFetchPort;

public class AcademicCommonToolRuntimeFactory {

    private final AcademicCodeInterpreterPort codeInterpreterPort;
    private final AcademicWebFetchPort webFetchPort;
    private final AcademicDataAnalysisPort dataAnalysisPort;
    private final AcademicReportPort reportPort;
    private final AcademicImageGenerationPort imageGenerationPort;
    private final AcademicMultimodalAnalysisPort multimodalAnalysisPort;
    private final AcademicDeepSearchPort deepSearchPort;
    private final AcademicFileToolPort fileToolPort;
    private final AcademicScriptRunnerPort scriptRunnerPort;
    private final AcademicTableRagPort tableRagPort;
    private final AcademicNl2SqlPort nl2SqlPort;
    private final AcademicTradeAuditPort tradeAuditPort;

    private AcademicCommonToolRuntimeFactory(Builder builder) {
        this.codeInterpreterPort = builder.codeInterpreterPort;
        this.webFetchPort = builder.webFetchPort;
        this.dataAnalysisPort = builder.dataAnalysisPort;
        this.reportPort = builder.reportPort;
        this.imageGenerationPort = builder.imageGenerationPort;
        this.multimodalAnalysisPort = builder.multimodalAnalysisPort;
        this.deepSearchPort = builder.deepSearchPort;
        this.fileToolPort = builder.fileToolPort;
        this.scriptRunnerPort = builder.scriptRunnerPort;
        this.tableRagPort = builder.tableRagPort;
        this.nl2SqlPort = builder.nl2SqlPort;
        this.tradeAuditPort = builder.tradeAuditPort;
    }

    public static Builder builder() {
        return new Builder();
    }

    public AcademicToolRuntimeRegistry buildRegistry() {
        return registerAll(new AcademicToolRuntimeRegistry());
    }

    public AcademicToolRuntimeRegistry registerAll(AcademicToolRuntimeRegistry registry) {
        AcademicToolRuntimeRegistry target = registry == null ? new AcademicToolRuntimeRegistry() : registry;
        target.registerStructured(AcademicWebFetchToolRuntime.definition(), new AcademicWebFetchToolRuntime(webFetchPort)::call);
        target.registerStructured(AcademicDataAnalysisToolRuntime.definition(), new AcademicDataAnalysisToolRuntime(dataAnalysisPort)::call);
        target.registerStructured(AcademicReportToolRuntime.definition(), new AcademicReportToolRuntime(reportPort)::call);
        target.registerStructured(AcademicPlanningToolRuntime.definition(), new AcademicPlanningToolRuntime()::call);
        if (codeInterpreterPort != null) {
            target.registerStructured(AcademicCodeInterpreterToolRuntime.definition(),
                    new AcademicCodeInterpreterToolRuntime(codeInterpreterPort)::call);
        }
        if (imageGenerationPort != null) {
            target.registerStructured(AcademicImageGenerationToolRuntime.definition(),
                    new AcademicImageGenerationToolRuntime(imageGenerationPort)::call);
        }
        if (multimodalAnalysisPort != null) {
            target.registerStructured(AcademicMultimodalAgentToolRuntime.definition(),
                    new AcademicMultimodalAgentToolRuntime(multimodalAnalysisPort)::call);
        }
        if (deepSearchPort != null) {
            target.registerStructured(AcademicDeepSearchToolRuntime.definition(),
                    new AcademicDeepSearchToolRuntime(deepSearchPort)::call);
        }
        if (fileToolPort != null) {
            target.registerStructured(AcademicFileToolRuntime.definition(), new AcademicFileToolRuntime(fileToolPort)::call);
        }
        if (scriptRunnerPort != null) {
            target.registerStructured(AcademicScriptRunnerToolRuntime.definition(),
                    new AcademicScriptRunnerToolRuntime(scriptRunnerPort)::call);
        }
        if (tableRagPort != null) {
            target.registerStructured(AcademicTableRagToolRuntime.definition(),
                    new AcademicTableRagToolRuntime(tableRagPort)::call);
        }
        if (nl2SqlPort != null) {
            target.registerStructured(AcademicNl2SqlToolRuntime.definition(), new AcademicNl2SqlToolRuntime(nl2SqlPort)::call);
        }
        if (tradeAuditPort != null) {
            target.registerStructured(AcademicTradeAuditToolRuntime.definition(),
                    new AcademicTradeAuditToolRuntime(tradeAuditPort, reportPort)::call);
        }
        return target;
    }

    public static final class Builder {

        private AcademicCodeInterpreterPort codeInterpreterPort;
        private AcademicWebFetchPort webFetchPort;
        private AcademicDataAnalysisPort dataAnalysisPort;
        private AcademicReportPort reportPort;
        private AcademicImageGenerationPort imageGenerationPort;
        private AcademicMultimodalAnalysisPort multimodalAnalysisPort;
        private AcademicDeepSearchPort deepSearchPort;
        private AcademicFileToolPort fileToolPort;
        private AcademicScriptRunnerPort scriptRunnerPort;
        private AcademicTableRagPort tableRagPort;
        private AcademicNl2SqlPort nl2SqlPort;
        private AcademicTradeAuditPort tradeAuditPort;

        private Builder() {
        }

        public Builder codeInterpreterPort(AcademicCodeInterpreterPort codeInterpreterPort) {
            this.codeInterpreterPort = codeInterpreterPort;
            return this;
        }

        public Builder webFetchPort(AcademicWebFetchPort webFetchPort) {
            this.webFetchPort = webFetchPort;
            return this;
        }

        public Builder dataAnalysisPort(AcademicDataAnalysisPort dataAnalysisPort) {
            this.dataAnalysisPort = dataAnalysisPort;
            return this;
        }

        public Builder reportPort(AcademicReportPort reportPort) {
            this.reportPort = reportPort;
            return this;
        }

        public Builder imageGenerationPort(AcademicImageGenerationPort imageGenerationPort) {
            this.imageGenerationPort = imageGenerationPort;
            return this;
        }

        public Builder multimodalAnalysisPort(AcademicMultimodalAnalysisPort multimodalAnalysisPort) {
            this.multimodalAnalysisPort = multimodalAnalysisPort;
            return this;
        }

        public Builder deepSearchPort(AcademicDeepSearchPort deepSearchPort) {
            this.deepSearchPort = deepSearchPort;
            return this;
        }

        public Builder fileToolPort(AcademicFileToolPort fileToolPort) {
            this.fileToolPort = fileToolPort;
            return this;
        }

        public Builder scriptRunnerPort(AcademicScriptRunnerPort scriptRunnerPort) {
            this.scriptRunnerPort = scriptRunnerPort;
            return this;
        }

        public Builder tableRagPort(AcademicTableRagPort tableRagPort) {
            this.tableRagPort = tableRagPort;
            return this;
        }

        public Builder nl2SqlPort(AcademicNl2SqlPort nl2SqlPort) {
            this.nl2SqlPort = nl2SqlPort;
            return this;
        }

        public Builder tradeAuditPort(AcademicTradeAuditPort tradeAuditPort) {
            this.tradeAuditPort = tradeAuditPort;
            return this;
        }

        public AcademicCommonToolRuntimeFactory build() {
            return new AcademicCommonToolRuntimeFactory(this);
        }
    }
}
