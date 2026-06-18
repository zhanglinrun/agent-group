package com.linrun.infrastructure.trade.gateway;

import com.linrun.domain.trade.model.payment.PaymentCreateCommand;
import com.linrun.domain.trade.model.payment.PaymentCreateResult;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OfficialPaymentGatewayClient 熔断降级测试")
class OfficialPaymentGatewayClientResilienceTest {

    /** 复用现有测试的占位构造方式（MOCK_PAY 通道不依赖真实支付宝/微信配置）。 */
    private OfficialPaymentGatewayClient client() {
        return new OfficialPaymentGatewayClient(
                "https://openapi-sandbox.dl.alipaydev.com/gateway.do",
                "", "", "", "UTF-8", "RSA2", "", "",
                "", "", "", "", "");
    }

    private PaymentCreateCommand mockPayCommand() {
        PaymentCreateCommand command = new PaymentCreateCommand();
        command.setOrderId("order-1");
        command.setPayOrderId("pay-1");
        command.setPayChannel("MOCK_PAY");
        command.setSubject("test order");
        command.setPayAmount(new BigDecimal("1.00"));
        return command;
    }

    @Test
    @DisplayName("熔断器/重试器未注入时创建支付正常返回，保持兼容")
    void shouldCreateMockPaymentWhenNoResilience() {
        OfficialPaymentGatewayClient client = client();

        PaymentCreateResult result = client.createPayment(mockPayCommand());

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("熔断器强制 OPEN 时创建支付被快速拒绝，不再调用底层网关逻辑")
    void shouldRejectCreatePaymentWhenCircuitForcedOpen() {
        OfficialPaymentGatewayClient client = client();
        CircuitBreaker breaker = CircuitBreaker.of("test-payment", CircuitBreakerConfig.custom().build());
        breaker.transitionToForcedOpenState();
        ReflectionTestUtils.setField(client, "circuitBreaker", breaker);

        assertThatThrownBy(() -> client.createPayment(mockPayCommand()))
            .isInstanceOf(CallNotPermittedException.class);
    }
}
