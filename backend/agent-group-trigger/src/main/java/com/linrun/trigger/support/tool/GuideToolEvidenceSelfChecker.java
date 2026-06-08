package com.linrun.trigger.support.tool;

import com.linrun.domain.agent.conversation.model.AgentPlan;
import com.linrun.domain.agent.conversation.model.GuideDecisionResult;
import com.linrun.domain.agent.conversation.model.GuideProduct;
import com.linrun.domain.agent.conversation.model.GuideReference;
import com.linrun.domain.agent.conversation.service.AgentToolRegistry;
import com.linrun.domain.groupbuy.model.GroupBuyActivityStatus;
import com.linrun.domain.groupbuy.model.GroupBuyTrialResult;
import org.springframework.util.StringUtils;

import java.util.List;

public class GuideToolEvidenceSelfChecker {

    public GuideToolEvidenceCheck check(AgentPlan plan,
                                        List<GuideReference> references,
                                        GuideDecisionResult decisionResult,
                                        ToolExecution<GroupBuyTrialResult> groupTrialExecution) {
        if (plan != null && plan.hasTool(AgentToolRegistry.KNOWLEDGE_SEARCH)
                && (references == null || references.isEmpty())) {
            return GuideToolEvidenceCheck.failed("knowledge reference missing");
        }
        GuideProduct product = decisionResult == null ? null : decisionResult.getProduct();
        if (product == null || !StringUtils.hasText(product.getGoodsId()) || product.getGroupPrice() == null) {
            return GuideToolEvidenceCheck.failed("recommended product evidence missing");
        }
        if (plan != null && plan.hasTool(AgentToolRegistry.GROUP_TRIAL)) {
            GuideToolEvidenceCheck trialCheck = checkGroupTrial(product, groupTrialExecution);
            if (!trialCheck.passed()) {
                return trialCheck;
            }
        }
        return GuideToolEvidenceCheck.ok();
    }

    private GuideToolEvidenceCheck checkGroupTrial(GuideProduct product,
                                                   ToolExecution<GroupBuyTrialResult> groupTrialExecution) {
        if (groupTrialExecution == null || !groupTrialExecution.isSuccess() || groupTrialExecution.getResult() == null) {
            return GuideToolEvidenceCheck.failed("group trial evidence missing");
        }
        GroupBuyTrialResult result = groupTrialExecution.getResult();
        if (!product.getGoodsId().equals(result.getGoodsId())) {
            return GuideToolEvidenceCheck.failed("group trial goods mismatch");
        }
        if (GroupBuyActivityStatus.ACTIVE.equals(result.getStatus())
                && (!StringUtils.hasText(result.getActivityId()) || result.getGroupPrice() == null)) {
            return GuideToolEvidenceCheck.failed("group trial activity evidence missing");
        }
        return GuideToolEvidenceCheck.ok();
    }
}
