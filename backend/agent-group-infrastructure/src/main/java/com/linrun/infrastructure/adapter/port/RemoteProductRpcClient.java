package com.linrun.infrastructure.adapter.port;

import com.linrun.api.dto.MallProductDTO;
import com.linrun.api.dto.ProductCatalogResponse;
import com.linrun.domain.agent.conversation.adapter.ProductRpcClient;
import com.linrun.domain.agent.conversation.model.GuideProduct;
import com.linrun.infrastructure.gateway.ProductRemoteService;
import com.linrun.types.common.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Primary
@Component
@ConditionalOnProperty(name = "agent.group.product.rpc.enabled", havingValue = "true")
public class RemoteProductRpcClient implements ProductRpcClient {

    private static final String SUCCESS_CODE = "0000";

    private final ProductRemoteService productRemoteService;
    private final LocalProductRpcClient fallbackClient;

    public RemoteProductRpcClient(@Value("${agent.group.product.rpc.base-url:http://127.0.0.1:18080/}") String baseUrl,
                                  LocalProductRpcClient fallbackClient) {
        this.productRemoteService = new Retrofit.Builder()
                .baseUrl(normalizeBaseUrl(baseUrl))
                .addConverterFactory(JacksonConverterFactory.create())
                .build()
                .create(ProductRemoteService.class);
        this.fallbackClient = fallbackClient;
    }

    @Override
    public List<GuideProduct> queryProducts(String keyword, int limit) {
        int safeLimit = limit <= 0 ? 20 : Math.min(limit, 50);
        try {
            Response<ProductCatalogResponse> response = productRemoteService.queryProducts(keyword, safeLimit)
                    .execute()
                    .body();
            if (isSuccess(response) && response.getData() != null) {
                return response.getData().getProducts().stream()
                        .map(this::toGuideProduct)
                        .map(this::normalize)
                        .toList();
            }
        } catch (IOException ignored) {
            // 外部商品服务不可用时，演示链路回退本地商品库。
        }
        return fallbackClient.queryProducts(keyword, safeLimit);
    }

    @Override
    public Optional<GuideProduct> queryProductByGoodsId(String goodsId) {
        try {
            Response<MallProductDTO> response = productRemoteService.queryProductByGoodsId(goodsId)
                    .execute()
                    .body();
            if (isSuccess(response) && response.getData() != null) {
                return Optional.of(normalize(toGuideProduct(response.getData())));
            }
        } catch (IOException ignored) {
            // 外部商品服务不可用时，演示链路回退本地商品库。
        }
        return fallbackClient.queryProductByGoodsId(goodsId);
    }

    private boolean isSuccess(Response<?> response) {
        return response != null && SUCCESS_CODE.equals(response.getCode());
    }

    private GuideProduct toGuideProduct(MallProductDTO dto) {
        GuideProduct product = new GuideProduct();
        product.setGoodsId(dto.getGoodsId());
        product.setGoodsName(dto.getGoodsName());
        product.setImageUrl(dto.getImageUrl());
        product.setOriginPrice(dto.getOriginPrice());
        product.setGroupPrice(dto.getGroupPrice());
        product.setSpecSummary(dto.getSpecSummary());
        product.setAfterSalePolicy(dto.getAfterSalePolicy());
        product.setRecommendReason(dto.getRecommendReason());
        product.setNotSuitableFor(dto.getNotSuitableFor());
        product.setActivityId(dto.getActivityId());
        product.setTeamSize(dto.getTeamSize());
        product.setRemainingSeconds(dto.getRemainingSeconds());
        return product;
    }

    private GuideProduct normalize(GuideProduct product) {
        if (product.getGroupPrice() == null) {
            product.setGroupPrice(product.getOriginPrice());
        }
        if (product.getTeamSize() == null) {
            product.setTeamSize(1);
        }
        if (product.getRemainingSeconds() == null || product.getRemainingSeconds() <= 0) {
            product.setRemainingSeconds((int) Duration.ofMinutes(30).toSeconds());
        }
        return product;
    }

    private String normalizeBaseUrl(String baseUrl) {
        String safeBaseUrl = StringUtils.hasText(baseUrl) ? baseUrl.trim() : "http://127.0.0.1:18080/";
        return safeBaseUrl.endsWith("/") ? safeBaseUrl : safeBaseUrl + "/";
    }
}
