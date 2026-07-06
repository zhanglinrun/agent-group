package com.linrun.trigger.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.api.dto.NotifyTaskExecuteResponse;
import com.linrun.domain.support.config.service.DynamicConfigService;
import com.linrun.domain.trade.service.TradeCompensationService;
import com.linrun.domain.trade.service.task.NotifyTaskService;
import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradeCompensationXxlJobHandlerTest {

    @Test
    void shouldExecuteNotifyTaskByUuid() {
        NotifyTaskService notifyTaskService = mock(NotifyTaskService.class);
        TradeCompensationService tradeCompensationService = mock(TradeCompensationService.class);
        DynamicConfigService dynamicConfigService = mock(DynamicConfigService.class);
        ScheduledJobLockExecutor lockExecutor = executableLock();
        when(notifyTaskService.execNotifyTask("N10001")).thenReturn(notifyResponse());
        TradeCompensationXxlJobHandler handler = handler(
                notifyTaskService, tradeCompensationService, dynamicConfigService, lockExecutor);

        handler.handleGroupBuyNotifyTask("{\"uuid\":\"N10001\"}");

        verify(notifyTaskService).execNotifyTask("N10001");
        verify(notifyTaskService, never()).execNotifyJob();
    }

    @Test
    void shouldExecuteNotifyTaskByTeamId() {
        NotifyTaskService notifyTaskService = mock(NotifyTaskService.class);
        TradeCompensationService tradeCompensationService = mock(TradeCompensationService.class);
        DynamicConfigService dynamicConfigService = mock(DynamicConfigService.class);
        ScheduledJobLockExecutor lockExecutor = executableLock();
        when(notifyTaskService.execNotifyJob("T10001")).thenReturn(notifyResponse());
        TradeCompensationXxlJobHandler handler = handler(
                notifyTaskService, tradeCompensationService, dynamicConfigService, lockExecutor);

        handler.handleGroupBuyNotifyTask("teamId=T10001");

        verify(notifyTaskService).execNotifyJob("T10001");
        verify(notifyTaskService, never()).execNotifyTask(anyString());
    }

    @Test
    void shouldRejectNotifyTaskWhenUuidAndTeamIdBothSet() {
        NotifyTaskService notifyTaskService = mock(NotifyTaskService.class);
        TradeCompensationService tradeCompensationService = mock(TradeCompensationService.class);
        DynamicConfigService dynamicConfigService = mock(DynamicConfigService.class);
        ScheduledJobLockExecutor lockExecutor = executableLock();
        TradeCompensationXxlJobHandler handler = handler(
                notifyTaskService, tradeCompensationService, dynamicConfigService, lockExecutor);

        assertThrows(AppException.class,
                () -> handler.handleGroupBuyNotifyTask("uuid=N10001,teamId=T10001"));
    }

    @Test
    void shouldExecutePaymentCompensationByOrderId() {
        NotifyTaskService notifyTaskService = mock(NotifyTaskService.class);
        TradeCompensationService tradeCompensationService = mock(TradeCompensationService.class);
        DynamicConfigService dynamicConfigService = mock(DynamicConfigService.class);
        ScheduledJobLockExecutor lockExecutor = executableLock();
        when(tradeCompensationService.reconcilePayWaitOrder("O10001")).thenReturn(true);
        TradeCompensationXxlJobHandler handler = handler(
                notifyTaskService, tradeCompensationService, dynamicConfigService, lockExecutor);

        handler.handlePaymentQueryCompensation("{\"orderId\":\"O10001\"}");

        verify(tradeCompensationService).reconcilePayWaitOrder("O10001");
        verify(tradeCompensationService, never()).closeTimeoutUnpaidOrders(any(), anyInt());
    }

    @Test
    void shouldSkipWhenLockIsHeld() {
        NotifyTaskService notifyTaskService = mock(NotifyTaskService.class);
        TradeCompensationService tradeCompensationService = mock(TradeCompensationService.class);
        DynamicConfigService dynamicConfigService = mock(DynamicConfigService.class);
        ScheduledJobLockExecutor lockExecutor = mock(ScheduledJobLockExecutor.class);
        when(lockExecutor.execute(anyString(), any(Duration.class), any(Runnable.class))).thenReturn(false);
        TradeCompensationXxlJobHandler handler = handler(
                notifyTaskService, tradeCompensationService, dynamicConfigService, lockExecutor);

        handler.handleGroupBuyNotifyTask("{\"uuid\":\"N10001\"}");

        verify(notifyTaskService, never()).execNotifyTask(anyString());
    }

    private TradeCompensationXxlJobHandler handler(NotifyTaskService notifyTaskService,
                                                   TradeCompensationService tradeCompensationService,
                                                   DynamicConfigService dynamicConfigService,
                                                   ScheduledJobLockExecutor lockExecutor) {
        return new TradeCompensationXxlJobHandler(
                notifyTaskService,
                tradeCompensationService,
                dynamicConfigService,
                lockExecutor,
                new ObjectMapper());
    }

    private ScheduledJobLockExecutor executableLock() {
        ScheduledJobLockExecutor lockExecutor = mock(ScheduledJobLockExecutor.class);
        when(lockExecutor.execute(anyString(), any(Duration.class), any(Runnable.class))).thenAnswer(invocation -> {
            Runnable task = invocation.getArgument(2);
            task.run();
            return true;
        });
        return lockExecutor;
    }

    private NotifyTaskExecuteResponse notifyResponse() {
        NotifyTaskExecuteResponse response = new NotifyTaskExecuteResponse();
        response.setWaitCount(1);
        response.setSuccessCount(1);
        return response;
    }
}
