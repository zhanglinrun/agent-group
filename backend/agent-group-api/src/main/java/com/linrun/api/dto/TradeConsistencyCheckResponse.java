package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
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
        private String buyType;
        private String orderStatus;
        private String payOrderId;
        private String payChannel;
        private String payStatus;
        private BigDecimal payAmount;
        private String refundId;
        private String refundStatus;
        private boolean quotaGrantFlowExists;
        private boolean refundRollbackFlowExists;
        private String conclusion;
        private String message;
    }
}















