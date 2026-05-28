package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class CartValidateRequest implements Serializable {

    private String userId;
    private List<Item> items = new ArrayList<>();

    @Data
    public static class Item implements Serializable {
        private String goodsId;
        private Integer quantity;
        private Integer marketType;
        private String activityId;
    }
}
