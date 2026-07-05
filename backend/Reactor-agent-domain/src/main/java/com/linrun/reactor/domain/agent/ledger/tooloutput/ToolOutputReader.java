package com.linrun.reactor.domain.agent.ledger.tooloutput;

import com.linrun.reactor.domain.agent.ledger.model.tooloutput.ToolOutputView;
import com.linrun.reactor.domain.agent.ledger.model.tooloutput.ToolStructuredOutput;

import java.util.Optional;

/**
 * rich tool 输出读取契约。
 */
public interface ToolOutputReader {

    Optional<ToolStructuredOutput> readByInvocationId(String toolName, Long toolInvocationId);

    Optional<ToolOutputView> readDirect(String requestId, String toolCallId);
}
