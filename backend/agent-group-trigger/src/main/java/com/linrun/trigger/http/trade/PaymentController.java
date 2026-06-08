package com.linrun.trigger.http.trade;

import com.linrun.api.dto.CreatePaymentRequest;
import com.linrun.api.dto.DownloadPaymentBillRequest;
import com.linrun.api.dto.DownloadPaymentBillResponse;
import com.linrun.api.dto.PaymentGatewayErrorMapResponse;
import com.linrun.api.dto.PaymentGatewayStatusResponse;
import com.linrun.api.dto.PaymentWebhookRequest;
import com.linrun.api.dto.QueryPaymentRefundRequest;
import com.linrun.api.dto.QueryPaymentRefundResponse;
import com.linrun.api.dto.ReconcilePaymentRequest;
import com.linrun.api.dto.RefreshPaymentCertificateRequest;
import com.linrun.api.dto.RefreshPaymentCertificateResponse;
import com.linrun.api.dto.RefundPaymentRequest;
import com.linrun.api.dto.CreatePaymentResponse;
import com.linrun.api.dto.PaymentWebhookResponse;
import com.linrun.api.dto.ReconcilePaymentResponse;
import com.linrun.api.dto.RefundPaymentResponse;
import com.linrun.domain.account.model.UserAccount;
import com.linrun.domain.account.service.UserAccountService;
import com.linrun.trigger.config.RequestTraceContext;
import com.linrun.domain.trade.service.payment.PaymentService;
import com.linrun.domain.trade.service.TradeRefundService;
import com.linrun.types.common.Response;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final TradeRefundService tradeRefundService;
    private final UserAccountService userAccountService;

    public PaymentController(PaymentService paymentService,
                             TradeRefundService tradeRefundService,
                             UserAccountService userAccountService) {
        this.paymentService = paymentService;
        this.tradeRefundService = tradeRefundService;
        this.userAccountService = userAccountService;
    }

    @PostMapping("/create")
    public Response<CreatePaymentResponse> create(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody CreatePaymentRequest request) {
        UserAccount user = userAccountService.requireUserByToken(token);
        return Response.success(paymentService.createPayment(request, user.getUserId()), RequestTraceContext.getRequestId());
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

    @PostMapping(value = "/alipay/notify", consumes = MediaType.ALL_VALUE)
    public String alipayNotify(@RequestBody(required = false) String requestBody,
                               @RequestHeader Map<String, String> headers,
                               @RequestParam(required = false) Map<String, String> params) {
        PaymentWebhookRequest request = new PaymentWebhookRequest();
        request.setPayChannel("ALIPAY");
        request.setHeaders(headers);
        request.setRequestBody(StringUtils.hasText(requestBody) ? requestBody : formBody(params));
        try {
            paymentService.handleWebhook(request);
            return "success";
        } catch (RuntimeException ignored) {
            return "false";
        }
    }

    @PostMapping("/refund")
    public Response<RefundPaymentResponse> refund(@RequestBody RefundPaymentRequest request) {
        return Response.success(tradeRefundService.refund(request), RequestTraceContext.getRequestId());
    }

    @PostMapping("/reconcile")
    public Response<ReconcilePaymentResponse> reconcile(@RequestBody ReconcilePaymentRequest request) {
        return Response.success(paymentService.reconcile(request), RequestTraceContext.getRequestId());
    }

    @PostMapping("/bill/download")
    public Response<DownloadPaymentBillResponse> downloadBill(@RequestBody DownloadPaymentBillRequest request) {
        return Response.success(paymentService.downloadBill(request), RequestTraceContext.getRequestId());
    }

    @PostMapping("/refund/query")
    public Response<QueryPaymentRefundResponse> queryRefund(@RequestBody QueryPaymentRefundRequest request) {
        return Response.success(paymentService.queryRefund(request), RequestTraceContext.getRequestId());
    }

    @PostMapping(value = "/refund/webhook/{payChannel}", consumes = MediaType.ALL_VALUE)
    public Response<QueryPaymentRefundResponse> refundWebhook(@PathVariable String payChannel,
                                                              @RequestBody(required = false) String requestBody,
                                                              @RequestHeader Map<String, String> headers,
                                                              @RequestParam(required = false) Map<String, String> params) {
        PaymentWebhookRequest request = new PaymentWebhookRequest();
        request.setPayChannel(payChannel);
        request.setHeaders(headers);
        request.setRequestBody(StringUtils.hasText(requestBody) ? requestBody : formBody(params));
        return Response.success(paymentService.handleRefundWebhook(request), RequestTraceContext.getRequestId());
    }

    @PostMapping("/certificate/refresh")
    public Response<RefreshPaymentCertificateResponse> refreshCertificate(@RequestBody RefreshPaymentCertificateRequest request) {
        return Response.success(paymentService.refreshCertificate(request), RequestTraceContext.getRequestId());
    }

    @GetMapping("/error-map")
    public Response<PaymentGatewayErrorMapResponse> errorMap(@RequestParam String payChannel,
                                                             @RequestParam String gatewayCode) {
        return Response.success(paymentService.mapGatewayError(payChannel, gatewayCode), RequestTraceContext.getRequestId());
    }

    @GetMapping("/gateway/status")
    public Response<PaymentGatewayStatusResponse> gatewayStatus() {
        return Response.success(paymentService.gatewayStatus(), RequestTraceContext.getRequestId());
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
