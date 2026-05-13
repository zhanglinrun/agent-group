package com.linrun.api.trade.request;

import lombok.Data;

import java.io.Serializable;

@Data
public class CreateDirectOrderRequest implements Serializable {

    private String userId;
    private String goodsId;
    private String payChannel;
}
