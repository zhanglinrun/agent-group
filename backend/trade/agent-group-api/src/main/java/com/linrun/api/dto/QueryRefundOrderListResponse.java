package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class QueryRefundOrderListResponse implements Serializable {

    private List<RefundInfo> refundList = new ArrayList<>();

    @Data
    public static class RefundInfo implements Serializable {
        private Long id;
        private String refundId;
        private String orderId;
        private String payOrderId;
        private String userId;
        private BigDecimal refundAmount;
        private String refundStatus;
        private String refundReason;
        private LocalDateTime createTime;
        private LocalDateTime refundTime;
    }
}















