package com.linrun.infrastructure.gateway;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayConfig;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.alipay.api.domain.AlipayTradePrecreateModel;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.domain.AlipayTradeRefundModel;
import com.linrun.domain.trade.adapter.port.PaymentGatewayClient;
import com.linrun.domain.trade.model.payment.PaymentChannel;
import com.linrun.domain.trade.model.payment.PaymentCreateCommand;
import com.linrun.domain.trade.model.payment.PaymentCreateResult;
import com.linrun.domain.trade.model.payment.PaymentReconcileCommand;
import com.linrun.domain.trade.model.payment.PaymentReconcileResult;
import com.linrun.domain.trade.model.payment.PaymentRefundCommand;
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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
public class OfficialPaymentGatewayClient implements PaymentGatewayClient {

    private static final DateTimeFormatter NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter ALIPAY_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
}
