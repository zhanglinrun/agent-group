package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class AgentReplayResponse implements Serializable {

    private String sessionId;
    private String runId;
    private String status;
    private List<QuotaStreamEvent<Map<String, Object>>> events = new ArrayList<>();
}















