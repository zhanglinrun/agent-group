package com.linrun.domain.trade.adapter.port;

import com.linrun.domain.trade.model.payment.PaymentCreateCommand;
import com.linrun.domain.trade.model.payment.PaymentCreateResult;
import com.linrun.domain.trade.model.payment.PaymentBillDownloadCommand;
import com.linrun.domain.trade.model.payment.PaymentBillDownloadResult;
import com.linrun.domain.trade.model.payment.PaymentCertificateRefreshCommand;
import com.linrun.domain.trade.model.payment.PaymentCertificateRefreshResult;
import com.linrun.domain.trade.model.payment.PaymentGatewayErrorMapping;
import com.linrun.domain.trade.model.payment.PaymentReconcileCommand;
import com.linrun.domain.trade.model.payment.PaymentReconcileResult;
import com.linrun.domain.trade.model.payment.PaymentRefundCommand;
import com.linrun.domain.trade.model.payment.PaymentRefundQueryCommand;
import com.linrun.domain.trade.model.payment.PaymentRefundQueryResult;
import com.linrun.domain.trade.model.payment.PaymentRefundResult;
import com.linrun.domain.trade.model.payment.PaymentWebhookCommand;
import com.linrun.domain.trade.model.payment.PaymentWebhookResult;

public interface PaymentGatewayClient {

    PaymentCreateResult createPayment(PaymentCreateCommand command);

    PaymentWebhookResult verifyWebhook(PaymentWebhookCommand command);

    PaymentRefundResult refund(PaymentRefundCommand command);

    PaymentReconcileResult reconcile(PaymentReconcileCommand command);

    default PaymentBillDownloadResult downloadBill(PaymentBillDownloadCommand command) {
        throw new UnsupportedOperationException("payment bill download is not implemented");
    }

    default PaymentRefundQueryResult queryRefund(PaymentRefundQueryCommand command) {
        throw new UnsupportedOperationException("payment refund query is not implemented");
    }

    default PaymentRefundQueryResult verifyRefundWebhook(PaymentRefundQueryCommand command) {
        throw new UnsupportedOperationException("payment refund webhook is not implemented");
    }

    default PaymentCertificateRefreshResult refreshCertificate(PaymentCertificateRefreshCommand command) {
        throw new UnsupportedOperationException("payment certificate refresh is not implemented");
    }

    default PaymentGatewayErrorMapping mapGatewayError(String payChannel, String gatewayCode) {
        throw new UnsupportedOperationException("payment gateway error mapping is not implemented");
    }

    default PaymentWebhookResult queryPayment(PaymentReconcileCommand command) {
        return null;
    }
}
