package com.linrun.infrastructure.gateway;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayConfig;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayDataDataserviceBillDownloadurlQueryRequest;
import com.alipay.api.request.AlipayTradeFastpayRefundQueryRequest;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayDataDataserviceBillDownloadurlQueryResponse;
import com.alipay.api.response.AlipayTradeFastpayRefundQueryResponse;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.alipay.api.domain.AlipayTradePrecreateModel;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.domain.AlipayTradeRefundModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.domain.trade.adapter.port.PaymentGatewayClient;
import com.linrun.domain.trade.model.payment.PaymentBillDownloadCommand;
import com.linrun.domain.trade.model.payment.PaymentBillDownloadResult;
import com.linrun.domain.trade.model.payment.PaymentCertificateRefreshCommand;
import com.linrun.domain.trade.model.payment.PaymentCertificateRefreshResult;
import com.linrun.domain.trade.model.payment.PaymentChannel;
import com.linrun.domain.trade.model.payment.PaymentCreateCommand;
import com.linrun.domain.trade.model.payment.PaymentCreateResult;
import com.linrun.domain.trade.model.payment.PaymentGatewayErrorMapping;
import com.linrun.domain.trade.model.payment.PaymentReconcileCommand;
import com.linrun.domain.trade.model.payment.PaymentReconcileResult;
import com.linrun.domain.trade.model.payment.PaymentRefundCommand;
import com.linrun.domain.trade.model.payment.PaymentRefundQueryCommand;
import com.linrun.domain.trade.model.payment.PaymentRefundQueryResult;
import com.linrun.domain.trade.model.payment.PaymentRefundResult;
import com.linrun.domain.trade.model.payment.PaymentWebhookCommand;
import com.linrun.domain.trade.model.payment.PaymentWebhookResult;
import com.linrun.types.exception.AppException;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.payments.model.TransactionAmount;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.payments.nativepay.model.Amount;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayResponse;
import com.wechat.pay.java.service.payments.nativepay.model.QueryOrderByOutTradeNoRequest;
import com.wechat.pay.java.service.refund.RefundService;
import com.wechat.pay.java.service.refund.model.AmountReq;
import com.wechat.pay.java.service.refund.model.CreateRequest;
import com.wechat.pay.java.service.refund.model.Refund;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
public class OfficialPaymentGatewayClient implements PaymentGatewayClient {

    private static final DateTimeFormatter NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter ALIPAY_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter BILL_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String alipayGatewayUrl;
    private final String alipayAppId;
    private final String alipayPrivateKey;
    private final String alipayPublicKey;
    private final String alipayCharset;
    private final String alipaySignType;
    private final String wechatAppId;
    private final String wechatMerchantId;
    private final String wechatPrivateKeyPath;
    private final String wechatMerchantSerialNo;
    private final String wechatApiV3Key;

    public OfficialPaymentGatewayClient(
            @Value("${agent.group.payment.alipay.gateway-url:https://openapi.alipay.com/gateway.do}") String alipayGatewayUrl,
            @Value("${agent.group.payment.alipay.app-id:}") String alipayAppId,
            @Value("${agent.group.payment.alipay.private-key:}") String alipayPrivateKey,
            @Value("${agent.group.payment.alipay.public-key:}") String alipayPublicKey,
            @Value("${agent.group.payment.alipay.charset:UTF-8}") String alipayCharset,
            @Value("${agent.group.payment.alipay.sign-type:RSA2}") String alipaySignType,
            @Value("${agent.group.payment.wechat.app-id:}") String wechatAppId,
            @Value("${agent.group.payment.wechat.merchant-id:}") String wechatMerchantId,
            @Value("${agent.group.payment.wechat.private-key-path:}") String wechatPrivateKeyPath,
            @Value("${agent.group.payment.wechat.merchant-serial-no:}") String wechatMerchantSerialNo,
            @Value("${agent.group.payment.wechat.api-v3-key:}") String wechatApiV3Key) {
        this.alipayGatewayUrl = alipayGatewayUrl;
        this.alipayAppId = alipayAppId;
        this.alipayPrivateKey = alipayPrivateKey;
        this.alipayPublicKey = alipayPublicKey;
        this.alipayCharset = alipayCharset;
        this.alipaySignType = alipaySignType;
        this.wechatAppId = wechatAppId;
        this.wechatMerchantId = wechatMerchantId;
        this.wechatPrivateKeyPath = wechatPrivateKeyPath;
        this.wechatMerchantSerialNo = wechatMerchantSerialNo;
        this.wechatApiV3Key = wechatApiV3Key;
    }

    @Override
    public PaymentCreateResult createPayment(PaymentCreateCommand command) {
        PaymentChannel channel = PaymentChannel.parse(command.getPayChannel());
        return switch (channel) {
            case ALIPAY -> createAlipayPayment(command);
            case WECHAT_PAY -> createWechatPayment(command);
            case MOCK_PAY -> createMockPayment(command);
        };
    }

    @Override
    public PaymentWebhookResult verifyWebhook(PaymentWebhookCommand command) {
        PaymentChannel channel = PaymentChannel.parse(command.getPayChannel());
        return switch (channel) {
            case ALIPAY -> verifyAlipayWebhook(command);
            case WECHAT_PAY -> verifyWechatWebhook(command);
            case MOCK_PAY -> verifyMockWebhook(command);
        };
    }

    @Override
    public PaymentRefundResult refund(PaymentRefundCommand command) {
        PaymentChannel channel = PaymentChannel.parse(command.getPayChannel());
        return switch (channel) {
            case ALIPAY -> refundAlipay(command);
            case WECHAT_PAY -> refundWechat(command);
            case MOCK_PAY -> refundMock(command);
        };
    }

    @Override
    public PaymentReconcileResult reconcile(PaymentReconcileCommand command) {
        PaymentChannel channel = PaymentChannel.parse(command.getPayChannel());
        String message = switch (channel) {
            case ALIPAY -> alipayReady() ? "支付宝配置可用，当前按本地支付单做轻量对账" : "支付宝配置不完整，当前仅完成本地对账";
            case WECHAT_PAY -> wechatReady() ? "微信支付配置可用，当前按本地支付单做轻量对账" : "微信支付配置不完整，当前仅完成本地对账";
            case MOCK_PAY -> "模拟支付渠道按本地支付单完成对账";
        };
        return PaymentReconcileResult.matched(
                command.getOrderId(),
                command.getPayOrderId(),
                command.getGatewayTradeNo(),
                message);
    }

    @Override
    public PaymentWebhookResult queryPayment(PaymentReconcileCommand command) {
        PaymentChannel channel = PaymentChannel.parse(command.getPayChannel());
        return switch (channel) {
            case ALIPAY -> queryAlipayPayment(command);
            case WECHAT_PAY -> queryWechatPayment(command);
            case MOCK_PAY -> queryMockPayment(command);
        };
    }

    @Override
    public PaymentBillDownloadResult downloadBill(PaymentBillDownloadCommand command) {
        PaymentChannel channel = PaymentChannel.parse(command.payChannel());
        return switch (channel) {
            case ALIPAY -> downloadAlipayBill(command);
            case WECHAT_PAY -> downloadWechatBill(command);
            case MOCK_PAY -> downloadMockBill(command);
        };
    }

    @Override
    public PaymentRefundQueryResult queryRefund(PaymentRefundQueryCommand command) {
        PaymentChannel channel = PaymentChannel.parse(command.payChannel());
        return switch (channel) {
            case ALIPAY -> queryAlipayRefund(command);
            case WECHAT_PAY -> queryWechatRefund(command);
            case MOCK_PAY -> mockRefundQuery(command, true, "模拟退款查询完成");
        };
    }

    @Override
    public PaymentRefundQueryResult verifyRefundWebhook(PaymentRefundQueryCommand command) {
        PaymentChannel channel = PaymentChannel.parse(command.payChannel());
        return switch (channel) {
            case ALIPAY -> verifyAlipayRefundWebhook(command);
            case WECHAT_PAY -> verifyWechatRefundWebhook(command);
            case MOCK_PAY -> mockRefundQuery(command, true, "模拟退款回调验签通过");
        };
    }

    @Override
    public PaymentCertificateRefreshResult refreshCertificate(PaymentCertificateRefreshCommand command) {
        PaymentChannel channel = PaymentChannel.parse(command.payChannel());
        return switch (channel) {
            case ALIPAY -> new PaymentCertificateRefreshResult(
                    PaymentChannel.ALIPAY.name(),
                    alipayReady(),
                    "",
                    LocalDateTime.now(),
                    alipayReady() ? "支付宝公钥配置可用，请按平台证书轮换后更新环境变量" : "支付宝配置不完整，无法校验证书状态");
            case WECHAT_PAY -> refreshWechatCertificate();
            case MOCK_PAY -> new PaymentCertificateRefreshResult(
                    PaymentChannel.MOCK_PAY.name(),
                    true,
                    "MOCK_CERT",
                    LocalDateTime.now(),
                    "模拟支付无需证书轮换");
        };
    }

    @Override
    public PaymentGatewayErrorMapping mapGatewayError(String payChannel, String gatewayCode) {
        PaymentChannel channel = PaymentChannel.parse(payChannel);
        String normalized = gatewayCode == null ? "" : gatewayCode.trim().toUpperCase(Locale.ROOT);
        return switch (channel) {
            case ALIPAY -> mapAlipayError(normalized);
            case WECHAT_PAY -> mapWechatError(normalized);
            case MOCK_PAY -> new PaymentGatewayErrorMapping(
                    PaymentChannel.MOCK_PAY.name(),
                    normalized,
                    "MOCK_GATEWAY_ERROR",
                    "模拟支付渠道错误",
                    false,
                    "检查本地模拟支付参数");
        };
    }

    private PaymentCreateResult createMockPayment(PaymentCreateCommand command) {
        return PaymentCreateResult.created(
                command.getOrderId(),
                command.getPayOrderId(),
                PaymentChannel.MOCK_PAY.name(),
                "mock://pay/" + command.getPayOrderId(),
                "MOCK" + command.getPayOrderId(),
                "模拟支付单已创建");
    }

    private PaymentWebhookResult verifyMockWebhook(PaymentWebhookCommand command) {
        return PaymentWebhookResult.verified(
                command.getOrderId(),
                StringUtils.hasText(command.getPayOrderId()) ? command.getPayOrderId() : command.getGatewayTradeNo(),
                StringUtils.hasText(command.getGatewayTradeNo()) ? command.getGatewayTradeNo() : "MOCK" + command.getPayOrderId(),
                command.getPayTime() == null ? LocalDateTime.now() : command.getPayTime(),
                command.getPayAmount(),
                command.getTradeStatus(),
                "模拟支付回调验签通过");
    }

    private PaymentRefundResult refundMock(PaymentRefundCommand command) {
        return PaymentRefundResult.success(command.getOrderId(), command.getPayOrderId(), nextNo("R"), "模拟退款成功");
    }

    private PaymentWebhookResult queryMockPayment(PaymentReconcileCommand command) {
        return notPaid(command, "WAIT_BUYER_PAY", "mock payment query keeps local wait status");
    }

    private PaymentCreateResult createAlipayPayment(PaymentCreateCommand command) {
        ensureAlipayReady();
        try {
            AlipayTradePrecreateRequest request = new AlipayTradePrecreateRequest();
            AlipayTradePrecreateModel model = new AlipayTradePrecreateModel();
            model.setOutTradeNo(command.getPayOrderId());
            model.setSubject(command.getSubject());
            model.setTotalAmount(amountText(command.getPayAmount()));
            model.setPassbackParams(command.getOrderId());
            request.setBizModel(model);
            request.setNotifyUrl(command.getNotifyUrl());
            AlipayTradePrecreateResponse response = alipayClient().execute(request);
            if (!response.isSuccess()) {
                throw new AppException("PAY_0003", "支付宝创建支付失败：" + response.getSubMsg());
            }
            return PaymentCreateResult.created(
                    command.getOrderId(),
                    command.getPayOrderId(),
                    PaymentChannel.ALIPAY.name(),
                    response.getQrCode(),
                    command.getPayOrderId(),
                    "支付宝预下单成功");
        } catch (AlipayApiException e) {
            throw new AppException("PAY_0003", "支付宝创建支付失败：" + e.getMessage());
        }
    }

    private PaymentWebhookResult queryAlipayPayment(PaymentReconcileCommand command) {
        ensureAlipayReady();
        try {
            AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
            AlipayTradeQueryModel model = new AlipayTradeQueryModel();
            model.setOutTradeNo(command.getPayOrderId());
            if (StringUtils.hasText(command.getGatewayTradeNo())
                    && !command.getGatewayTradeNo().equals(command.getPayOrderId())) {
                model.setTradeNo(command.getGatewayTradeNo());
            }
            request.setBizModel(model);
            AlipayTradeQueryResponse response = alipayClient().execute(request);
            if (!response.isSuccess()) {
                return notPaid(command, "WAIT_BUYER_PAY", "alipay query not paid: " + response.getSubMsg());
            }
            String tradeStatus = response.getTradeStatus();
            if (!isAlipayPaid(tradeStatus)) {
                return notPaid(command, tradeStatus, "alipay query trade status: " + tradeStatus);
            }
            return PaymentWebhookResult.verified(
                    command.getOrderId(),
                    firstText(response.getOutTradeNo(), command.getPayOrderId()),
                    firstText(response.getTradeNo(), command.getGatewayTradeNo()),
                    parseAlipayTime(response.getSendPayDate()),
                    firstText(response.getTradeNo(), command.getGatewayTradeNo()),
                    LocalDateTime.now(),
                    parseAmount(response.getTotalAmount()),
                    tradeStatus,
                    "alipay query confirmed paid");
        } catch (AlipayApiException e) {
            throw new AppException("PAY_0007", "鏀粯瀹濇煡鍗曞紓甯革細" + e.getMessage());
        }
    }

    private PaymentWebhookResult verifyAlipayWebhook(PaymentWebhookCommand command) {
        ensureAlipayReady();
        Map<String, String> params = parseForm(command.getRequestBody());
        try {
            boolean verified = AlipaySignature.rsaCheckV1(params, alipayPublicKey, alipayCharset, alipaySignType);
            if (!verified) {
                throw new AppException("PAY_0002", "支付宝回调验签失败");
            }
            return PaymentWebhookResult.verified(
                    firstText(params.get("passback_params"), command.getOrderId()),
                    firstText(params.get("out_trade_no"), command.getPayOrderId()),
                    firstText(params.get("trade_no"), command.getGatewayTradeNo()),
                    command.getPayTime() == null ? LocalDateTime.now() : command.getPayTime(),
                    firstText(params.get("notify_id"), params.get("trade_no")),
                    parseAlipayTime(firstText(params.get("notify_time"), params.get("gmt_payment"))),
                    parseAmount(params.get("total_amount")),
                    firstText(params.get("trade_status"), command.getTradeStatus()),
                    "支付宝回调验签通过");
        } catch (AlipayApiException e) {
            throw new AppException("PAY_0002", "支付宝回调验签异常：" + e.getMessage());
        }
    }

    private PaymentRefundResult refundAlipay(PaymentRefundCommand command) {
        ensureAlipayReady();
        String refundId = nextNo("R");
        try {
            AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
            AlipayTradeRefundModel model = new AlipayTradeRefundModel();
            model.setOutTradeNo(command.getPayOrderId());
            model.setTradeNo(command.getGatewayTradeNo());
            model.setRefundAmount(amountText(command.getRefundAmount()));
            model.setRefundReason(command.getRefundReason());
            model.setOutRequestNo(refundId);
            request.setBizModel(model);
            AlipayTradeRefundResponse response = alipayClient().execute(request);
            if (!response.isSuccess()) {
                throw new AppException("PAY_0004", "支付宝退款失败：" + response.getSubMsg());
            }
            return PaymentRefundResult.success(command.getOrderId(), command.getPayOrderId(), refundId, "支付宝退款成功");
        } catch (AlipayApiException e) {
            throw new AppException("PAY_0004", "支付宝退款异常：" + e.getMessage());
        }
    }

    private PaymentCreateResult createWechatPayment(PaymentCreateCommand command) {
        ensureWechatReady();
        PrepayRequest request = new PrepayRequest();
        Amount amount = new Amount();
        amount.setTotal(centsInt(command.getPayAmount()));
        request.setAmount(amount);
        request.setAppid(wechatAppId);
        request.setMchid(wechatMerchantId);
        request.setDescription(command.getSubject());
        request.setNotifyUrl(command.getNotifyUrl());
        request.setOutTradeNo(command.getPayOrderId());
        request.setAttach(command.getOrderId());
        PrepayResponse response = new NativePayService.Builder().config(wechatConfig()).build().prepay(request);
        return PaymentCreateResult.created(
                command.getOrderId(),
                command.getPayOrderId(),
                PaymentChannel.WECHAT_PAY.name(),
                response.getCodeUrl(),
                command.getPayOrderId(),
                "微信支付预下单成功");
    }

    private PaymentWebhookResult queryWechatPayment(PaymentReconcileCommand command) {
        ensureWechatReady();
        QueryOrderByOutTradeNoRequest request = new QueryOrderByOutTradeNoRequest();
        request.setMchid(wechatMerchantId);
        request.setOutTradeNo(command.getPayOrderId());
        Transaction transaction = new NativePayService.Builder()
                .config(wechatConfig())
                .build()
                .queryOrderByOutTradeNo(request);
        String tradeStatus = transaction.getTradeState() == null ? "" : transaction.getTradeState().name();
        if (!"SUCCESS".equals(tradeStatus)) {
            return notPaid(command, tradeStatus, "wechat pay query trade status: " + tradeStatus);
        }
        return PaymentWebhookResult.verified(
                firstText(transaction.getAttach(), command.getOrderId()),
                firstText(transaction.getOutTradeNo(), command.getPayOrderId()),
                transaction.getTransactionId(),
                parseWechatTime(transaction.getSuccessTime()),
                transaction.getTransactionId(),
                LocalDateTime.now(),
                amountYuan(transaction.getAmount()),
                tradeStatus,
                "wechat pay query confirmed paid");
    }

    private PaymentWebhookResult verifyWechatWebhook(PaymentWebhookCommand command) {
        ensureWechatReady();
        Map<String, String> headers = command.getHeaders() == null ? Map.of() : command.getHeaders();
        RequestParam requestParam = new RequestParam.Builder()
                .serialNumber(header(headers, "Wechatpay-Serial"))
                .signature(header(headers, "Wechatpay-Signature"))
                .timestamp(header(headers, "Wechatpay-Timestamp"))
                .nonce(header(headers, "Wechatpay-Nonce"))
                .signType(header(headers, "Wechatpay-Signature-Type"))
                .body(command.getRequestBody())
                .build();
        Transaction transaction = new NotificationParser(wechatConfig()).parse(requestParam, Transaction.class);
        LocalDateTime webhookTime = parseWechatTimestamp(header(headers, "Wechatpay-Timestamp"));
        return PaymentWebhookResult.verified(
                firstText(transaction.getAttach(), command.getOrderId()),
                firstText(transaction.getOutTradeNo(), command.getPayOrderId()),
                transaction.getTransactionId(),
                parseWechatTime(transaction.getSuccessTime()),
                firstText(transaction.getTransactionId(), header(headers, "Wechatpay-Serial")),
                webhookTime,
                amountYuan(transaction.getAmount()),
                transaction.getTradeState() == null ? command.getTradeStatus() : transaction.getTradeState().name(),
                "微信支付回调验签通过");
    }

    private PaymentRefundResult refundWechat(PaymentRefundCommand command) {
        ensureWechatReady();
        String refundId = nextNo("R");
        CreateRequest request = new CreateRequest();
        request.setOutTradeNo(command.getPayOrderId());
        request.setOutRefundNo(refundId);
        request.setReason(command.getRefundReason());
        AmountReq amount = new AmountReq();
        Long cents = cents(command.getRefundAmount());
        amount.setRefund(cents);
        amount.setTotal(cents);
        amount.setCurrency("CNY");
        request.setAmount(amount);
        Refund refund = new RefundService.Builder().config(wechatConfig()).build().create(request);
        return PaymentRefundResult.success(
                command.getOrderId(),
                command.getPayOrderId(),
                firstText(refund.getRefundId(), refundId),
                "微信支付退款已受理");
    }

    private PaymentBillDownloadResult downloadAlipayBill(PaymentBillDownloadCommand command) {
        ensureAlipayReady();
        LocalDate billDate = command.billDate() == null ? LocalDate.now() : command.billDate();
        String billType = StringUtils.hasText(command.billType()) ? command.billType() : "trade";
        try {
            AlipayDataDataserviceBillDownloadurlQueryRequest request = new AlipayDataDataserviceBillDownloadurlQueryRequest();
            request.setBizContent("{\"bill_type\":\"" + jsonEscape(billType)
                    + "\",\"bill_date\":\"" + billDate.format(BILL_DATE_FORMATTER) + "\"}");
            AlipayDataDataserviceBillDownloadurlQueryResponse response = alipayClient().execute(request);
            if (!response.isSuccess()) {
                throw new AppException("PAY_0017", "支付宝账单下载地址查询失败：" + response.getSubMsg());
            }
            String downloadUrl = firstText(command.billFileUrl(), jsonText(response.getBody(), "bill_download_url"));
            return buildBillResult(PaymentChannel.ALIPAY.name(), billDate, billType, downloadUrl,
                    command.downloadContent(), "支付宝账单下载地址查询成功");
        } catch (AlipayApiException e) {
            throw new AppException("PAY_0017", "支付宝账单下载地址查询异常：" + e.getMessage());
        }
    }

    private PaymentBillDownloadResult downloadWechatBill(PaymentBillDownloadCommand command) {
        LocalDate billDate = command.billDate() == null ? LocalDate.now() : command.billDate();
        String billType = StringUtils.hasText(command.billType()) ? command.billType() : "trade";
        if (StringUtils.hasText(command.billFileUrl())) {
            return buildBillResult(PaymentChannel.WECHAT_PAY.name(), billDate, billType, command.billFileUrl(),
                    command.downloadContent(), "微信支付账单文件按传入地址下载解析");
        }
        if (!wechatReady()) {
            return new PaymentBillDownloadResult(PaymentChannel.WECHAT_PAY.name(), billDate, billType, "",
                    false, false, 0, BigDecimal.ZERO, "",
                    "微信支付配置不完整，无法请求真实账单");
        }
        return new PaymentBillDownloadResult(PaymentChannel.WECHAT_PAY.name(), billDate, billType, "",
                false, false, 0, BigDecimal.ZERO, "",
                "微信支付证书配置可用，请在沙箱接入账单文件地址后开启下载解析");
    }

    private PaymentBillDownloadResult downloadMockBill(PaymentBillDownloadCommand command) {
        LocalDate billDate = command.billDate() == null ? LocalDate.now() : command.billDate();
        BillParseSummary summary = parseBillContent("pay_order_id,amount\nMOCK_001,1.00\nMOCK_002,2.50\n");
        return new PaymentBillDownloadResult(PaymentChannel.MOCK_PAY.name(), billDate,
                StringUtils.hasText(command.billType()) ? command.billType() : "trade",
                "mock://payment-bill/" + billDate,
                true,
                true,
                summary.totalCount(),
                summary.totalAmount(),
                summary.summary(),
                "模拟账单下载解析完成");
    }

    private PaymentRefundQueryResult queryAlipayRefund(PaymentRefundQueryCommand command) {
        ensureAlipayReady();
        try {
            AlipayTradeFastpayRefundQueryRequest request = new AlipayTradeFastpayRefundQueryRequest();
            request.setBizContent("{\"out_trade_no\":\"" + jsonEscape(command.payOrderId())
                    + "\",\"trade_no\":\"" + jsonEscape(command.gatewayTradeNo())
                    + "\",\"out_request_no\":\"" + jsonEscape(firstText(command.refundId(), command.payOrderId())) + "\"}");
            AlipayTradeFastpayRefundQueryResponse response = alipayClient().execute(request);
            String rawBody = response.getBody();
            if (!response.isSuccess()) {
                return new PaymentRefundQueryResult(PaymentChannel.ALIPAY.name(), command.orderId(), command.payOrderId(),
                        command.gatewayTradeNo(), command.refundId(), "UNKNOWN", null, null, false,
                        rawBody, "支付宝退款查询未成功：" + response.getSubMsg());
            }
            return new PaymentRefundQueryResult(PaymentChannel.ALIPAY.name(), command.orderId(), command.payOrderId(),
                    command.gatewayTradeNo(), firstText(command.refundId(), jsonText(rawBody, "out_request_no")),
                    firstText(jsonText(rawBody, "refund_status"), "SUCCESS"),
                    parseOptionalAmount(jsonText(rawBody, "refund_amount")),
                    LocalDateTime.now(),
                    true,
                    rawBody,
                    "支付宝退款查询成功");
        } catch (AlipayApiException e) {
            throw new AppException("PAY_0018", "支付宝退款查询异常：" + e.getMessage());
        }
    }

    private PaymentRefundQueryResult queryWechatRefund(PaymentRefundQueryCommand command) {
        ensureWechatReady();
        try {
            Class<?> requestType = Class.forName("com.wechat.pay.java.service.refund.model.QueryByOutRefundNoRequest");
            Object request = requestType.getDeclaredConstructor().newInstance();
            invokeSetter(request, "setMchid", wechatMerchantId);
            invokeSetter(request, "setOutRefundNo", firstText(command.refundId(), command.payOrderId()));
            RefundService refundService = new RefundService.Builder().config(wechatConfig()).build();
            Object refund = refundService.getClass()
                    .getMethod("queryByOutRefundNo", requestType)
                    .invoke(refundService, request);
            return toWechatRefundQueryResult(command, refund, true, "微信支付退款查询成功");
        } catch (Exception e) {
            throw new AppException("PAY_0018", "微信支付退款查询异常：" + e.getMessage());
        }
    }

    private PaymentRefundQueryResult verifyAlipayRefundWebhook(PaymentRefundQueryCommand command) {
        ensureAlipayReady();
        Map<String, String> params = parseForm(command.requestBody());
        try {
            boolean verified = AlipaySignature.rsaCheckV1(params, alipayPublicKey, alipayCharset, alipaySignType);
            if (!verified) {
                throw new AppException("PAY_0019", "支付宝退款回调验签失败");
            }
            return new PaymentRefundQueryResult(PaymentChannel.ALIPAY.name(),
                    firstText(params.get("passback_params"), command.orderId()),
                    firstText(params.get("out_trade_no"), command.payOrderId()),
                    firstText(params.get("trade_no"), command.gatewayTradeNo()),
                    firstText(params.get("out_biz_no"), command.refundId()),
                    firstText(params.get("refund_status"), "SUCCESS"),
                    parseOptionalAmount(firstText(params.get("refund_fee"), params.get("refund_amount"))),
                    parseAlipayTime(firstText(params.get("gmt_refund"), params.get("notify_time"))),
                    true,
                    command.requestBody(),
                    "支付宝退款回调验签通过");
        } catch (AlipayApiException e) {
            throw new AppException("PAY_0019", "支付宝退款回调验签异常：" + e.getMessage());
        }
    }

    private PaymentRefundQueryResult verifyWechatRefundWebhook(PaymentRefundQueryCommand command) {
        ensureWechatReady();
        Map<String, String> headers = command.headers() == null ? Map.of() : command.headers();
        RequestParam requestParam = new RequestParam.Builder()
                .serialNumber(header(headers, "Wechatpay-Serial"))
                .signature(header(headers, "Wechatpay-Signature"))
                .timestamp(header(headers, "Wechatpay-Timestamp"))
                .nonce(header(headers, "Wechatpay-Nonce"))
                .signType(header(headers, "Wechatpay-Signature-Type"))
                .body(command.requestBody())
                .build();
        try {
            Class<?> notificationType = Class.forName("com.wechat.pay.java.service.refund.model.RefundNotification");
            Object notification = new NotificationParser(wechatConfig()).parse(requestParam, notificationType);
            return toWechatRefundQueryResult(command, notification, true, "微信支付退款回调验签通过");
        } catch (ClassNotFoundException e) {
            return parseWechatRefundWebhookBody(command, true, "微信支付退款回调验签通过，当前 SDK 未提供退款通知模型，按报文解析");
        }
    }

    private PaymentRefundQueryResult mockRefundQuery(PaymentRefundQueryCommand command, boolean verified, String message) {
        return new PaymentRefundQueryResult(PaymentChannel.MOCK_PAY.name(),
                command.orderId(),
                command.payOrderId(),
                command.gatewayTradeNo(),
                firstText(command.refundId(), "MOCK_REFUND_" + command.payOrderId()),
                "SUCCESS",
                BigDecimal.ZERO,
                LocalDateTime.now(),
                verified,
                command.requestBody(),
                message);
    }

    private PaymentCertificateRefreshResult refreshWechatCertificate() {
        if (!wechatReady()) {
            return new PaymentCertificateRefreshResult(PaymentChannel.WECHAT_PAY.name(), false, "",
                    LocalDateTime.now(), "微信支付配置不完整，无法刷新平台证书");
        }
        wechatConfig();
        return new PaymentCertificateRefreshResult(PaymentChannel.WECHAT_PAY.name(), true, wechatMerchantSerialNo,
                LocalDateTime.now(), "微信支付自动证书配置已重新加载");
    }

    private PaymentGatewayErrorMapping mapAlipayError(String code) {
        return switch (code) {
            case "ACQ.TRADE_HAS_SUCCESS" -> new PaymentGatewayErrorMapping(PaymentChannel.ALIPAY.name(), code,
                    "PAY_DUPLICATE_SUCCESS", "交易已支付成功", false, "按支付成功幂等处理");
            case "ACQ.SYSTEM_ERROR", "SYSTEM_ERROR" -> new PaymentGatewayErrorMapping(PaymentChannel.ALIPAY.name(), code,
                    "PAY_GATEWAY_SYSTEM_ERROR", "支付渠道系统异常", true, "按指数退避重试或进入补偿任务");
            case "ACQ.TRADE_NOT_EXIST" -> new PaymentGatewayErrorMapping(PaymentChannel.ALIPAY.name(), code,
                    "PAY_ORDER_NOT_FOUND", "渠道侧交易不存在", false, "检查本地支付单号和渠道交易号");
            case "ACQ.INVALID_PARAMETER", "ISV.INVALID_PARAMETER" -> new PaymentGatewayErrorMapping(PaymentChannel.ALIPAY.name(), code,
                    "PAY_ILLEGAL_PARAMETER", "支付请求参数非法", false, "修正请求参数后重新发起");
            default -> new PaymentGatewayErrorMapping(PaymentChannel.ALIPAY.name(), code,
                    "PAY_GATEWAY_UNKNOWN", "未配置的支付宝错误码", false, "保留原始错误码并人工确认");
        };
    }

    private PaymentGatewayErrorMapping mapWechatError(String code) {
        return switch (code) {
            case "ORDERPAID" -> new PaymentGatewayErrorMapping(PaymentChannel.WECHAT_PAY.name(), code,
                    "PAY_DUPLICATE_SUCCESS", "订单已支付", false, "按支付成功幂等处理");
            case "SYSTEMERROR", "BANKERROR" -> new PaymentGatewayErrorMapping(PaymentChannel.WECHAT_PAY.name(), code,
                    "PAY_GATEWAY_SYSTEM_ERROR", "微信支付渠道异常", true, "按指数退避重试或进入补偿任务");
            case "ORDERNOTEXIST", "REFUNDNOTEXIST" -> new PaymentGatewayErrorMapping(PaymentChannel.WECHAT_PAY.name(), code,
                    "PAY_ORDER_NOT_FOUND", "渠道侧订单或退款单不存在", false, "检查商户单号、退款单号和渠道配置");
            case "FREQUENCY_LIMITED" -> new PaymentGatewayErrorMapping(PaymentChannel.WECHAT_PAY.name(), code,
                    "PAY_GATEWAY_RATE_LIMITED", "微信支付频率限制", true, "降低查询频率并延迟重试");
            case "SIGN_ERROR", "SIGNATURE_ERROR" -> new PaymentGatewayErrorMapping(PaymentChannel.WECHAT_PAY.name(), code,
                    "PAY_SIGNATURE_ERROR", "签名或证书校验失败", false, "检查商户证书、平台证书和 APIv3 密钥");
            case "NO_AUTH" -> new PaymentGatewayErrorMapping(PaymentChannel.WECHAT_PAY.name(), code,
                    "PAY_NO_AUTH", "商户无接口权限", false, "检查商户号权限和产品开通状态");
            default -> new PaymentGatewayErrorMapping(PaymentChannel.WECHAT_PAY.name(), code,
                    "PAY_GATEWAY_UNKNOWN", "未配置的微信支付错误码", false, "保留原始错误码并人工确认");
        };
    }

    private PaymentWebhookResult notPaid(PaymentReconcileCommand command, String tradeStatus, String message) {
        PaymentWebhookResult result = new PaymentWebhookResult();
        result.setOrderId(command.getOrderId());
        result.setPayOrderId(command.getPayOrderId());
        result.setGatewayTradeNo(command.getGatewayTradeNo());
        result.setTradeStatus(tradeStatus);
        result.setVerified(false);
        result.setMessage(message);
        return result;
    }

    private PaymentBillDownloadResult buildBillResult(String payChannel,
                                                      LocalDate billDate,
                                                      String billType,
                                                      String downloadUrl,
                                                      boolean downloadContent,
                                                      String message) {
        if (!downloadContent || !StringUtils.hasText(downloadUrl)) {
            return new PaymentBillDownloadResult(payChannel, billDate, billType, downloadUrl,
                    false, false, 0, BigDecimal.ZERO, "", message);
        }
        String billContent = downloadText(downloadUrl);
        BillParseSummary summary = parseBillContent(billContent);
        return new PaymentBillDownloadResult(payChannel, billDate, billType, downloadUrl,
                true, true, summary.totalCount(), summary.totalAmount(), summary.summary(), message);
    }

    private String downloadText(String downloadUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(downloadUrl)).GET().build();
            return HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .body();
        } catch (Exception e) {
            throw new AppException("PAY_0017", "账单文件下载失败：" + e.getMessage());
        }
    }

    private BillParseSummary parseBillContent(String content) {
        if (!StringUtils.hasText(content)) {
            return new BillParseSummary(0, BigDecimal.ZERO, "账单文件为空");
        }
        int totalCount = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (String line : content.split("\\R")) {
            String row = line == null ? "" : line.trim();
            if (!StringUtils.hasText(row) || row.startsWith("#") || row.contains("业务明细列表")) {
                continue;
            }
            String[] cells = row.split(",|\\t");
            BigDecimal amount = null;
            for (int i = cells.length - 1; i >= 0; i--) {
                amount = parseOptionalAmount(cells[i]);
                if (amount != null) {
                    break;
                }
            }
            if (amount == null) {
                continue;
            }
            totalCount++;
            totalAmount = totalAmount.add(amount);
        }
        return new BillParseSummary(totalCount, totalAmount.setScale(2, RoundingMode.HALF_UP),
                "解析账单明细 " + totalCount + " 行，推算交易金额 " + totalAmount.setScale(2, RoundingMode.HALF_UP));
    }

    private PaymentRefundQueryResult toWechatRefundQueryResult(PaymentRefundQueryCommand command,
                                                               Object refund,
                                                               boolean verified,
                                                               String message) {
        String rawBody = toJson(refund);
        return new PaymentRefundQueryResult(PaymentChannel.WECHAT_PAY.name(),
                firstText(readText(refund, "getOutTradeNo"), command.orderId()),
                command.payOrderId(),
                firstText(readText(refund, "getTransactionId"), command.gatewayTradeNo()),
                firstText(readText(refund, "getOutRefundNo"), command.refundId()),
                firstText(readEnumText(refund, "getStatus"), readEnumText(refund, "getRefundStatus")),
                parseWechatRefundAmount(refund),
                LocalDateTime.now(),
                verified,
                rawBody,
                message);
    }

    private PaymentRefundQueryResult parseWechatRefundWebhookBody(PaymentRefundQueryCommand command,
                                                                  boolean verified,
                                                                  String message) {
        String body = command.requestBody();
        return new PaymentRefundQueryResult(PaymentChannel.WECHAT_PAY.name(),
                firstText(jsonText(body, "out_trade_no"), command.orderId()),
                command.payOrderId(),
                firstText(jsonText(body, "transaction_id"), command.gatewayTradeNo()),
                firstText(jsonText(body, "out_refund_no"), command.refundId()),
                firstText(jsonText(body, "refund_status"), jsonText(body, "status")),
                parseOptionalAmount(jsonText(body, "refund_amount")),
                LocalDateTime.now(),
                verified,
                body,
                message);
    }

    private BigDecimal parseWechatRefundAmount(Object refund) {
        Object amount = readObject(refund, "getAmount");
        Object refundAmount = amount == null ? null : readObject(amount, "getRefund");
        if (refundAmount instanceof Number number) {
            return BigDecimal.valueOf(number.longValue(), 2).setScale(2, RoundingMode.HALF_UP);
        }
        return null;
    }

    private void invokeSetter(Object target, String methodName, String value) throws ReflectiveOperationException {
        target.getClass().getMethod(methodName, String.class).invoke(target, value);
    }

    private Object readObject(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String readText(Object target, String methodName) {
        Object value = readObject(target, methodName);
        return value == null ? "" : String.valueOf(value);
    }

    private String readEnumText(Object target, String methodName) {
        Object value = readObject(target, methodName);
        return value == null ? "" : String.valueOf(value);
    }

    private String toJson(Object value) {
        if (value == null) {
            return "";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String jsonText(String json, String fieldName) {
        if (!StringUtils.hasText(json) || !StringUtils.hasText(fieldName)) {
            return "";
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(json).findValue(fieldName);
            return node == null || node.isNull() ? "" : node.asText();
        } catch (Exception ignored) {
            return "";
        }
    }

    private String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private boolean isAlipayPaid(String tradeStatus) {
        if (!StringUtils.hasText(tradeStatus)) {
            return false;
        }
        String normalized = tradeStatus.trim().toUpperCase(Locale.ROOT);
        return "TRADE_SUCCESS".equals(normalized) || "TRADE_FINISHED".equals(normalized);
    }

    private AlipayClient alipayClient() throws AlipayApiException {
        AlipayConfig config = new AlipayConfig();
        config.setServerUrl(alipayGatewayUrl);
        config.setAppId(alipayAppId);
        config.setPrivateKey(alipayPrivateKey);
        config.setFormat("json");
        config.setCharset(alipayCharset);
        config.setAlipayPublicKey(alipayPublicKey);
        config.setSignType(alipaySignType);
        return new DefaultAlipayClient(config);
    }

    private RSAAutoCertificateConfig wechatConfig() {
        return new RSAAutoCertificateConfig.Builder()
                .merchantId(wechatMerchantId)
                .privateKeyFromPath(wechatPrivateKeyPath)
                .merchantSerialNumber(wechatMerchantSerialNo)
                .apiV3Key(wechatApiV3Key)
                .build();
    }

    private void ensureAlipayReady() {
        if (!alipayReady()) {
            throw new AppException("PAY_0005", "支付宝配置不完整，请通过环境变量配置应用和密钥");
        }
    }

    private void ensureWechatReady() {
        if (!wechatReady()) {
            throw new AppException("PAY_0006", "微信支付配置不完整，请通过环境变量配置商户号、证书和密钥");
        }
    }

    private boolean alipayReady() {
        return StringUtils.hasText(alipayGatewayUrl)
                && StringUtils.hasText(alipayAppId)
                && StringUtils.hasText(alipayPrivateKey)
                && StringUtils.hasText(alipayPublicKey);
    }

    private boolean wechatReady() {
        return StringUtils.hasText(wechatAppId)
                && StringUtils.hasText(wechatMerchantId)
                && StringUtils.hasText(wechatPrivateKeyPath)
                && StringUtils.hasText(wechatMerchantSerialNo)
                && StringUtils.hasText(wechatApiV3Key);
    }

    private String amountText(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private Long cents(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private Integer centsInt(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    private BigDecimal parseAmount(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            throw new AppException("PAY_0002", "支付回调金额格式不正确");
        }
    }

    private BigDecimal parseOptionalAmount(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim()
                .replace("\"", "")
                .replace("￥", "")
                .replace("¥", "")
                .replace("`", "");
        try {
            return new BigDecimal(normalized).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private BigDecimal amountYuan(TransactionAmount amount) {
        if (amount == null || amount.getTotal() == null) {
            return null;
        }
        return BigDecimal.valueOf(amount.getTotal(), 2).setScale(2, RoundingMode.HALF_UP);
    }

    private String nextNo(String prefix) {
        return prefix + LocalDateTime.now().format(NO_FORMATTER)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private Map<String, String> parseForm(String body) {
        if (!StringUtils.hasText(body)) {
            return Map.of();
        }
        Map<String, String> params = new HashMap<>();
        for (String pair : body.split("&")) {
            int index = pair.indexOf('=');
            if (index <= 0) {
                continue;
            }
            String key = URLDecoder.decode(pair.substring(0, index), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(index + 1), StandardCharsets.UTF_8);
            params.put(key, value);
        }
        return params;
    }

    private String header(Map<String, String> headers, String name) {
        if (headers.containsKey(name)) {
            return headers.get(name);
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().toLowerCase(Locale.ROOT).equals(lower)) {
                return entry.getValue();
            }
        }
        return "";
    }

    private String firstText(String first, String fallback) {
        return StringUtils.hasText(first) ? first : fallback;
    }

    private LocalDateTime parseWechatTime(String value) {
        if (!StringUtils.hasText(value)) {
            return LocalDateTime.now();
        }
        return OffsetDateTime.parse(value).toLocalDateTime();
    }

    private LocalDateTime parseWechatTimestamp(String value) {
        if (!StringUtils.hasText(value)) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.ofInstant(java.time.Instant.ofEpochSecond(Long.parseLong(value)), java.time.ZoneId.systemDefault());
        } catch (NumberFormatException e) {
            throw new AppException("PAY_0002", "微信支付回调时间戳格式不正确");
        }
    }

    private LocalDateTime parseAlipayTime(String value) {
        if (!StringUtils.hasText(value)) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(value, ALIPAY_TIME_FORMATTER);
        } catch (Exception e) {
            throw new AppException("PAY_0002", "支付宝回调时间戳格式不正确");
        }
    }

    private LocalDateTime parseAlipayTime(java.util.Date value) {
        if (value == null) {
            return LocalDateTime.now();
        }
        return LocalDateTime.ofInstant(value.toInstant(), java.time.ZoneId.systemDefault());
    }

    private record BillParseSummary(int totalCount, BigDecimal totalAmount, String summary) {
    }
}
