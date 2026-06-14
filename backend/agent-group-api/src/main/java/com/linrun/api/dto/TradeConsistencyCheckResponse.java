package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class TradeConsistencyCheckResponse implements Serializable {

    private int checkedCount;
    private List<Item> items = new ArrayList<>();

    @Data
    public static class Item implements Serializable {

        private String orderId;
        private String userId;
        private String goodsId;
        private String goodsName;
        private String activityId;
        private String buyType;
        private String orderStatus;
        private BigDecimal originAmount;
        private BigDecimal orderPayAmount;
        private LocalDateTime orderCreateTime;
        private LocalDateTime orderPayTime;
        private LocalDateTime orderCloseTime;
        private String payOrderId;
        private String payChannel;
        private String payStatus;
        private BigDecimal payAmount;
        private String outTradeNo;
        private LocalDateTime payCreateTime;
        private LocalDateTime payTime;
        private String refundId;
        private String refundStatus;
        private BigDecimal refundAmount;
        private String refundReason;
        private LocalDateTime refundCreateTime;
        private LocalDateTime refundTime;
        private boolean quotaGrantFlowExists;
        private boolean refundRollbackFlowExists;
        private boolean quotaGrantAllowed;
        private boolean refundRollbackRequired;
        private String settlementLabel;
        private String settlementDetail;
        private List<String> facts = new ArrayList<>();
        private String conclusion;
        private String message;
    }
}














