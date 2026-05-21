package com.linrun.trigger.job;

import com.linrun.api.order.response.TradeEventOutboxDispatchResponse;
import com.linrun.trigger.service.TradeEventOutboxDispatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "agent.group.trade.event.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TradeEventOutboxDispatchJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(TradeEventOutboxDispatchJob.class);

    private final TradeEventOutboxDispatchService tradeEventOutboxDispatchService;

    public TradeEventOutboxDispatchJob(TradeEventOutboxDispatchService tradeEventOutboxDispatchService) {
        this.tradeEventOutboxDispatchService = tradeEventOutboxDispatchService;
    }

    @Scheduled(cron = "${agent.group.trade.event.outbox.cron:0 */1 * * * ?}")
    public void exec() {
        TradeEventOutboxDispatchResponse response = tradeEventOutboxDispatchService.execDispatchJob();
        if (response.getWaitCount() > 0) {
            LOGGER.info("trade event outbox dispatched, wait={}, success={}, retry={}, deadLetter={}",
                    response.getWaitCount(),
                    response.getSuccessCount(),
                    response.getRetryCount(),
                    response.getDeadLetterCount());
        }
    }
}
