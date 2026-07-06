package com.linrun.domain.trade.model.notify;

import com.linrun.types.exception.AppException;
import org.springframework.util.StringUtils;

import java.util.Locale;

public class NotifyConfig {

    private final String notifyType;
    private final String notifyMq;
    private final String notifyUrl;

    private NotifyConfig(String notifyType, String notifyMq, String notifyUrl) {
        this.notifyType = normalizeType(notifyType);
        this.notifyMq = notifyMq;
        this.notifyUrl = notifyUrl;
        validate();
    }

    public static NotifyConfig http(String notifyUrl) {
        return new NotifyConfig(NotifyTask.TYPE_HTTP, "", notifyUrl);
    }

    public static NotifyConfig mq(String notifyMq) {
        return new NotifyConfig(NotifyTask.TYPE_MQ, notifyMq, "");
    }

    public static NotifyConfig of(String notifyType, String notifyMq, String notifyUrl) {
        return new NotifyConfig(notifyType, notifyMq, notifyUrl);
    }

    public boolean isHttp() {
        return NotifyTask.TYPE_HTTP.equalsIgnoreCase(notifyType);
    }

    public boolean isMq() {
        return NotifyTask.TYPE_MQ.equalsIgnoreCase(notifyType);
    }

    public String getNotifyType() {
        return notifyType;
    }

    public String getNotifyMq() {
        return notifyMq;
    }

    public String getNotifyUrl() {
        return notifyUrl;
    }

    private void validate() {
        if (!isHttp() && !isMq()) {
            throw new AppException("NOTIFY_0005", "unsupported notify type");
        }
        if (isMq() && !StringUtils.hasText(notifyMq)) {
            throw new AppException("NOTIFY_0006", "notify mq cannot be blank");
        }
    }

    private static String normalizeType(String notifyType) {
        if (!StringUtils.hasText(notifyType)) {
            return NotifyTask.TYPE_HTTP;
        }
        return notifyType.trim().toUpperCase(Locale.ROOT);
    }
}















