package com.linrun.trigger.http.trade;

import com.linrun.api.dto.TradeEventOutboxDispatchResponse;
import com.linrun.trigger.config.RequestTraceContext;
import com.linrun.domain.trade.service.TradeEventOutboxDispatchService;
import com.linrun.types.common.Response;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/trade/event/outbox")
public class TradeEventOutboxController {

    private final TradeEventOutboxDispatchService tradeEventOutboxDispatchService;

    public TradeEventOutboxController(TradeEventOutboxDispatchService tradeEventOutboxDispatchService) {
        this.tradeEventOutboxDispatchService = tradeEventOutboxDispatchService;
    }

    @PostMapping("/exec_job")
    public Response<TradeEventOutboxDispatchResponse> execJob() {
        return Response.success(tradeEventOutboxDispatchService.execDispatchJob(), RequestTraceContext.getRequestId());
    }

    @GetMapping("/status")
    public Response<Map<String, Object>> status() {
        return Response.success(tradeEventOutboxDispatchService.queryStatus(), RequestTraceContext.getRequestId());
    }
}















