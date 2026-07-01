package com.linrun.domain.agent.runtime.tool.common;

import com.linrun.domain.agent.runtime.tool.AgentToolRuntimeRegistry;
import com.linrun.domain.agent.runtime.tool.port.AgentCodeInterpreterPort;
import com.linrun.domain.agent.runtime.tool.port.AgentDataAnalysisPort;
import com.linrun.domain.agent.runtime.tool.port.AgentDeepSearchPort;
import com.linrun.domain.agent.runtime.tool.port.AgentFileToolPort;
import com.linrun.domain.agent.runtime.tool.port.AgentImageGenerationPort;
import com.linrun.domain.agent.runtime.tool.port.AgentMultimodalAnalysisPort;
import com.linrun.domain.agent.runtime.tool.port.AgentNl2SqlPort;
import com.linrun.domain.agent.runtime.tool.port.AgentReportPort;
import com.linrun.domain.agent.runtime.tool.port.AgentScriptRunnerPort;
import com.linrun.domain.agent.runtime.tool.port.AgentTableRagPort;
import com.linrun.domain.agent.runtime.tool.port.AgentWebFetchPort;
import com.linrun.domain.trade.service.TradeConsistencyCheckService;

public class AgentCommonToolRuntimeFactory {

    private final AgentCodeInterpreterPort codeInterpreterPort;
    private final AgentWebFetchPort webFetchPort;
    private final AgentDataAnalysisPort dataAnalysisPort;
    private final AgentReportPort reportPort;
    private final AgentImageGenerationPort imageGenerationPort;
    private final AgentMultimodalAnalysisPort multimodalAnalysisPort;
    private final AgentDeepSearchPort deepSearchPort;
    private final AgentFileToolPort fileToolPort;
    private final AgentScriptRunnerPort scriptRunnerPort;
    private final AgentTableRagPort tableRagPort;
    private final AgentNl2SqlPort nl2SqlPort;
    private final TradeConsistencyCheckService tradeConsistencyCheckService;

    private AgentCommonToolRuntimeFactory(Builder builder) {
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
        this.tradeConsistencyCheckService = builder.tradeConsistencyCheckService;
    }

    public static Builder builder() {
        return new Builder();
    }

    public AgentToolRuntimeRegistry buildRegistry() {
        return registerAll(new AgentToolRuntimeRegistry());
    }

    public AgentToolRuntimeRegistry registerAll(AgentToolRuntimeRegistry registry) {
        AgentToolRuntimeRegistry target = registry == null ? new AgentToolRuntimeRegistry() : registry;
        target.registerStructured(AgentWebFetchToolRuntime.definition(), new AgentWebFetchToolRuntime(webFetchPort)::call);
        target.registerStructured(AgentDataAnalysisToolRuntime.definition(), new AgentDataAnalysisToolRuntime(dataAnalysisPort)::call);
        target.registerStructured(AgentReportToolRuntime.definition(), new AgentReportToolRuntime(reportPort)::call);
        target.registerStructured(AgentPlanningToolRuntime.definition(), new AgentPlanningToolRuntime()::call);
        if (codeInterpreterPort != null) {
            target.registerStructured(AgentCodeInterpreterToolRuntime.definition(),
                    new AgentCodeInterpreterToolRuntime(codeInterpreterPort)::call);
        }
        if (imageGenerationPort != null) {
            target.registerStructured(AgentImageGenerationToolRuntime.definition(),
                    new AgentImageGenerationToolRuntime(imageGenerationPort)::call);
        }
        if (multimodalAnalysisPort != null) {
            target.registerStructured(AgentMultimodalAgentToolRuntime.definition(),
                    new AgentMultimodalAgentToolRuntime(multimodalAnalysisPort)::call);
        }
        if (deepSearchPort != null) {
            target.registerStructured(AgentDeepSearchToolRuntime.definition(),
                    new AgentDeepSearchToolRuntime(deepSearchPort)::call);
        }
        if (fileToolPort != null) {
            target.registerStructured(AgentFileToolRuntime.definition(), new AgentFileToolRuntime(fileToolPort)::call);
        }
        if (scriptRunnerPort != null) {
            target.registerStructured(AgentScriptRunnerToolRuntime.definition(),
                    new AgentScriptRunnerToolRuntime(scriptRunnerPort)::call);
        }
        if (tableRagPort != null) {
            target.registerStructured(AgentTableRagToolRuntime.definition(),
                    new AgentTableRagToolRuntime(tableRagPort)::call);
        }
        if (nl2SqlPort != null) {
            target.registerStructured(AgentNl2SqlToolRuntime.definition(), new AgentNl2SqlToolRuntime(nl2SqlPort)::call);
        }
        if (tradeConsistencyCheckService != null) {
            AgentTradeDiagnosisToolRuntime tradeRuntime =
                    new AgentTradeDiagnosisToolRuntime(tradeConsistencyCheckService);
            target.registerStructured(AgentTradeDiagnosisToolRuntime.diagnoseDefinition(), tradeRuntime::diagnose);
            target.registerStructured(AgentTradeDiagnosisToolRuntime.listDefinition(), tradeRuntime::list);
        }
        return target;
    }

    public static final class Builder {

        private AgentCodeInterpreterPort codeInterpreterPort;
        private AgentWebFetchPort webFetchPort;
        private AgentDataAnalysisPort dataAnalysisPort;
        private AgentReportPort reportPort;
        private AgentImageGenerationPort imageGenerationPort;
        private AgentMultimodalAnalysisPort multimodalAnalysisPort;
        private AgentDeepSearchPort deepSearchPort;
        private AgentFileToolPort fileToolPort;
        private AgentScriptRunnerPort scriptRunnerPort;
        private AgentTableRagPort tableRagPort;
        private AgentNl2SqlPort nl2SqlPort;
        private TradeConsistencyCheckService tradeConsistencyCheckService;

        private Builder() {
        }

        public Builder codeInterpreterPort(AgentCodeInterpreterPort codeInterpreterPort) {
            this.codeInterpreterPort = codeInterpreterPort;
            return this;
        }

        public Builder webFetchPort(AgentWebFetchPort webFetchPort) {
            this.webFetchPort = webFetchPort;
            return this;
        }

        public Builder dataAnalysisPort(AgentDataAnalysisPort dataAnalysisPort) {
            this.dataAnalysisPort = dataAnalysisPort;
            return this;
        }

        public Builder reportPort(AgentReportPort reportPort) {
            this.reportPort = reportPort;
            return this;
        }

        public Builder imageGenerationPort(AgentImageGenerationPort imageGenerationPort) {
            this.imageGenerationPort = imageGenerationPort;
            return this;
        }

        public Builder multimodalAnalysisPort(AgentMultimodalAnalysisPort multimodalAnalysisPort) {
            this.multimodalAnalysisPort = multimodalAnalysisPort;
            return this;
        }

        public Builder deepSearchPort(AgentDeepSearchPort deepSearchPort) {
            this.deepSearchPort = deepSearchPort;
            return this;
        }

        public Builder fileToolPort(AgentFileToolPort fileToolPort) {
            this.fileToolPort = fileToolPort;
            return this;
        }

        public Builder scriptRunnerPort(AgentScriptRunnerPort scriptRunnerPort) {
            this.scriptRunnerPort = scriptRunnerPort;
            return this;
        }

        public Builder tableRagPort(AgentTableRagPort tableRagPort) {
            this.tableRagPort = tableRagPort;
            return this;
        }

        public Builder nl2SqlPort(AgentNl2SqlPort nl2SqlPort) {
            this.nl2SqlPort = nl2SqlPort;
            return this;
        }

        public Builder tradeConsistencyCheckService(TradeConsistencyCheckService tradeConsistencyCheckService) {
            this.tradeConsistencyCheckService = tradeConsistencyCheckService;
            return this;
        }

        public AgentCommonToolRuntimeFactory build() {
            return new AgentCommonToolRuntimeFactory(this);
        }
    }
}
