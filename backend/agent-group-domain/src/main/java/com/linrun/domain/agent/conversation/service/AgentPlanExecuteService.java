package com.linrun.domain.agent.conversation.service;

import com.linrun.domain.agent.conversation.model.AgentExecutionStage;
import com.linrun.domain.agent.conversation.model.AgentPlan;
import com.linrun.domain.agent.conversation.model.AgentSkill;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class AgentPlanExecuteService {

    public AgentPlan enrich(String question, AgentPlan plan, List<AgentSkill> skills) {
        if (plan == null) {
            return null;
        }
        boolean clarificationRequired = !StringUtils.hasText(question)
                || question.trim().length() < 4
                || question.contains("随便");
        plan.setClarificationRequired(clarificationRequired);
        plan.setSkills(skills);
        plan.setExecutionStages(stages(plan, clarificationRequired));
        plan.setCritique(critique(plan, skills));
        return plan;
    }

    private List<AgentExecutionStage> stages(AgentPlan plan, boolean clarificationRequired) {
        List<AgentExecutionStage> stages = new ArrayList<>();
        stages.add(new AgentExecutionStage(
                "clarify",
                clarificationRequired ? "问题过宽时先补齐预算、用途和交易状态" : "问题信息可直接进入工具规划",
                clarificationRequired ? "NEED_CLARIFY" : "PASS",
                "用户意图、约束和当前会话上下文"));
        stages.add(new AgentExecutionStage(
                "topic",
                "识别额度、退款或订单查询主题",
                "PASS",
                plan.getIntent() == null ? "意图分类结果" : plan.getIntent().name()));
        stages.add(new AgentExecutionStage(
                "plan",
                "按风险从低到高排列工具调用",
                "READY",
                String.join(",", plan.toolNames())));
        stages.add(new AgentExecutionStage(
                "execute",
                "执行知识检索、推荐、拼团试算或订单查询",
                "WAIT_TOOL_RESULT",
                "工具返回值和引用片段"));
        stages.add(new AgentExecutionStage(
                "critique",
                "检查价格、库存、订单状态是否全部来自后端工具",
                "READY",
                "高风险字段来源"));
        stages.add(new AgentExecutionStage(
                "summarize",
                "生成带依据的额度包回答和自检结果",
                "READY",
                "推荐理由、额度包信息和引用"));
        return stages;
    }

    private String critique(AgentPlan plan, List<AgentSkill> skills) {
        boolean hasTradeTool = plan.hasTool(AgentToolRegistry.GROUP_TRIAL)
                || plan.hasTool(AgentToolRegistry.ORDER_STATUS);
        String skillNames = skills == null || skills.isEmpty()
                ? "未匹配专用技能"
                : String.join(",", skills.stream().map(AgentSkill::getSkillName).toList());
        if (hasTradeTool) {
            return skillNames + "；交易敏感信息已规划后端工具校验";
        }
        return skillNames + "；回答不得自行生成价格、库存、订单状态";
    }
}
