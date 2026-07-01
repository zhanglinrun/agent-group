package com.linrun.domain.market.service.trial;

import com.linrun.domain.market.model.GroupBuyActivity;
import com.linrun.domain.market.model.GroupBuyDiscount;
import com.linrun.domain.market.model.GroupBuyMarketSku;
import com.linrun.domain.market.model.GroupBuyStock;
import com.linrun.domain.market.model.SourceChannelSkuActivity;

import java.math.BigDecimal;

public class GroupBuyMarketTrialContext {

    private GroupBuyMarketSku sku;
    private SourceChannelSkuActivity sourceChannelSkuActivity;
    private GroupBuyActivity activity;
    private GroupBuyDiscount discount;
    private GroupBuyStock stock;
    private BigDecimal deductionPrice;
    private BigDecimal payPrice;
    private long dataLoadMillis;
    private boolean visible = true;
    private boolean enable = true;

    public GroupBuyMarketSku getSku() {
        return sku;
    }

    public void setSku(GroupBuyMarketSku sku) {
        this.sku = sku;
    }

    public SourceChannelSkuActivity getSourceChannelSkuActivity() {
        return sourceChannelSkuActivity;
    }

    public void setSourceChannelSkuActivity(SourceChannelSkuActivity sourceChannelSkuActivity) {
        this.sourceChannelSkuActivity = sourceChannelSkuActivity;
    }

    public GroupBuyActivity getActivity() {
        return activity;
    }

    public void setActivity(GroupBuyActivity activity) {
        this.activity = activity;
    }

    public GroupBuyDiscount getDiscount() {
        return discount;
    }

    public void setDiscount(GroupBuyDiscount discount) {
        this.discount = discount;
    }

    public GroupBuyStock getStock() {
        return stock;
    }

    public void setStock(GroupBuyStock stock) {
        this.stock = stock;
    }

    public BigDecimal getDeductionPrice() {
        return deductionPrice;
    }

    public void setDeductionPrice(BigDecimal deductionPrice) {
        this.deductionPrice = deductionPrice;
    }

    public BigDecimal getPayPrice() {
        return payPrice;
    }

    public void setPayPrice(BigDecimal payPrice) {
        this.payPrice = payPrice;
    }

    public long getDataLoadMillis() {
        return dataLoadMillis;
    }

    public void setDataLoadMillis(long dataLoadMillis) {
        this.dataLoadMillis = Math.max(0L, dataLoadMillis);
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isEnable() {
        return enable;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
    }
}















