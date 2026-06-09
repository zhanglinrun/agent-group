package com.linrun.trigger.agent.agent.pptx.strategy;

import com.linrun.trigger.agent.entity.record.pptx.AiPptInst;
import com.linrun.trigger.agent.entity.record.pptx.PptInstStatus;
import reactor.core.publisher.Sinks;

/**
 * PPT状态策略接�?
 * 使用策略模式处理不同状态的处理逻辑
 */
public interface PptStateStrategy {

    /**
     * 执行该状态的处理逻辑
     *
     * @param inst            PPT实例
     * @param sink            响应�?
     * @param query           用户查询
     * @param thinkingBuffer  思考缓冲区
     * @param context         策略上下�?
     */
    void execute(AiPptInst inst, Sinks.Many<String> sink, String query,
                 StringBuilder thinkingBuffer, PptStateStrategyContext context);

    /**
     * 获取该策略对应的目标状�?
     * 执行成功后，状态应该变为这个状�?
     *
     * @return 目标状�?
     */
    PptInstStatus getTargetStatus();
}















