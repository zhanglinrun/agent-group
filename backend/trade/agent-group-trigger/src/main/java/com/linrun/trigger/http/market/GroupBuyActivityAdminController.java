package com.linrun.trigger.http.market;

import com.linrun.api.dto.GroupBuyActivityAdminRequest;
import com.linrun.api.dto.GroupBuyActivityAdminResponse;
import com.linrun.api.dto.GroupBuyActivityStockRequest;
import com.linrun.domain.quota.model.QuotaProduct;
import com.linrun.domain.quota.service.QuotaPackageCatalogService;
import com.linrun.domain.market.adapter.repository.GroupBuyMarketRepository;
import com.linrun.domain.market.adapter.repository.GroupBuyStockRepository;
import com.linrun.domain.market.model.GroupBuyActivity;
import com.linrun.domain.market.model.GroupBuyDiscount;
import com.linrun.domain.market.model.GroupBuyStock;
import com.linrun.domain.market.service.GroupBuyActivityAdminService;
import com.linrun.trigger.config.RequestTraceContext;
import com.linrun.types.common.Response;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 运营端拼团活动管理接口。
 */
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/market/admin")
public class GroupBuyActivityAdminController {

    private static final int OPTION_LIMIT = 100;

    private final GroupBuyActivityAdminService groupBuyActivityAdminService;
    private final GroupBuyStockRepository groupBuyStockRepository;
    private final GroupBuyMarketRepository groupBuyMarketRepository;
    private final QuotaPackageCatalogService quotaPackageCatalogService;

    public GroupBuyActivityAdminController(GroupBuyActivityAdminService groupBuyActivityAdminService,
                                           GroupBuyStockRepository groupBuyStockRepository,
                                           GroupBuyMarketRepository groupBuyMarketRepository,
                                           QuotaPackageCatalogService quotaPackageCatalogService) {
        this.groupBuyActivityAdminService = groupBuyActivityAdminService;
        this.groupBuyStockRepository = groupBuyStockRepository;
        this.groupBuyMarketRepository = groupBuyMarketRepository;
        this.quotaPackageCatalogService = quotaPackageCatalogService;
    }

    @GetMapping("/activities")
    public Response<List<GroupBuyActivityAdminResponse>> listActivities() {
        List<GroupBuyActivityAdminResponse> list = groupBuyActivityAdminService.listActivities().stream()
                .map(this::toResponse)
                .toList();
        return Response.success(list, RequestTraceContext.getRequestId());
    }

    @GetMapping("/activities/{activityId}")
    public Response<GroupBuyActivityAdminResponse> queryDetail(@PathVariable("activityId") String activityId) {
        GroupBuyActivity activity = groupBuyActivityAdminService.queryDetail(activityId);
        return Response.success(toResponse(activity), RequestTraceContext.getRequestId());
    }

    @PostMapping("/activities")
    public Response<GroupBuyActivityAdminResponse> createActivity(@RequestBody GroupBuyActivityAdminRequest request) {
        GroupBuyActivity activity = toEntity(request);
        int totalStock = request.getTotalStock() == null ? 0 : request.getTotalStock();
        GroupBuyActivity saved = groupBuyActivityAdminService.createActivity(activity, totalStock);
        return Response.success(toResponse(saved), RequestTraceContext.getRequestId());
    }

    @PutMapping("/activities/{activityId}")
    public Response<GroupBuyActivityAdminResponse> updateActivity(@PathVariable("activityId") String activityId,
                                                                  @RequestBody GroupBuyActivityAdminRequest request) {
        GroupBuyActivity activity = toEntity(request);
        GroupBuyActivity updated = groupBuyActivityAdminService.updateActivity(activityId, activity);
        return Response.success(toResponse(updated), RequestTraceContext.getRequestId());
    }

    @PutMapping("/activities/{activityId}/enabled")
    public Response<Boolean> updateEnabled(@PathVariable("activityId") String activityId,
                                           @RequestParam("enabled") boolean enabled) {
        boolean success = groupBuyActivityAdminService.updateEnabled(activityId, enabled);
        return Response.success(success, RequestTraceContext.getRequestId());
    }

    @PutMapping("/activities/{activityId}/stock")
    public Response<GroupBuyActivityAdminResponse> updateStock(@PathVariable("activityId") String activityId,
                                                               @RequestBody GroupBuyActivityStockRequest request) {
        int totalStock = request.getTotalStock() == null ? 0 : request.getTotalStock();
        groupBuyActivityAdminService.updateStock(activityId, totalStock);
        GroupBuyActivity activity = groupBuyActivityAdminService.queryDetail(activityId);
        return Response.success(toResponse(activity), RequestTraceContext.getRequestId());
    }

    @DeleteMapping("/activities/{activityId}")
    public Response<Boolean> removeActivity(@PathVariable("activityId") String activityId) {
        boolean success = groupBuyActivityAdminService.removeActivity(activityId);
        return Response.success(success, RequestTraceContext.getRequestId());
    }

    /**
     * 商品下拉选项（关联额度包）。
     */
    @GetMapping("/goods-options")
    public Response<List<Map<String, Object>>> goodsOptions() {
        List<Map<String, Object>> options = quotaPackageCatalogService.listPackages(null, OPTION_LIMIT).stream()
                .map(this::toGoodsOption)
                .toList();
        return Response.success(options, RequestTraceContext.getRequestId());
    }

    /**
     * 折扣下拉选项。
     */
    @GetMapping("/discount-options")
    public Response<List<Map<String, Object>>> discountOptions() {
        List<Map<String, Object>> options = groupBuyMarketRepository.queryDiscountList(OPTION_LIMIT).stream()
                .map(this::toDiscountOption)
                .toList();
        return Response.success(options, RequestTraceContext.getRequestId());
    }

    private Map<String, Object> toGoodsOption(QuotaProduct product) {
        return Map.of(
                "goodsId", product.getGoodsId(),
                "goodsName", product.getGoodsName(),
                "originalPrice", product.getOriginPrice()
        );
    }

    private Map<String, Object> toDiscountOption(GroupBuyDiscount discount) {
        return Map.of(
                "discountId", discount.getDiscountId(),
                "discountName", discount.getDiscountName(),
                "marketPlan", discount.getMarketPlan(),
                "marketExpr", discount.getMarketExpr()
        );
    }

    private GroupBuyActivity toEntity(GroupBuyActivityAdminRequest request) {
        GroupBuyActivity activity = new GroupBuyActivity();
        activity.setActivityName(request.getActivityName());
        activity.setGoodsId(request.getGoodsId());
        activity.setGroupPrice(request.getGroupPrice());
        activity.setTeamSize(request.getTeamSize());
        activity.setDiscountId(request.getDiscountId());
        activity.setGroupType(request.getGroupType());
        activity.setTakeLimitCount(request.getTakeLimitCount());
        activity.setTarget(request.getTarget());
        activity.setValidTime(request.getValidTime());
        activity.setStatus(request.getStatus());
        activity.setStartTime(request.getStartTime());
        activity.setEndTime(request.getEndTime());
        activity.setTagId(request.getTagId());
        activity.setTagScope(request.getTagScope());
        activity.setEnabled(request.getEnabled());
        return activity;
    }

    private GroupBuyActivityAdminResponse toResponse(GroupBuyActivity activity) {
        GroupBuyActivityAdminResponse response = new GroupBuyActivityAdminResponse();
        response.setActivityId(activity.getActivityId());
        response.setActivityName(activity.getActivityName());
        response.setGoodsId(activity.getGoodsId());
        response.setGroupPrice(activity.getGroupPrice());
        response.setTeamSize(activity.resolveTeamSize());
        response.setDiscountId(activity.getDiscountId());
        response.setGroupType(activity.getGroupType());
        response.setTakeLimitCount(activity.getTakeLimitCount());
        response.setTarget(activity.getTarget());
        response.setValidTime(activity.getValidTime());
        response.setStatus(activity.getStatus());
        response.setStartTime(activity.getStartTime());
        response.setEndTime(activity.getEndTime());
        response.setTagId(activity.getTagId());
        response.setTagScope(activity.getTagScope());
        response.setEnabled(activity.getEnabled());
        GroupBuyStock stock = groupBuyStockRepository.queryByActivityId(activity.getActivityId()).orElse(null);
        if (stock != null) {
            response.setTotalStock(stock.getTotalStock());
            response.setAvailableStock(stock.getAvailableStock());
            response.setLockedStock(stock.getLockedStock());
            response.setPaidStock(stock.getPaidStock());
        }
        return response;
    }
}
