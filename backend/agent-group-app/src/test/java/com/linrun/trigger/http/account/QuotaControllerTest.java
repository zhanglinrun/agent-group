package com.linrun.trigger.http.account;

import com.linrun.domain.account.service.UserAccountService;
import com.linrun.domain.account.service.UserQuotaService;
import com.linrun.domain.agent.conversation.service.QuotaPackageCatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QuotaControllerTest {

    @Test
    void shouldGrantQuotaByBackendOrderStateForAdminRepair() throws Exception {
        UserQuotaService userQuotaService = mock(UserQuotaService.class);
        when(userQuotaService.grantQuotaForOrderIds(anyList()))
                .thenReturn(List.of("O-DEMO-DIRECT-001"));
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new QuotaController(
                        mock(UserAccountService.class),
                        userQuotaService,
                        mock(QuotaPackageCatalogService.class)))
                .build();

        mockMvc.perform(post("/api/v1/quota/admin/grant-by-orders")
                        .contentType("application/json")
                        .content("""
                                {
                                  "orderIds": ["O-DEMO-DIRECT-001", "O-GROUP-WAIT"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestedCount").value(2))
                .andExpect(jsonPath("$.data.processedCount").value(1))
                .andExpect(jsonPath("$.data.processedOrderIds[0]").value("O-DEMO-DIRECT-001"))
                .andExpect(jsonPath("$.data.message").value("已按后端订单状态执行额度补发，未满足到账条件的订单不会发放额度"));
    }
}















