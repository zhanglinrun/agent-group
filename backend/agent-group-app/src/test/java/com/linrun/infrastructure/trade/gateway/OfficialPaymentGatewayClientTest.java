package com.linrun.infrastructure.trade.gateway;

import com.linrun.api.dto.PaymentGatewayStatusResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfficialPaymentGatewayClientTest {

    @Test
    void shouldExposeMissingAlipaySandboxItemsWhenKeysAreAbsent() {
        OfficialPaymentGatewayClient client = client("", "", "http://localhost:8080/api/v1/payment/alipay/notify");

        PaymentGatewayStatusResponse status = client.gatewayStatus();

        assertFalse(status.isMockReady());
        assertFalse(status.isOfficialSandboxReady());
        assertFalse(status.isAlipaySandboxReady());
        assertEquals("ALIPAY", status.getRecommendedChannel());
        assertTrue(status.getOfficialSandboxMissingItems().contains("AGENT_GROUP_ALIPAY_APP_ID"));
        assertTrue(status.getOfficialSandboxMissingItems().contains("AGENT_GROUP_ALIPAY_PRIVATE_KEY"));
        assertTrue(status.getOfficialSandboxMissingItems().contains("PUBLIC_AGENT_GROUP_ALIPAY_NOTIFY_URL"));

        PaymentGatewayStatusResponse.ChannelStatus alipay = alipay(status);
        assertTrue(alipay.isSandboxMode());
        assertFalse(alipay.isConfigured());
        assertEquals("http://localhost:8080/api/v1/payment/alipay/notify", alipay.getNotifyUrl());
        assertTrue(alipay.getMissingItems().contains("publicNotifyUrl"));
        assertTrue(alipay.getLastError().contains("publicNotifyUrl"));
    }

    @Test
    void shouldMarkAlipaySandboxReadyWhenKeysAndPublicCallbackAreConfigured() {
        OfficialPaymentGatewayClient client = client("app-1001", "private-key", "https://pay.example.com/alipay/notify");

        PaymentGatewayStatusResponse status = client.gatewayStatus();

        assertTrue(status.isOfficialSandboxReady());
        assertTrue(status.isAlipaySandboxReady());
        assertEquals("ALIPAY", status.getRecommendedChannel());
        assertTrue(status.getOfficialSandboxMissingItems().isEmpty());
        assertEquals("alipay sandbox config and public callback are ready", status.getSandboxEvidence());

        PaymentGatewayStatusResponse.ChannelStatus alipay = alipay(status);
        assertTrue(alipay.isConfigured());
        assertEquals(alipay.getRequiredItemCount(), alipay.getReadyItemCount());
        assertTrue(alipay.getMissingItems().isEmpty());
        assertEquals("", alipay.getLastError());
    }

    private OfficialPaymentGatewayClient client(String appId, String privateKey, String notifyUrl) {
        return new OfficialPaymentGatewayClient(
                "https://openapi-sandbox.dl.alipaydev.com/gateway.do",
                appId,
                privateKey,
                privateKey.isEmpty() ? "" : "public-key",
                "UTF-8",
                "RSA2",
                notifyUrl,
                "",
                "",
                "",
                "",
                "",
                "");
    }

    private PaymentGatewayStatusResponse.ChannelStatus alipay(PaymentGatewayStatusResponse status) {
        return status.getChannels().stream()
                .filter(channel -> "ALIPAY".equals(channel.getPayChannel()))
                .findFirst()
                .orElseThrow();
    }
}















