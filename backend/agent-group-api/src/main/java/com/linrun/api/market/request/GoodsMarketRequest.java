package com.linrun.api.market.request;

import lombok.Data;

import java.io.Serializable;

@Data
public class GoodsMarketRequest implements Serializable {

    private String userId;
    private String source;
    private String channel;
    private String goodsId;
}
