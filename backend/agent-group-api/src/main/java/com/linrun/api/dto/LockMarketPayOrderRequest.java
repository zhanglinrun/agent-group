package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class LockMarketPayOrderRequest implements Serializable {

    private String userId;
    private String decisionId;
    private String teamId;
    private String activityId;
    private String goodsId;
    private String source;
    private String channel;
    private String outTradeNo;
    private String hitlApprovalId;
    private NotifyConfig notifyConfigVO;

    @Data
    public static class NotifyConfig implements Serializable {

        private String notifyType;
        private String notifyMQ;
        private String notifyUrl;
    }
}
