package com.linrun.infrastructure.trade.port;

import com.linrun.domain.trade.adapter.port.TradeNotifyPort;
import com.linrun.domain.trade.model.notify.NotifyTask;
import com.linrun.domain.trade.adapter.repository.TradeEventPublisher;
import com.linrun.domain.trade.model.entity.TradeEventMessageEntity;
import com.linrun.types.exception.AppException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;

@Component
public class DefaultTradeNotifyPort implements TradeNotifyPort {

    private final TradeEventPublisher tradeEventPublisher;
    private final RestTemplate restTemplate = new RestTemplate();

    public DefaultTradeNotifyPort(TradeEventPublisher tradeEventPublisher) {
        this.tradeEventPublisher = tradeEventPublisher;
    }

    @Override
    public void dispatch(NotifyTask task) {
        if (task == null) {
            return;
        }
        if (NotifyTask.TYPE_HTTP.equalsIgnoreCase(task.getNotifyType())) {
            dispatchHttp(task);
            return;
        }
        if (NotifyTask.TYPE_MQ.equalsIgnoreCase(task.getNotifyType())) {
            dispatchMq(task);
            return;
        }
        throw new AppException("NOTIFY_0005", "unsupported notify type");
    }

    private void dispatchHttp(NotifyTask task) {
        if (!StringUtils.hasText(task.getNotifyUrl()) || "none".equalsIgnoreCase(task.getNotifyUrl())) {
            return;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(task.getParameterJson(), headers);
        try {
            restTemplate.postForEntity(task.getNotifyUrl(), entity, String.class);
        } catch (RestClientException e) {
            throw new AppException("NOTIFY_0001", "notify http dispatch failed");
        }
    }

    private void dispatchMq(NotifyTask task) {
        TradeEventMessageEntity message = new TradeEventMessageEntity();
        message.setFlowId(task.getUuid());
        message.setOrderId(task.getTeamId());
        message.setBizType("NOTIFY");
        message.setBizId(task.getTeamId());
        message.setEventType(task.getNotifyCategory());
        message.setRoutingKey(task.getNotifyMq());
        message.setToStatus("DISPATCHED");
        message.setRemark(task.getParameterJson());
        message.setCreateTime(LocalDateTime.now());
        tradeEventPublisher.publish(message);
    }
}















