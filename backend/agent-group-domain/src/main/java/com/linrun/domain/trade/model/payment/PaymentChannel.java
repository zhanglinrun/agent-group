package com.linrun.domain.trade.model.payment;

import com.linrun.types.exception.AppException;
import org.springframework.util.StringUtils;

import java.util.Locale;

public enum PaymentChannel {

    MOCK_PAY,
    ALIPAY,
    WECHAT_PAY;

    public static PaymentChannel parse(String value) {
        if (!StringUtils.hasText(value)) {
            return MOCK_PAY;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace("-", "_");
        for (PaymentChannel channel : values()) {
            if (channel.name().equals(normalized)) {
                return channel;
            }
        }
        throw new AppException("PAY_0001", "不支持的支付渠道");
    }
}
