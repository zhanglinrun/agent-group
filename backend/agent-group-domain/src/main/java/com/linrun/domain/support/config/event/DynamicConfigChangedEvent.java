package com.linrun.domain.support.config.event;

public record DynamicConfigChangedEvent(String configKey, String configValue) {
}
