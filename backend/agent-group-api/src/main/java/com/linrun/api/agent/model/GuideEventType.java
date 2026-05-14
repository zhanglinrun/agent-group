package com.linrun.api.agent.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author linrun.com
 * @description
 * @create 2026-05-13 上午9:17
 */
@Getter
@AllArgsConstructor
public enum GuideEventType {

    ANSWER_DELTA("answer_delta"),
    REFERENCE_DELTA("reference_delta"),
    TOOL_CALL("tool_call"),
    PRODUCT_CARD("product_card"),
    ORDER_DELTA("order_delta"),
    SELF_CHECK("self_check"),
    USAGE_METRIC("usage_metric"),
    DONE("done"),
    ERROR("error");

    private final String code;
}
