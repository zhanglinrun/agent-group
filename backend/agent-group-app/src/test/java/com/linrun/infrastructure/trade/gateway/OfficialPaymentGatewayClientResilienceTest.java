package com.linrun.infrastructure.trade.gateway;

import com.linrun.domain.trade.model.payment.PaymentCreateCommand;
import com.linrun.types.exception.AppException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OfficialPaymentGatewayClient 熔断降级测试")
class OfficialPaymentGatewayClientResilienceTest {

    /** 使用空配置，避免测试连接真实支付宝/微信。 */
    private OfficialPaymentGatewayClient client() {
        return new OfficialPaymentGatewayClient(
                "https://openapi-sandbox.dl.alipaydev.com/gateway.do",
                "", "", "", "UTF-8", "RSA2", "", "",
                "", "", "", "", "");
    }

    private PaymentCreateCommand unsupportedChannelCommand() {
        PaymentCreateCommand command = new PaymentCreateCommand();
        command.setOrderId("order-1");
        command.setPayOrderId("pay-1");
        command.setPayChannel("MOCK_PAY");
        command.setSubject("test order");
        command.setPayAmount(new BigDecimal("1.00"));
        return command;
    }

    @Test
    @DisplayName("不支持的支付渠道未接入熔断时也会被拒绝")
    void shouldRejectUnsupportedPaymentChannelWhenNoResilience() {
        OfficialPaymentGatewayClient client = client();

        assertThatThrownBy(() -> client.createPayment(unsupportedChannelCommand()))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("不支持的支付渠道");
    }

    @Test
    @DisplayName("熔断器强制 OPEN 时创建支付被快速拒绝，不再调用底层网关逻辑")
    void shouldRejectCreatePaymentWhenCircuitForcedOpen() {
        OfficialPaymentGatewayClient client = client();
        CircuitBreaker breaker = CircuitBreaker.of("test-payment", CircuitBreakerConfig.custom().build());
        breaker.transitionToForcedOpenState();
        ReflectionTestUtils.setField(client, "circuitBreaker", breaker);

        assertThatThrownBy(() -> client.createPayment(unsupportedChannelCommand()))
            .isInstanceOf(CallNotPermittedException.class);
    }
}
