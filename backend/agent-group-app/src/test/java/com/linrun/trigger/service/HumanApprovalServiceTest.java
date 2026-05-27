package com.linrun.trigger.service;

import com.linrun.api.agent.request.ApproveHumanApprovalRequest;
import com.linrun.api.agent.request.CreateHumanApprovalRequest;
import com.linrun.api.agent.response.HumanApprovalResponse;
import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HumanApprovalServiceTest {

    @Test
    void shouldApproveAndConsumeHumanApproval() {
        HumanApprovalService service = new HumanApprovalService();
        CreateHumanApprovalRequest createRequest = new CreateHumanApprovalRequest();
        createRequest.setUserId("U10001");
        createRequest.setAction(HumanApprovalService.ACTION_REFUND_MARKET_PAY_ORDER);
        createRequest.setBizId("O10001");
        createRequest.setSummary("refund order");

        HumanApprovalResponse created = service.createApproval(createRequest);
        ApproveHumanApprovalRequest approveRequest = new ApproveHumanApprovalRequest();
        approveRequest.setApprovalId(created.getApprovalId());
        approveRequest.setUserId("U10001");
        approveRequest.setApproved(true);
        service.approve(approveRequest);

        service.assertApproved(created.getApprovalId(), "U10001",
                HumanApprovalService.ACTION_REFUND_MARKET_PAY_ORDER, "O10001");

        assertEquals(HumanApprovalRecord.STATUS_CONSUMED,
                service.queryApproval(created.getApprovalId()).getStatus());
    }

    @Test
    void shouldRejectActionMismatch() {
        HumanApprovalService service = new HumanApprovalService();
        CreateHumanApprovalRequest createRequest = new CreateHumanApprovalRequest();
        createRequest.setUserId("U10001");
        createRequest.setAction(HumanApprovalService.ACTION_LOCK_MARKET_PAY_ORDER);
        createRequest.setBizId("OUT10001");
        HumanApprovalResponse created = service.createApproval(createRequest);
        ApproveHumanApprovalRequest approveRequest = new ApproveHumanApprovalRequest();
        approveRequest.setApprovalId(created.getApprovalId());
        approveRequest.setApproved(true);
        service.approve(approveRequest);

        AppException exception = assertThrows(AppException.class, () -> service.assertApproved(
                created.getApprovalId(), "U10001",
                HumanApprovalService.ACTION_REFUND_MARKET_PAY_ORDER, "OUT10001"));

        assertEquals("HITL_0006", exception.getCode());
    }
}
