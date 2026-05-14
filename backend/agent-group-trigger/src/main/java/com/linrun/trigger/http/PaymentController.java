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
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

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

    @PostMapping(value = "/webhook/{payChannel}", consumes = MediaType.ALL_VALUE)
    public Response<PaymentWebhookResponse> gatewayWebhook(@PathVariable String payChannel,
                                                           @RequestBody(required = false) String requestBody,
                                                           @RequestHeader Map<String, String> headers,
                                                           @RequestParam(required = false) Map<String, String> params) {
        PaymentWebhookRequest request = new PaymentWebhookRequest();
        request.setPayChannel(payChannel);
        request.setHeaders(headers);
        request.setRequestBody(StringUtils.hasText(requestBody) ? requestBody : formBody(params));
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

    private String formBody(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        return params.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
