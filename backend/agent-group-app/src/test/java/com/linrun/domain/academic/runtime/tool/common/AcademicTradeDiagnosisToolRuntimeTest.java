package com.linrun.domain.academic.runtime.tool.common;

import com.linrun.api.dto.TradeConsistencyCheckRequest;
import com.linrun.api.dto.TradeConsistencyCheckResponse;
import com.linrun.domain.academic.runtime.tool.AcademicToolCallCommand;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolStructuredOutput;
import com.linrun.domain.trade.service.TradeConsistencyCheckService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class AcademicTradeDiagnosisToolRuntimeTest {

    @Test
    void diagnoseAggregatesOrderStateIntoReadOnlyConclusion() {
        TradeConsistencyCheckService service = mock(TradeConsistencyCheckService.class);
        when(service.check(any(TradeConsistencyCheckRequest.class))).thenReturn(
                response(TradeConsistencyCheckService.QUOTA_GRANT_REQUIRED, "待发放额度", true, false));

        AcademicTradeDiagnosisToolRuntime runtime = new AcademicTradeDiagnosisToolRuntime(service);
        AcademicToolStructuredOutput output = runtime.diagnose(command("trade_diagnosis", Map.of("orderId", "O1")));

        assertEquals("O1", output.getMetadata().get("orderId"));
        assertEquals(TradeConsistencyCheckService.QUOTA_GRANT_REQUIRED, output.getMetadata().get("conclusion"));
        assertEquals("待发放额度", output.getMetadata().get("settlementLabel"));
        assertEquals(Boolean.TRUE, output.getMetadata().get("quotaGrantAllowed"));
        assertEquals("额度到账流水：缺失", ((List<?>) output.getMetadata().get("facts")).get(0));
        // 只读红线：工具只调用一致性巡检查询，不触发任何写操作
        verify(service, times(1)).check(any(TradeConsistencyCheckRequest.class));
        verifyNoMoreInteractions(service);
    }

    @Test
    void diagnoseRequiresOrderId() {
        TradeConsistencyCheckService service = mock(TradeConsistencyCheckService.class);
        AcademicTradeDiagnosisToolRuntime runtime = new AcademicTradeDiagnosisToolRuntime(service);

        assertThrows(Exception.class, () -> runtime.diagnose(command("trade_diagnosis", Map.of())));
        verifyNoInteractions(service);
    }

    @Test
    void listReturnsLeanOrderSummaryWithoutDeepFacts() {
        TradeConsistencyCheckService service = mock(TradeConsistencyCheckService.class);
        when(service.check(any(TradeConsistencyCheckRequest.class))).thenReturn(
                response(TradeConsistencyCheckService.QUOTA_GRANTED_CONSISTENT, "额度已到账", true, false));

        AcademicTradeDiagnosisToolRuntime runtime = new AcademicTradeDiagnosisToolRuntime(service);
        AcademicToolStructuredOutput output = runtime.list(
                command("trade_order_list", Map.of("userId", "OTHER", "pageSize", 5)));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> orders = (List<Map<String, Object>>) output.getMetadata().get("orders");
        assertEquals(1, orders.size());
        assertEquals("O1", orders.get(0).get("orderId"));
        assertEquals(TradeConsistencyCheckService.QUOTA_GRANTED_CONSISTENT, orders.get(0).get("conclusion"));
        assertFalse(orders.get(0).containsKey("facts"), "列表视图不应泄露深度诊断事实");
        assertEquals(5, output.getMetadata().get("pageSize"));
        ArgumentCaptor<TradeConsistencyCheckRequest> requestCaptor =
                ArgumentCaptor.forClass(TradeConsistencyCheckRequest.class);
        verify(service).check(requestCaptor.capture());
        assertEquals("U1", requestCaptor.getValue().getUserId(), "订单列表必须绑定当前登录用户");
    }

    @Test
    void diagnoseRejectsOtherUsersOrder() {
        TradeConsistencyCheckService service = mock(TradeConsistencyCheckService.class);
        when(service.check(any(TradeConsistencyCheckRequest.class))).thenReturn(
                response(TradeConsistencyCheckService.QUOTA_GRANTED_CONSISTENT, "额度已到账", true, false, "U2"));

        AcademicTradeDiagnosisToolRuntime runtime = new AcademicTradeDiagnosisToolRuntime(service);

        assertThrows(Exception.class,
                () -> runtime.diagnose(command("trade_diagnosis", Map.of("orderId", "O1"))));
    }

    private static AcademicToolCallCommand command(String toolName, Map<String, Object> arguments) {
        return AcademicToolCallCommand.builder(toolName).userId("U1").arguments(arguments).build();
    }

    private static TradeConsistencyCheckResponse response(String conclusion, String label,
                                                          boolean quotaGrantAllowed, boolean refundRollbackRequired) {
        return response(conclusion, label, quotaGrantAllowed, refundRollbackRequired, "U1");
    }

    private static TradeConsistencyCheckResponse response(String conclusion, String label,
                                                          boolean quotaGrantAllowed,
                                                          boolean refundRollbackRequired,
                                                          String userId) {
        TradeConsistencyCheckResponse response = new TradeConsistencyCheckResponse();
        response.setCheckedCount(1);
        TradeConsistencyCheckResponse.Item item = new TradeConsistencyCheckResponse.Item();
        item.setOrderId("O1");
        item.setUserId(userId);
        item.setBuyType("DIRECT");
        item.setOrderStatus("PAY_SUCCESS");
        item.setPayStatus("SUCCESS");
        item.setConclusion(conclusion);
        item.setMessage("message");
        item.setSettlementLabel(label);
        item.setSettlementDetail("detail");
        item.setQuotaGrantAllowed(quotaGrantAllowed);
        item.setRefundRollbackRequired(refundRollbackRequired);
        item.getFacts().add("额度到账流水：缺失");
        response.getItems().add(item);
        return response;
    }
}
