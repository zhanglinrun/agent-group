package com.linrun.trigger.http;

import com.linrun.api.dto.MallProductDTO;
import com.linrun.api.dto.CartValidateRequest;
import com.linrun.api.dto.CartValidateResponse;
import com.linrun.api.dto.ProductCatalogResponse;
import com.linrun.trigger.config.RequestTraceContext;
import com.linrun.types.common.Response;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/mall")
public class MallProductCatalogController {

    private final MallProductCatalogHandler mallProductCatalogHandler;

    public MallProductCatalogController(MallProductCatalogHandler mallProductCatalogHandler) {
        this.mallProductCatalogHandler = mallProductCatalogHandler;
    }

    @GetMapping("/products")
    public Response<ProductCatalogResponse> listProducts(@RequestParam(required = false) String keyword,
                                                         @RequestParam(defaultValue = "20") int limit) {
        return Response.success(mallProductCatalogHandler.listProducts(keyword, limit), RequestTraceContext.getRequestId());
    }

    @GetMapping("/products/{goodsId}")
    public Response<MallProductDTO> queryProductDetail(@PathVariable String goodsId) {
        return Response.success(mallProductCatalogHandler.queryProductDetail(goodsId), RequestTraceContext.getRequestId());
    }

    @PostMapping("/cart/validate")
    public Response<CartValidateResponse> validateCart(@RequestBody CartValidateRequest request) {
        return Response.success(mallProductCatalogHandler.validateCart(request), RequestTraceContext.getRequestId());
    }
}
