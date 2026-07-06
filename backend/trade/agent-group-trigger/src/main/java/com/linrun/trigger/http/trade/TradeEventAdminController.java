package com.linrun.trigger.http.trade;

import com.linrun.domain.trade.model.entity.TradeEventConsumeRecordEntity;
import com.linrun.domain.trade.service.TradeEventConsumeAdminService;
import com.linrun.trigger.config.RequestTraceContext;
import com.linrun.types.common.Response;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/trade/admin/events")
public class TradeEventAdminController {

    private final TradeEventConsumeAdminService tradeEventConsumeAdminService;

    public TradeEventAdminController(TradeEventConsumeAdminService tradeEventConsumeAdminService) {
        this.tradeEventConsumeAdminService = tradeEventConsumeAdminService;
    }

    @GetMapping("/dead-letters")
    public Response<List<TradeEventConsumeRecordEntity>> listDeadLetters(
            @RequestParam(defaultValue = "50") int limit) {
        return Response.success(tradeEventConsumeAdminService.listDeadLetters(limit), RequestTraceContext.getRequestId());
    }

    @PostMapping("/dead-letters/{eventId}/replay")
    public Response<Map<String, Object>> replayDeadLetter(@PathVariable String eventId) {
        tradeEventConsumeAdminService.replayDeadLetter(eventId);
        return Response.success(Map.of("eventId", eventId, "replayed", true), RequestTraceContext.getRequestId());
    }
}
