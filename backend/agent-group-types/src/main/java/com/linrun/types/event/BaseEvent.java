package com.linrun.types.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 领域事件统一抽象基类。
 *
 * 对齐 s-pay 的 BaseEvent 设计：每个具体事件定义自己的数据载体类型 T，
 * 通过 {@link #buildEventMessage(Object)} 构造带事件 ID、时间戳和负载的标准消息，
 * 通过 {@link #topic()} 声明事件归属的主题（用于消息总线或本地消息表路由）。
 *
 * @param <T> 事件负载类型
 */
public abstract class BaseEvent<T> {

    /**
     * 构造事件消息。
     *
     * @param data 事件负载
     * @return 标准化的事件消息
     */
    public abstract EventMessage<T> buildEventMessage(T data);

    /**
     * 事件归属主题，用于消息总线或本地消息表路由。
     *
     * @return 主题名
     */
    public abstract String topic();

    /**
     * 标准事件消息载体，包含事件 ID、时间戳和业务负载。
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class EventMessage<T> {
        private String id;
        private LocalDateTime timestamp;
        private T data;
    }
}
