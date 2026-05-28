package com.linrun.trigger.http;

import com.linrun.api.dto.MallProductDTO;
import com.linrun.api.dto.CartValidateRequest;
import com.linrun.api.dto.CartValidateResponse;
import com.linrun.api.dto.ProductCatalogResponse;
import com.linrun.domain.activity.model.GroupBuyTrialResult;
import com.linrun.domain.activity.service.GroupBuyActivityService;
import com.linrun.domain.agent.conversation.model.GuideProduct;
import com.linrun.domain.agent.conversation.service.ProductCatalogService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.math.BigDecimal;

@Service
public class MallProductCatalogHandler {

    private final ProductCatalogService productCatalogService;
    private final GroupBuyActivityService groupBuyActivityService;

    public MallProductCatalogHandler(ProductCatalogService productCatalogService,
                                     GroupBuyActivityService groupBuyActivityService) {
        this.productCatalogService = productCatalogService;
        this.groupBuyActivityService = groupBuyActivityService;
    }

    public ProductCatalogResponse listProducts(String keyword, int limit) {
        ProductCatalogResponse response = new ProductCatalogResponse();
        response.setProducts(productCatalogService.listProducts(keyword, limit).stream()
                .map(this::toDTO)
                .toList());
        return response;
    }

    public MallProductDTO queryProductDetail(String goodsId) {
        return toDTO(productCatalogService.queryProductDetail(goodsId));
    }

    public CartValidateResponse validateCart(CartValidateRequest request) {
        CartValidateResponse response = new CartValidateResponse();
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            response.setPass(false);
            return response;
        }
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (CartValidateRequest.Item item : request.getItems()) {
            CartValidateResponse.Item line = validateCartItem(item);
            response.getItems().add(line);
            response.setPass(response.isPass() && line.isPass());
            totalAmount = totalAmount.add(line.getLineAmount() == null ? BigDecimal.ZERO : line.getLineAmount());
        }
        response.setTotalAmount(totalAmount);
        return response;
    }

    public List<MallProductDTO> queryProductOptions(String keyword, int limit) {
        return productCatalogService.listProducts(keyword, limit).stream()
                .map(this::toDTO)
                .toList();
    }

    private MallProductDTO toDTO(GuideProduct product) {
        MallProductDTO dto = new MallProductDTO();
        dto.setGoodsId(product.getGoodsId());
        dto.setGoodsName(product.getGoodsName());
        dto.setImageUrl(product.getImageUrl());
        dto.setOriginPrice(product.getOriginPrice());
        dto.setGroupPrice(product.getGroupPrice());
        dto.setSpecSummary(product.getSpecSummary());
        dto.setAfterSalePolicy(product.getAfterSalePolicy());
        dto.setRecommendReason(product.getRecommendReason());
        dto.setNotSuitableFor(product.getNotSuitableFor());
        dto.setActivityId(product.getActivityId());
        dto.setTeamSize(product.getTeamSize());
        dto.setRemainingSeconds(product.getRemainingSeconds());
        fillMarket(dto);
        return dto;
    }

    private CartValidateResponse.Item validateCartItem(CartValidateRequest.Item item) {
        CartValidateResponse.Item line = new CartValidateResponse.Item();
        line.setGoodsId(item == null ? "" : item.getGoodsId());
        int quantity = item == null || item.getQuantity() == null || item.getQuantity() <= 0 ? 1 : item.getQuantity();
        int marketType = item == null || item.getMarketType() == null ? 0 : item.getMarketType();
        line.setQuantity(quantity);
        line.setMarketType(marketType);
        try {
            MallProductDTO product = queryProductDetail(item.getGoodsId());
            line.setGoodsName(product.getGoodsName());
            line.setActivityId(product.getActivityId());
            line.setAvailableStock(product.isGroupBuyAvailable() ? null : 9999);
            BigDecimal unitPrice = marketType == 1 ? product.getGroupPrice() : product.getOriginPrice();
            line.setUnitPrice(unitPrice);
            line.setLineAmount(unitPrice == null ? BigDecimal.ZERO : unitPrice.multiply(BigDecimal.valueOf(quantity)));
            if (marketType == 1) {
                GroupBuyTrialResult trialResult = groupBuyActivityService.trial(product.getGoodsId());
                line.setAvailableStock(trialResult.getAvailableStock());
                boolean pass = trialResult.isAvailable()
                        && (trialResult.getAvailableStock() == null || trialResult.getAvailableStock() >= quantity);
                line.setPass(pass);
                line.setMessage(pass ? "库存和活动校验通过" : trialResult.getMessage());
            } else {
                line.setPass(true);
                line.setMessage("直接购买商品校验通过");
            }
        } catch (Exception e) {
            line.setPass(false);
            line.setLineAmount(BigDecimal.ZERO);
            line.setMessage(e.getMessage());
        }
        return line;
    }

    private void fillMarket(MallProductDTO dto) {
        try {
            GroupBuyTrialResult trialResult = groupBuyActivityService.trial(dto.getGoodsId());
            dto.setGroupBuyAvailable(trialResult.isAvailable());
            dto.setMarketMessage(trialResult.getMessage());
            dto.setActivityId(trialResult.getActivityId());
            dto.setGroupPrice(trialResult.getGroupPrice());
            dto.setTeamSize(trialResult.getTeamSize());
            dto.setRemainingSeconds(trialResult.getRemainingSeconds());
        } catch (Exception e) {
            dto.setGroupBuyAvailable(false);
            dto.setMarketMessage("拼团活动暂不可用");
        }
    }
}
