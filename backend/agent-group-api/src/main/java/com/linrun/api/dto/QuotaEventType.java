package com.linrun.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author linrun.com
 * @description
 * @create 2026-05-13 上午9:17
 */
@Getter
@AllArgsConstructor
public enum QuotaEventType {

    ANSWER_DELTA("answer_delta"),
    REFERENCE_DELTA("reference_delta"),
    RETRIEVAL_PROGRESS("retrieval_progress"),
    TOOL_PLAN("tool_plan"),
    TOOL_CALL("tool_call"),
    PRODUCT_CARD("product_card"),
    ORDER_DELTA("order_delta"),
    SELF_CHECK("self_check"),
    USAGE_METRIC("usage_metric"),
    DONE("done"),
    ERROR("error");

    private final String code;
}















