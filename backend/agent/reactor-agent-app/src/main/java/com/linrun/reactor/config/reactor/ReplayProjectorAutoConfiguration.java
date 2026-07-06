package com.linrun.reactor.config.reactor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.linrun.reactor.domain.agent.ledger.ExecutionLedgerQueryService;
import com.linrun.reactor.domain.agent.ledger.replay.ConversationHistoryReplayService;
import com.linrun.reactor.domain.agent.ledger.replay.HistoryReplayPrinter;
import com.linrun.reactor.domain.agent.ledger.replay.ReplayProjector;
import com.linrun.reactor.domain.agent.ledger.replay.projector.ToolInvocationProjector;
import com.linrun.reactor.domain.agent.ledger.replay.projector.ToolInvocationProjectorRegistry;
import com.linrun.reactor.domain.agent.ledger.replay.projector.impl.CodeInterpreterToolInvocationProjector;
import com.linrun.reactor.domain.agent.ledger.replay.projector.impl.DataAnalysisToolInvocationProjector;
import com.linrun.reactor.domain.agent.ledger.replay.projector.impl.DefaultToolInvocationProjector;
import com.linrun.reactor.domain.agent.ledger.replay.projector.impl.DeepSearchToolInvocationProjector;
import com.linrun.reactor.domain.agent.ledger.replay.projector.impl.FileToolInvocationProjector;
import com.linrun.reactor.domain.agent.ledger.replay.projector.impl.ImageGenerationToolInvocationProjector;
import com.linrun.reactor.domain.agent.ledger.replay.projector.impl.MultiModalToolInvocationProjector;
import com.linrun.reactor.domain.agent.ledger.replay.projector.impl.PlanningToolInvocationProjector;
import com.linrun.reactor.domain.agent.ledger.replay.projector.impl.ReportToolInvocationProjector;
import com.linrun.reactor.domain.agent.ledger.replay.projector.impl.ScriptRunnerToolInvocationProjector;

import java.util.List;

/**
 * 历史回放相关 Bean 装配。
 */
@Configuration
public class ReplayProjectorAutoConfiguration {

    @Bean
    public FileToolInvocationProjector fileToolInvocationProjector() {
        return new FileToolInvocationProjector();
    }

    @Bean
    public PlanningToolInvocationProjector planningToolInvocationProjector() {
        return new PlanningToolInvocationProjector();
    }

    @Bean
    public DeepSearchToolInvocationProjector deepSearchToolInvocationProjector() {
        return new DeepSearchToolInvocationProjector();
    }

    @Bean
    public CodeInterpreterToolInvocationProjector codeInterpreterToolInvocationProjector() {
        return new CodeInterpreterToolInvocationProjector();
    }

    @Bean
    public ReportToolInvocationProjector reportToolInvocationProjector() {
        return new ReportToolInvocationProjector();
    }

    @Bean
    public DataAnalysisToolInvocationProjector dataAnalysisToolInvocationProjector() {
        return new DataAnalysisToolInvocationProjector();
    }

    @Bean
    public MultiModalToolInvocationProjector multiModalToolInvocationProjector() {
        return new MultiModalToolInvocationProjector();
    }

    @Bean
    public ImageGenerationToolInvocationProjector imageGenerationToolInvocationProjector() {
        return new ImageGenerationToolInvocationProjector();
    }

    @Bean
    public ScriptRunnerToolInvocationProjector scriptRunnerToolInvocationProjector() {
        return new ScriptRunnerToolInvocationProjector();
    }

    @Bean
    public DefaultToolInvocationProjector defaultToolInvocationProjector() {
        return new DefaultToolInvocationProjector();
    }

    @Bean
    public ToolInvocationProjectorRegistry toolInvocationProjectorRegistry(
            List<ToolInvocationProjector> projectors,
            DefaultToolInvocationProjector defaultProjector) {
        return new ToolInvocationProjectorRegistry(projectors, defaultProjector);
    }

    @Bean
    public ReplayProjector replayProjector(ToolInvocationProjectorRegistry registry) {
        return new ReplayProjector(registry);
    }

    @Bean
    public HistoryReplayPrinter historyReplayPrinter() {
        return new HistoryReplayPrinter();
    }

    @Bean
    public ConversationHistoryReplayService conversationHistoryReplayService(
            ExecutionLedgerQueryService executionLedgerQueryService,
            ReplayProjector replayProjector,
            HistoryReplayPrinter historyReplayPrinter) {
        return new ConversationHistoryReplayService(
                executionLedgerQueryService,
                replayProjector,
                historyReplayPrinter
        );
    }
}
