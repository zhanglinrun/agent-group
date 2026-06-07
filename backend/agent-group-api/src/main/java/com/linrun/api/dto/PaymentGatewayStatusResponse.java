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
    private boolean alipaySandboxReady;
    private String recommendedChannel;
    private String sandboxEvidence;
    private String message;
    private List<String> officialSandboxMissingItems = new ArrayList<>();
    private List<ChannelStatus> channels = new ArrayList<>();

    @Data
    public static class ChannelStatus implements Serializable {

        private String payChannel;
        private String mode;
        private boolean configured;
        private boolean sandboxMode;
        private String gatewayUrl;
        private String notifyUrl;
        private String returnUrl;
        private int readyItemCount;
        private int requiredItemCount;
        private List<String> missingItems = new ArrayList<>();
        private Map<String, Boolean> requiredItems = new LinkedHashMap<>();
        private String lastError;
        private String message;
    }
}
