package com.linrun.domain.trade.service;

import com.linrun.api.dto.CreatePaymentRequest;
import com.linrun.api.dto.CreatePaymentResponse;
import com.linrun.domain.trade.model.entity.PayOrderEntity;
import com.linrun.domain.trade.model.entity.TradeOrderEntity;
import com.linrun.domain.trade.model.valobj.PayStatusEnumVO;
import com.linrun.domain.trade.service.payment.PaymentService;
import org.springframework.util.StringUtils;

/**
 * 交易下单服务抽象基类。
 *
 * 对齐 s-pay 的 AbstractOrderService：把直接购买、拼团锁单等下单流程中
 * 重复的"网关支付单创建"和"支付形态判定"逻辑收敛到此处，子类只关心各自的订单装配。
 *
 * 公共能力：
 * - {@link #createGatewayPayment} 在支付单处于待支付且尚未换取网关支付地址时统一发起网关下单；
 * - {@link #resolvePayFormHtml} / {@link #resolvePaymentType} / {@link #looksLikePaymentForm}
 *   统一判断支付地址是表单 HTML 还是跳转 URL，供响应装配复用。
 */
public abstract class AbstractTradeOrderService {

    protected final PaymentService paymentService;

    protected AbstractTradeOrderService(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * 在支付单处于待支付且尚未换取网关支付地址时，统一发起网关下单。
     *
     * @param tradeOrder 交易订单
     * @param payOrder   支付单
     * @param payChannel 支付渠道
     * @return 网关支付响应；无需创建时返回 null
     */
    protected CreatePaymentResponse createGatewayPayment(TradeOrderEntity tradeOrder,
                                                         PayOrderEntity payOrder,
                                                         String payChannel) {
        if (paymentService == null || payOrder == null || !PayStatusEnumVO.WAIT_PAY.equals(payOrder.getPayStatus())) {
            return null;
        }
        if (StringUtils.hasText(payOrder.getPayUrl())) {
            return null;
        }
        CreatePaymentRequest paymentRequest = new CreatePaymentRequest();
        paymentRequest.setOrderId(tradeOrder.getOrderId());
        paymentRequest.setPayChannel(payChannel);
        return paymentService.createPayment(paymentRequest, tradeOrder.getUserId());
    }

    protected String resolvePayFormHtml(String payUrl) {
        return looksLikePaymentForm(payUrl) ? payUrl : null;
    }

    protected String resolvePaymentType(String payUrl) {
        return looksLikePaymentForm(payUrl) ? "PAGE_FORM" : "URL";
    }

    protected boolean looksLikePaymentForm(String value) {
        return StringUtils.hasText(value) && value.toLowerCase().contains("<form");
    }
}
