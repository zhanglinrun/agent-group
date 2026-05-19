package com.linrun.api.mall.response;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class QueryOrderListResponse implements Serializable {

    private List<OrderInfo> orderList = new ArrayList<>();
    private boolean hasMore;
    private Long lastId;

    @Data
    public static class OrderInfo implements Serializable {

        private Long id;
        private String userId;
        private String productId;
        private String productName;
        private String orderId;
        private LocalDateTime orderTime;
        private BigDecimal totalAmount;
        private String status;
        private String payUrl;
        private Integer marketType;
        private BigDecimal marketDeductionAmount;
        private BigDecimal payAmount;
        private LocalDateTime payTime;
    }
}
