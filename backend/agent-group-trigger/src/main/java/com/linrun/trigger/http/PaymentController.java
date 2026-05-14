package com.linrun.trigger.http;

import com.linrun.api.payment.request.CreatePaymentRequest;
import com.linrun.api.payment.request.PaymentWebhookRequest;
import com.linrun.api.payment.request.ReconcilePaymentRequest;
import com.linrun.api.payment.request.RefundPaymentRequest;
import com.linrun.api.payment.response.CreatePaymentResponse;
import com.linrun.api.payment.response.PaymentWebhookResponse;
import com.linrun.api.payment.response.ReconcilePaymentResponse;
import com.linrun.api.payment.response.RefundPaymentResponse;
import com.linrun.trigger.config.RequestTraceContext;
import com.linrun.trigger.service.PaymentService;
import com.linrun.types.response.Response;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/payment")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/create")
    public Response<CreatePaymentResponse> create(@RequestBody CreatePaymentRequest request) {
        return Response.success(paymentService.createPayment(request), RequestTraceContext.getRequestId());
    }

    @PostMapping("/webhook")
    public Response<PaymentWebhookResponse> webhook(@RequestBody PaymentWebhookRequest request) {
        return Response.success(paymentService.handleWebhook(request), RequestTraceContext.getRequestId());
    }

    @PostMapping("/refund")
    public Response<RefundPaymentResponse> refund(@RequestBody RefundPaymentRequest request) {
        return Response.success(paymentService.refund(request), RequestTraceContext.getRequestId());
    }

    @PostMapping("/reconcile")
    public Response<ReconcilePaymentResponse> reconcile(@RequestBody ReconcilePaymentRequest request) {
        return Response.success(paymentService.reconcile(request), RequestTraceContext.getRequestId());
    }
}
