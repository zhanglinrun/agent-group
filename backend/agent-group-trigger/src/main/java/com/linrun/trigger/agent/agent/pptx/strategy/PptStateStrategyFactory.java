package com.linrun.trigger.agent.agent.pptx.strategy;

import com.linrun.trigger.agent.entity.record.pptx.AiPptInst;
import com.linrun.trigger.agent.entity.record.pptx.PptInstStatus;
import com.linrun.trigger.agent.prompts.PptBuilderPrompts;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Sinks;

import java.util.HashMap;
import java.util.Map;

/**
 * PPT状态策略工??
 * 使用工厂模式管理所有状态策??
 */
@Slf4j
public class PptStateStrategyFactory {

    private static final Map<PptInstStatus, PptStateStrategy> STRATEGY_MAP = new HashMap<>();

    private PptStateStrategyFactory() {
        // 私有构造函数，防止实例??
    }

    static {
        // 初始化策略映??
        STRATEGY_MAP.put(PptInstStatus.INIT, new RequirementStrategy());
        STRATEGY_MAP.put(PptInstStatus.REQUIREMENT, new RequirementStrategy());
        STRATEGY_MAP.put(PptInstStatus.TEMPLATE, new TemplateStrategy());
        STRATEGY_MAP.put(PptInstStatus.OUTLINE, new OutlineStrategy());
        STRATEGY_MAP.put(PptInstStatus.SEARCH, new SearchStrategy());
        STRATEGY_MAP.put(PptInstStatus.SCHEMA, new SchemaStrategy());
        STRATEGY_MAP.put(PptInstStatus.RENDER, new RenderStrategy());
        STRATEGY_MAP.put(PptInstStatus.SUCCESS, new SuccessStrategy());
        STRATEGY_MAP.put(PptInstStatus.FAILED, new FailedStrategy());
    }

    /**
     * 获取工厂实例
     */
    public static PptStateStrategyFactory getInstance() {
        return SingletonHolder.INSTANCE;
    }

    /**
     * 获取指定状态的策略
     */
    public PptStateStrategy getStrategy(PptInstStatus status) {
        PptStateStrategy strategy = STRATEGY_MAP.get(status);
        if (strategy == null) {
            log.warn("未找到状态对应的策略: {}", status);
            return new DefaultStrategy();
        }
        return strategy;
    }

    /**
     * 执行下一个状??
     */
    public void executeNextState(AiPptInst inst, Sinks.Many<String> sink, String query,
                                  StringBuilder thinkingBuffer, PptStateStrategyContext context) {
        try {
            // 重新加载最新状??
            AiPptInst latestInst = context.getPptInstService().getById(inst.getId());
            if (latestInst != null) {
                inst = latestInst;
            }

            // 检查是否有错误信息，如果有则说明是断点重连
            if (latestInst != null && latestInst.getErrorMsg() != null && !latestInst.getErrorMsg().isEmpty()
                    && latestInst.getStatusEnum() != PptInstStatus.SUCCESS) {
                log.info("检测到断点重连: status={}, errorMsg={}",
                        latestInst.getStatusEnum(), latestInst.getErrorMsg());
                // 清空错误信息，允许继续执??
                context.getPptInstService().updateError(latestInst.getId(), "", latestInst.getStatusEnum());
            }

            PptInstStatus status = inst.getStatusEnum();
            log.info("状态机执行: status={}", status);
            sink.tryEmitNext(context.createPptStatusResponse(
                    status == null ? "" : status.name(), "进入PPT阶段：" + statusLabel(status), inst));

            PptStateStrategy strategy = getStrategy(status);
            strategy.execute(inst, sink, query, thinkingBuffer, context);
        } catch (Exception e) {
            log.error("继续状态机执行失败", e);
            sink.tryEmitError(e);
        }
    }

    private String statusLabel(PptInstStatus status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case INIT, REQUIREMENT -> "需求确认";
            case SEARCH -> "资料收集";
            case TEMPLATE -> "模板选择";
            case OUTLINE -> "大纲生成";
            case SCHEMA -> "内容生成";
            case RENDER -> "文件渲染";
            case SUCCESS -> "生成完成";
            case FAILED -> "执行失败";
        };
    }

    /**
     * 执行 FAILED 状态策??
     * 统一处理失败场景，避免各策略重复创建 FailedStrategy 实例
     */
    public void executeFailedState(AiPptInst inst, Sinks.Many<String> sink, String query,
                                  StringBuilder thinkingBuffer, PptStateStrategyContext context) {
        PptStateStrategy failedStrategy = getStrategy(PptInstStatus.FAILED);
        failedStrategy.execute(inst, sink, query, thinkingBuffer, context);
    }

    /**
     * 执行 Schema 策略，支持修改模??
     * 用于修改流程，直接调??SchemaStrategy 而不需要通过状态机流转
     *
     * @param inst PPT 实例
     * @param sink 输出 sink
     * @param query 用户查询
     * @param thinkingBuffer 思考缓??
     * @param context 策略上下??
     */
    public void executeSchemaStrategy(AiPptInst inst, Sinks.Many<String> sink, String query,
                                      StringBuilder thinkingBuffer, PptStateStrategyContext context) {
        SchemaStrategy schemaStrategy = new SchemaStrategy();
        String modifyPrompt = PptBuilderPrompts.getSchemaModifyPrompt(query, inst.getPptSchema());
        sink.tryEmitNext(context.createPptStatusResponse(
                PptInstStatus.SCHEMA.name(), "进入PPT阶段：内容生成", inst));
        schemaStrategy.executeWithModifyPrompt(inst, sink, query, thinkingBuffer, context, modifyPrompt);
    }

    /**
     * 单例持有??
     */
    private static class SingletonHolder {
        private static final PptStateStrategyFactory INSTANCE = new PptStateStrategyFactory();
    }

    /**
     * 默认策略，用于处理未知状??
     */
    private static class DefaultStrategy implements PptStateStrategy {
        @Override
        public void execute(AiPptInst inst, Sinks.Many<String> sink, String query,
                            StringBuilder thinkingBuffer, PptStateStrategyContext context) {
            log.warn("未知状态 {}", inst.getStatusEnum());
            sink.tryEmitNext(context.createThinkingResponse("状态异常，终止执行\n"));
            sink.tryEmitComplete();
        }

        @Override
        public PptInstStatus getTargetStatus() {
            return PptInstStatus.FAILED;
        }
    }
}















