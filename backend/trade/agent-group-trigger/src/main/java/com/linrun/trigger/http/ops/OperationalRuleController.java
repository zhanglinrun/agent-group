package com.linrun.trigger.http.ops;

import com.linrun.api.dto.OperationalRuleResponse;
import com.linrun.api.dto.UpdateOperationalRuleRequest;
import com.linrun.domain.support.config.model.DynamicConfig;
import com.linrun.domain.support.config.service.DynamicConfigService;
import com.linrun.trigger.config.RequestTraceContext;
import com.linrun.types.common.Response;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/ops/rules")
public class OperationalRuleController {

    private static final Map<String, String> GROUPS = Map.ofEntries(
            Map.entry(DynamicConfigService.DOWNGRADE_SWITCH, "降级开关"),
            Map.entry(DynamicConfigService.CUT_RANGE, "切量比例"),
            Map.entry(DynamicConfigService.SC_BLACKLIST, "渠道黑名单"),
            Map.entry(DynamicConfigService.GROUP_SETTLEMENT_NOTIFY_TYPE, "拼团结算通知类型"),
            Map.entry(DynamicConfigService.GROUP_SETTLEMENT_NOTIFY_URL, "拼团结算通知地址"),
            Map.entry(DynamicConfigService.GROUP_SETTLEMENT_NOTIFY_MQ, "拼团结算消息主题"),
            Map.entry(DynamicConfigService.GROUP_REFUND_NOTIFY_TYPE, "拼团退款通知类型"),
            Map.entry(DynamicConfigService.GROUP_REFUND_NOTIFY_URL, "拼团退款通知地址"),
            Map.entry(DynamicConfigService.GROUP_REFUND_NOTIFY_MQ, "拼团退款消息主题"),
            Map.entry(DynamicConfigService.PAYMENT_QUERY_COMPENSATION_SWITCH, "支付查询补偿开关"),
            Map.entry(DynamicConfigService.PAYMENT_QUERY_COMPENSATION_LIMIT, "支付查询补偿上限"),
            Map.entry(DynamicConfigService.PAYMENT_RISK_CHECK_SWITCH, "支付风控检查开关"),
            Map.entry(DynamicConfigService.AGENT_PLAN_EXECUTE_SWITCH, "Agent 计划执行开关"),
            Map.entry(DynamicConfigService.AGENT_CONTEXT_COMPACT_THRESHOLD, "Agent 上下文压缩阈值"),
            Map.entry(DynamicConfigService.KNOWLEDGE_CONTEXT_EXPANSION_SWITCH, "知识库上下文扩展开关")
    );

    private final DynamicConfigService dynamicConfigService;

    public OperationalRuleController(DynamicConfigService dynamicConfigService) {
        this.dynamicConfigService = dynamicConfigService;
    }

    @GetMapping
    public Response<List<OperationalRuleResponse>> listRules() {
        return Response.success(dynamicConfigService.queryAllConfigs().stream()
                .map(this::toResponse)
                .toList(), RequestTraceContext.getRequestId());
    }

    @PostMapping
    public Response<OperationalRuleResponse> updateRule(@RequestBody UpdateOperationalRuleRequest request) {
        DynamicConfig config = dynamicConfigService.updateConfig(request.getRuleKey(), request.getRuleValue());
        return Response.success(toResponse(config), RequestTraceContext.getRequestId());
    }

    private OperationalRuleResponse toResponse(DynamicConfig config) {
        OperationalRuleResponse response = new OperationalRuleResponse();
        response.setRuleKey(config.getConfigKey());
        response.setRuleValue(config.getConfigValue());
        response.setRuleGroup(GROUPS.getOrDefault(config.getConfigKey(), "其他规则"));
        response.setDescription(config.getRemark());
        response.setUpdateTime(config.getUpdateTime());
        return response;
    }
}















