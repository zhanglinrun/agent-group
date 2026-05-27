package com.linrun.trigger.http;







import com.linrun.trigger.support.tool.ToolExecution;
import com.linrun.trigger.support.tool.ToolExecutor;
import com.linrun.domain.trade.service.*;
import com.linrun.domain.trade.service.payment.*;
import com.linrun.domain.trade.service.task.NotifyTaskService;
import com.linrun.domain.support.metrics.AgentObservabilityMetrics;
import com.linrun.api.dto.ApproveHumanApprovalRequest;
import com.linrun.api.dto.CreateHumanApprovalRequest;
import com.linrun.api.dto.HumanApprovalResponse;
import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HumanApprovalHandlerTest {

    @Test
    void shouldApproveAndConsumeHumanApproval() {
        HumanApprovalHandler service = new HumanApprovalHandler();
        CreateHumanApprovalRequest createRequest = new CreateHumanApprovalRequest();
        createRequest.setUserId("U10001");
        createRequest.setAction(HumanApprovalHandler.ACTION_REFUND_MARKET_PAY_ORDER);
        createRequest.setBizId("O10001");
        createRequest.setSummary("refund order");

        HumanApprovalResponse created = service.createApproval(createRequest);
        ApproveHumanApprovalRequest approveRequest = new ApproveHumanApprovalRequest();
        approveRequest.setApprovalId(created.getApprovalId());
        approveRequest.setUserId("U10001");
        approveRequest.setApproved(true);
        service.approve(approveRequest);

        service.assertApproved(created.getApprovalId(), "U10001",
                HumanApprovalHandler.ACTION_REFUND_MARKET_PAY_ORDER, "O10001");

        assertEquals(HumanApprovalRecord.STATUS_CONSUMED,
                service.queryApproval(created.getApprovalId()).getStatus());
    }

    @Test
    void shouldRejectActionMismatch() {
        HumanApprovalHandler service = new HumanApprovalHandler();
        CreateHumanApprovalRequest createRequest = new CreateHumanApprovalRequest();
        createRequest.setUserId("U10001");
        createRequest.setAction(HumanApprovalHandler.ACTION_LOCK_MARKET_PAY_ORDER);
        createRequest.setBizId("OUT10001");
        HumanApprovalResponse created = service.createApproval(createRequest);
        ApproveHumanApprovalRequest approveRequest = new ApproveHumanApprovalRequest();
        approveRequest.setApprovalId(created.getApprovalId());
        approveRequest.setApproved(true);
        service.approve(approveRequest);

        AppException exception = assertThrows(AppException.class, () -> service.assertApproved(
                created.getApprovalId(), "U10001",
                HumanApprovalHandler.ACTION_REFUND_MARKET_PAY_ORDER, "OUT10001"));

        assertEquals("HITL_0006", exception.getCode());
    }
}
