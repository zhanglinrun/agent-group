package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class CartValidateResponse implements Serializable {

    private boolean pass = true;
    private BigDecimal totalAmount = BigDecimal.ZERO;
    private List<Item> items = new ArrayList<>();

    @Data
    public static class Item implements Serializable {
        private String goodsId;
        private String goodsName;
        private Integer quantity;
        private Integer marketType;
        private String activityId;
        private BigDecimal unitPrice;
        private BigDecimal lineAmount;
        private Integer availableStock;
        private boolean pass;
        private String message;
    }
}
