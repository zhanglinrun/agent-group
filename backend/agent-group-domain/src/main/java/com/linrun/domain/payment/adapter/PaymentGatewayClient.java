package com.linrun.domain.payment.adapter;

import com.linrun.domain.payment.model.PaymentCreateCommand;
import com.linrun.domain.payment.model.PaymentCreateResult;
import com.linrun.domain.payment.model.PaymentReconcileCommand;
import com.linrun.domain.payment.model.PaymentReconcileResult;
import com.linrun.domain.payment.model.PaymentRefundCommand;
import com.linrun.domain.payment.model.PaymentRefundResult;
import com.linrun.domain.payment.model.PaymentWebhookCommand;
import com.linrun.domain.payment.model.PaymentWebhookResult;

public interface PaymentGatewayClient {

    PaymentCreateResult createPayment(PaymentCreateCommand command);

    PaymentWebhookResult verifyWebhook(PaymentWebhookCommand command);

    PaymentRefundResult refund(PaymentRefundCommand command);

    PaymentReconcileResult reconcile(PaymentReconcileCommand command);
}
