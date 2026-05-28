package com.linrun.infrastructure.gateway;

import com.linrun.api.dto.MallProductDTO;
import com.linrun.api.dto.ProductCatalogResponse;
import com.linrun.types.common.Response;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ProductRemoteService {

    @GET("api/v1/mall/products")
    Call<Response<ProductCatalogResponse>> queryProducts(@Query("keyword") String keyword,
                                                         @Query("limit") int limit);

    @GET("api/v1/mall/products/{goodsId}")
    Call<Response<MallProductDTO>> queryProductByGoodsId(@Path("goodsId") String goodsId);
}
