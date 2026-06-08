package com.linrun.trigger.http.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.api.dto.WeixinLoginQrRequest;
import com.linrun.api.dto.WeixinLoginStatusResponse;
import com.linrun.api.dto.WeixinSimulateScanRequest;
import com.linrun.api.dto.WeixinTemplateMessageRequest;
import com.linrun.api.dto.WeixinTemplateMessageResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeixinPortalHandlerTest {

    @Test
    void shouldCreateAndSimulateLoginSessionWhenOfficialNotConfigured() {
        WeixinPortalHandler handler = newHandler();
        WeixinLoginQrRequest request = new WeixinLoginQrRequest();
        request.setUserId("U10001");

        String sceneId = handler.createLoginQr(request).getSceneId();

        WeixinSimulateScanRequest scanRequest = new WeixinSimulateScanRequest();
        scanRequest.setSceneId(sceneId);
        scanRequest.setUserId("U10001");
        scanRequest.setOpenId("mock_openid_U10001");
        scanRequest.setNickname("demo");
        WeixinLoginStatusResponse response = handler.simulateScan(scanRequest);

        assertEquals("SCANNED", response.getStatus());
        assertEquals("U10001", response.getUserId());
        assertEquals("mock_openid_U10001", response.getOpenId());
    }

    @Test
    void shouldRecordTemplateMessageWhenOfficialNotConfigured() {
        WeixinPortalHandler handler = newHandler();
        WeixinTemplateMessageRequest request = new WeixinTemplateMessageRequest();
        request.setOpenId("mock_openid_U10001");
        request.setTemplateId("demo_template_id");
        request.setTitle("拼团状态更新");
        request.setRemark("订单状态已更新");

        WeixinTemplateMessageResponse response = handler.sendTemplateMessage(request);

        assertTrue(response.isSuccess());
        assertEquals("MOCK", response.getMode());
        assertFalse(response.getPayload().isBlank());
    }

    private WeixinPortalHandler newHandler() {
        WeixinOfficialAccountClient client = new WeixinOfficialAccountClient(
                new ObjectMapper(), "", "", "agent_group_dev_token", 900);
        return new WeixinPortalHandler(client, "http://localhost:8080");
    }
}
