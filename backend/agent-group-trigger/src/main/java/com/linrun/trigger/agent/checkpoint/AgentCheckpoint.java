package com.linrun.trigger.agent.checkpoint;

import com.linrun.trigger.agent.entity.event.ToolRecord;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 执行断点快照（checkpoint）。
 *
 * <p>断点续跑的核心数据载体：把一次 ReAct 执行中已经稳定下来的对话上下文（messages）、
 * 已用工具签名、工具执行流水和轮次计数完整保存，供中断后用同一个 continueTraceId 恢复继续执行。
 * continueTraceId 复用语义上等价于本次执行的 runId，但只在本快照存储内流转，避免侵入账本链路。
 *
 * <p>只读红线：快照本身只保存只读诊断上下文，恢复后续跑仍走同一套工具回调，不会因为续跑触发新的写操作。
 */
@Data
public class AgentCheckpoint {

    /** 续跑凭证，断点续跑用它定位快照（首次执行时生成，续跑时由调用方回传）。 */
    private String continueTraceId;

    /** 执行类型（skills / data / trade-diagnosis 等），恢复时用于核对。 */
    private String agentType;

    /** 会话 ID（内部 internalConversationId）。 */
    private String conversationId;

    /** 本次执行的用户问题，恢复时用于上下文压缩与推荐生成。 */
    private String question;

    /** 关联的文件 ID，可能为空。 */
    private String fileId;

    /** 首次执行时写库返回的会话主键，续跑时复用，避免对同一问题重复 saveQuestion。 */
    private long currentSessionId;

    /** 已经完成的推理轮次（恢复后从该轮次继续累计）。 */
    private int round;

    /** 已使用工具签名（对应 BaseAgent.usedTools），恢复后继续累加。 */
    private List<String> executedSignatures = new ArrayList<>();

    /** 工具执行流水（ToolRecord），用于断点处回看已发生的工具调用。 */
    private List<ToolRecord> toolRecords = new ArrayList<>();

    /** 序列化后的完整对话上下文，恢复后直接喂给下一轮 LLM 调用。 */
    private List<CheckpointMessage> messages = new ArrayList<>();

    /** 快照写入时间（epoch 毫秒），用于诊断与过期判断。 */
    private long savedAt;
}
