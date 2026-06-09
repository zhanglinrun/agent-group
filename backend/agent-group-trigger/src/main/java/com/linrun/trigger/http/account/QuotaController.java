package com.linrun.trigger.http.account;

import com.linrun.api.dto.QuotaAccountResponse;
import com.linrun.api.dto.QuotaPackageCatalogResponse;
import com.linrun.api.dto.QuotaSummaryResponse;
import com.linrun.api.dto.ProductCardDTO;
import com.linrun.api.dto.QuotaGrantOrderRequest;
import com.linrun.api.dto.QuotaGrantOrderResponse;
import com.linrun.api.dto.UserModelConfigRequest;
import com.linrun.api.dto.UserModelConfigResponse;
import com.linrun.domain.agent.conversation.model.QuotaProduct;
import com.linrun.domain.agent.conversation.service.QuotaPackageCatalogService;
import com.linrun.domain.account.model.UserAccount;
import com.linrun.domain.account.service.UserAccountService;
import com.linrun.domain.account.service.UserQuotaService;
import com.linrun.trigger.config.RequestTraceContext;
import com.linrun.types.common.Response;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/quota")
public class QuotaController {

    private final UserAccountService userAccountService;
    private final UserQuotaService userQuotaService;
    private final QuotaPackageCatalogService quotaPackageCatalogService;

    public QuotaController(UserAccountService userAccountService,
                           UserQuotaService userQuotaService,
                           QuotaPackageCatalogService quotaPackageCatalogService) {
        this.userAccountService = userAccountService;
        this.userQuotaService = userQuotaService;
        this.quotaPackageCatalogService = quotaPackageCatalogService;
    }

    @GetMapping("/summary")
    public Response<QuotaSummaryResponse> summary(@RequestHeader(value = "Authorization", required = false) String token,
                                                  @RequestParam(defaultValue = "20") int limit) {
        UserAccount user = userAccountService.requireUserByToken(token);
        return Response.success(userQuotaService.querySummary(user.getUserId(), limit), RequestTraceContext.getRequestId());
    }

    @GetMapping("/account")
    public Response<QuotaAccountResponse> account(@RequestHeader(value = "Authorization", required = false) String token) {
        UserAccount user = userAccountService.requireUserByToken(token);
        return Response.success(userQuotaService.queryAccountResponse(user.getUserId()), RequestTraceContext.getRequestId());
    }

    @GetMapping("/model-config")
    public Response<UserModelConfigResponse> modelConfig(@RequestHeader(value = "Authorization", required = false) String token) {
        UserAccount user = userAccountService.requireUserByToken(token);
        return Response.success(userQuotaService.queryModelConfigResponse(user.getUserId()), RequestTraceContext.getRequestId());
    }

    @PostMapping("/model-config")
    public Response<UserModelConfigResponse> saveModelConfig(@RequestHeader(value = "Authorization", required = false) String token,
                                                            @RequestBody(required = false) UserModelConfigRequest request) {
        UserAccount user = userAccountService.requireUserByToken(token);
        return Response.success(userQuotaService.saveModelConfig(user.getUserId(), request), RequestTraceContext.getRequestId());
    }

    @GetMapping("/packages")
    public Response<QuotaPackageCatalogResponse> packages(@RequestParam(required = false) String keyword,
                                                          @RequestParam(defaultValue = "20") int limit) {
        QuotaPackageCatalogResponse response = new QuotaPackageCatalogResponse();
        response.setPackages(quotaPackageCatalogService.listPackages(keyword, limit).stream()
                .map(this::toProductCard)
                .toList());
        return Response.success(response, RequestTraceContext.getRequestId());
    }

    @PostMapping("/admin/grant-by-orders")
    public Response<QuotaGrantOrderResponse> grantQuotaByOrders(@RequestBody(required = false) QuotaGrantOrderRequest request) {
        List<String> orderIds = request == null || request.getOrderIds() == null ? List.of() : request.getOrderIds();
        List<String> processedOrderIds = userQuotaService.grantQuotaForOrderIds(orderIds);
        QuotaGrantOrderResponse response = new QuotaGrantOrderResponse();
        response.setRequestedCount(orderIds.size());
        response.setProcessedOrderIds(processedOrderIds);
        response.setProcessedCount(processedOrderIds.size());
        response.setMessage("已按后端订单状态执行额度补发，未满足到账条件的订单不会发放额度");
        return Response.success(response, RequestTraceContext.getRequestId());
    }

    private ProductCardDTO toProductCard(QuotaProduct product) {
        ProductCardDTO dto = new ProductCardDTO();
        dto.setGoodsId(product.getGoodsId());
        dto.setGoodsName(product.getGoodsName());
        dto.setImageUrl(product.getImageUrl());
        dto.setOriginPrice(product.getOriginPrice());
        dto.setGroupPrice(product.getGroupPrice());
        dto.setQuotaAmount(product.getQuotaAmount());
        dto.setProductType(product.getProductType());
        dto.setSpecSummary(product.getSpecSummary());
        dto.setAfterSalePolicy(product.getAfterSalePolicy());
        dto.setRecommendReason(product.getRecommendReason());
        dto.setNotSuitableFor(product.getNotSuitableFor());
        dto.setActivityId(product.getActivityId());
        dto.setTeamSize(product.getTeamSize());
        dto.setRemainingSeconds(product.getRemainingSeconds());
        return dto;
    }
}















