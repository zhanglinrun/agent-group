package com.linrun.domain.trade.adapter.port;

import com.linrun.domain.trade.model.payment.PaymentCreateCommand;
import com.linrun.domain.trade.model.payment.PaymentCreateResult;
import com.linrun.domain.trade.model.payment.PaymentReconcileCommand;
import com.linrun.domain.trade.model.payment.PaymentReconcileResult;
import com.linrun.domain.trade.model.payment.PaymentRefundCommand;
import com.linrun.domain.trade.model.payment.PaymentRefundResult;
import com.linrun.domain.trade.model.payment.PaymentWebhookCommand;
import com.linrun.domain.trade.model.payment.PaymentWebhookResult;

public interface PaymentGatewayClient {

    PaymentCreateResult createPayment(PaymentCreateCommand command);

    PaymentWebhookResult verifyWebhook(PaymentWebhookCommand command);

    PaymentRefundResult refund(PaymentRefundCommand command);

    PaymentReconcileResult reconcile(PaymentReconcileCommand command);
}
