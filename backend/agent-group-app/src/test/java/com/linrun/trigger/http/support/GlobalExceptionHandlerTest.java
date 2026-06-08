package com.linrun.trigger.http.support;

import com.linrun.trigger.config.RequestTraceContext;
import com.linrun.trigger.config.RequestTraceFilter;
import com.linrun.types.enums.ResponseCode;
import com.linrun.types.exception.AppException;
import com.linrun.types.common.Response;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new RequestTraceFilter())
            .build();

    @Test
    void shouldReturnRequestIdInHeaderAndBody() throws Exception {
        mockMvc.perform(get("/test/ok").header(RequestTraceFilter.REQUEST_ID_HEADER, "REQ-10001"))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestTraceFilter.REQUEST_ID_HEADER, "REQ-10001"))
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.requestId").value("REQ-10001"))
                .andExpect(jsonPath("$.data").value("ok"));
    }

    @Test
    void shouldHandleBusinessException() throws Exception {
        mockMvc.perform(get("/test/biz-error").header(RequestTraceFilter.REQUEST_ID_HEADER, "REQ-10002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.BIZ_ERROR.getCode()))
                .andExpect(jsonPath("$.info").value("活动已结束"))
                .andExpect(jsonPath("$.requestId").value("REQ-10002"));
    }

    @Test
    void shouldHandleParamException() throws Exception {
        mockMvc.perform(get("/test/param-error").header(RequestTraceFilter.REQUEST_ID_HEADER, "REQ-10003"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ResponseCode.PARAM_ERROR.getCode()))
                .andExpect(jsonPath("$.info").value(ResponseCode.PARAM_ERROR.getInfo()))
                .andExpect(jsonPath("$.requestId").value("REQ-10003"));
    }

    @Test
    void shouldHandleSystemException() throws Exception {
        mockMvc.perform(get("/test/system-error").header(RequestTraceFilter.REQUEST_ID_HEADER, "REQ-10004"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(ResponseCode.SYSTEM_ERROR.getCode()))
                .andExpect(jsonPath("$.info").value("系统繁忙，请稍后再试"))
                .andExpect(jsonPath("$.requestId").value("REQ-10004"));
    }

    @RestController
    static class TestController {

        @GetMapping(value = "/test/ok", produces = MediaType.APPLICATION_JSON_VALUE)
        public Response<String> ok() {
            return Response.success("ok", RequestTraceContext.getRequestId());
        }

        @GetMapping("/test/biz-error")
        public Response<Void> bizError() {
            throw new AppException(ResponseCode.BIZ_ERROR.getCode(), "活动已结束");
        }

        @GetMapping("/test/param-error")
        public Response<Void> paramError() {
            throw new IllegalArgumentException("goodsId is required");
        }

        @GetMapping("/test/system-error")
        public Response<Void> systemError() {
            throw new IllegalStateException("database unavailable");
        }
    }
}
