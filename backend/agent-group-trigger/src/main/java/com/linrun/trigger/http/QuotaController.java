package com.linrun.trigger.http;

import com.linrun.api.dto.QuotaAccountResponse;
import com.linrun.api.dto.QuotaPackageCatalogResponse;
import com.linrun.api.dto.QuotaSummaryResponse;
import com.linrun.api.dto.ProductCardDTO;
import com.linrun.domain.agent.conversation.model.GuideProduct;
import com.linrun.domain.agent.conversation.service.QuotaPackageCatalogService;
import com.linrun.domain.account.model.UserAccount;
import com.linrun.domain.account.service.UserAccountService;
import com.linrun.domain.account.service.UserQuotaService;
import com.linrun.trigger.config.RequestTraceContext;
import com.linrun.types.common.Response;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/packages")
    public Response<QuotaPackageCatalogResponse> packages(@RequestParam(required = false) String keyword,
                                                          @RequestParam(defaultValue = "20") int limit) {
        QuotaPackageCatalogResponse response = new QuotaPackageCatalogResponse();
        response.setPackages(quotaPackageCatalogService.listPackages(keyword, limit).stream()
                .map(this::toProductCard)
                .toList());
        return Response.success(response, RequestTraceContext.getRequestId());
    }

    private ProductCardDTO toProductCard(GuideProduct product) {
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
