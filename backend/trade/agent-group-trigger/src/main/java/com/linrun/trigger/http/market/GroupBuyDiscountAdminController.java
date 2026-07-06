package com.linrun.trigger.http.market;

import com.linrun.domain.market.model.GroupBuyDiscount;
import com.linrun.domain.market.service.GroupBuyDiscountAdminService;
import com.linrun.trigger.config.RequestTraceContext;
import com.linrun.types.common.Response;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运营端折扣管理接口：列表、新建/编辑、启停、删除。
 */
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/market/admin/discounts")
public class GroupBuyDiscountAdminController {

    private final GroupBuyDiscountAdminService discountAdminService;

    public GroupBuyDiscountAdminController(GroupBuyDiscountAdminService discountAdminService) {
        this.discountAdminService = discountAdminService;
    }

    @GetMapping
    public Response<List<Map<String, Object>>> list() {
        List<Map<String, Object>> discounts = discountAdminService.listDiscounts().stream()
                .map(this::toView)
                .toList();
        return Response.success(discounts, RequestTraceContext.getRequestId());
    }

    @PostMapping
    public Response<Map<String, Object>> save(@RequestBody Map<String, Object> request) {
        GroupBuyDiscount discount = toEntity(request);
        GroupBuyDiscount saved = discountAdminService.saveDiscount(discount);
        return Response.success(toView(saved), RequestTraceContext.getRequestId());
    }

    @PostMapping("/{discountId}/enabled")
    public Response<Map<String, Object>> toggleEnabled(@PathVariable String discountId,
                                                       @RequestBody Map<String, Object> request) {
        boolean enabled = Boolean.parseBoolean(String.valueOf(request == null ? true : request.getOrDefault("enabled", true)));
        discountAdminService.toggleEnabled(discountId, enabled);
        return Response.success(Map.of("discountId", discountId, "enabled", enabled), RequestTraceContext.getRequestId());
    }

    @DeleteMapping("/{discountId}")
    public Response<Map<String, Object>> delete(@PathVariable String discountId) {
        discountAdminService.deleteDiscount(discountId);
        return Response.success(Map.of("discountId", discountId, "deleted", true), RequestTraceContext.getRequestId());
    }

    private GroupBuyDiscount toEntity(Map<String, Object> request) {
        GroupBuyDiscount discount = new GroupBuyDiscount();
        discount.setDiscountId(text(request.get("discountId")));
        discount.setDiscountName(text(request.get("discountName")));
        discount.setDiscountDesc(text(request.get("discountDesc")));
        discount.setDiscountType(request.get("discountType") == null ? 0 : Integer.parseInt(String.valueOf(request.get("discountType"))));
        discount.setMarketPlan(text(request.get("marketPlan")));
        discount.setMarketExpr(text(request.get("marketExpr")));
        discount.setTagId(text(request.get("tagId")));
        Object enabled = request.get("enabled");
        if (enabled != null) {
            discount.setEnabled(Boolean.parseBoolean(String.valueOf(enabled)));
        }
        return discount;
    }

    private Map<String, Object> toView(GroupBuyDiscount discount) {
        Map<String, Object> view = new LinkedHashMap<>();
        if (discount == null) {
            return view;
        }
        view.put("discountId", discount.getDiscountId());
        view.put("discountName", discount.getDiscountName());
        view.put("discountDesc", discount.getDiscountDesc());
        view.put("discountType", discount.getDiscountType());
        view.put("marketPlan", discount.getMarketPlan());
        view.put("marketExpr", discount.getMarketExpr());
        view.put("tagId", discount.getTagId());
        view.put("enabled", discount.getEnabled() == null || discount.getEnabled());
        return view;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
