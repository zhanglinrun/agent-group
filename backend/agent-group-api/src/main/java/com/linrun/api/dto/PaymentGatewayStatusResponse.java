package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class PaymentGatewayStatusResponse implements Serializable {

    private boolean mockReady;
    private boolean officialGatewayReady;
    private boolean officialSandboxReady;
    private String message;
    private List<ChannelStatus> channels = new ArrayList<>();

    @Data
    public static class ChannelStatus implements Serializable {

        private String payChannel;
        private String mode;
        private boolean configured;
        private boolean sandboxMode;
        private String gatewayUrl;
        private Map<String, Boolean> requiredItems = new LinkedHashMap<>();
        private String message;
    }
}
