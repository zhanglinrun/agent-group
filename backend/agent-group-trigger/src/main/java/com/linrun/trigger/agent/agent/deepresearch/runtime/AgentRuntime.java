package com.linrun.trigger.agent.agent.deepresearch.runtime;

/**
 * Agent 执行运行时统一入口。
 *
 * @param <C> 运行上下文
 * @param <R> 运行结果
 */
public interface AgentRuntime<C, R> {

    R execute(C context);
}
