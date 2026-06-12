package com.linrun.trigger.support.tool;

import com.linrun.domain.support.metrics.AgentObservabilityMetrics;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolInvocationGuardTest {

    @Test
    void defaultConfigAllowsAnyTool() {
        ToolInvocationGuard guard = new ToolInvocationGuard("", "");
        assertTrue(guard.rejectReason("literature_search").isEmpty());
        assertTrue(guard.rejectReason("chart_render").isEmpty());
    }

    @Test
    void blockedListRejectsConfiguredToolCaseInsensitively() {
        ToolInvocationGuard guard = new ToolInvocationGuard("", "shell_exec, chart_render");
        assertTrue(guard.rejectReason("shell_exec").isPresent());
        assertTrue(guard.rejectReason("CHART_RENDER").isPresent());
        assertTrue(guard.rejectReason("shell_exec").orElse("").contains("shell_exec"));
        assertTrue(guard.rejectReason("literature_search").isEmpty());
    }

    @Test
    void allowListOnlyPermitsConfiguredTools() {
        ToolInvocationGuard guard = new ToolInvocationGuard("literature_search,table_rag", "");
        assertTrue(guard.rejectReason("literature_search").isEmpty());
        assertTrue(guard.rejectReason("Table_RAG").isEmpty());
        assertTrue(guard.rejectReason("shell_exec").isPresent());
    }

    @Test
    void blankToolNameIsRejected() {
        ToolInvocationGuard guard = new ToolInvocationGuard("", "");
        assertTrue(guard.rejectReason("  ").isPresent());
        assertTrue(guard.rejectReason(null).isPresent());
    }

    @Test
    void toolExecutorFailsFastWithoutInvokingBlockedTool() {
        ToolExecutor executor = new ToolExecutor(AgentObservabilityMetrics.noop(),
                new ToolInvocationGuard("", "shell_exec"));
        AtomicBoolean invoked = new AtomicBoolean(false);

        ToolExecution<String> blocked = executor.execute("shell_exec", "execute", "ok", () -> {
            invoked.set(true);
            return "result";
        });

        assertFalse(blocked.isSuccess());
        assertFalse(invoked.get(), "被禁用的工具不应被实际调用");
        assertTrue(blocked.getMessage().contains("blocked by policy"));
    }

    @Test
    void toolExecutorStillRunsAllowedTool() {
        ToolExecutor executor = new ToolExecutor(AgentObservabilityMetrics.noop(),
                new ToolInvocationGuard("literature_search", ""));

        ToolExecution<String> allowed = executor.execute("literature_search", "execute", "ok", () -> "result");

        assertTrue(allowed.isSuccess());
    }
}
