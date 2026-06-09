package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @author linrun.com
 * @description
 * @create 2026-05-13 上午9:22
 */
@Data
public class QuotaStreamEvent<T> implements Serializable {

    private String event;
    private String sessionId;
    private String requestId;
    private Integer sequence;
    private Long timestamp;
    private T data;

    public static <T> QuotaStreamEvent<T> of(String event, String sessionId, String requestId, Integer sequence, T
            data) {
        QuotaStreamEvent<T> streamEvent = new QuotaStreamEvent<>();
        streamEvent.setEvent(event);
        streamEvent.setSessionId(sessionId);
        streamEvent.setRequestId(requestId);
        streamEvent.setSequence(sequence);
        streamEvent.setTimestamp(System.currentTimeMillis());
        streamEvent.setData(data);
        return streamEvent;
    }
}















