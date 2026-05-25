package com.linrun.api.agent.response;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author linrun.com
 * @description
 * @create 2026-05-13 上午9:28
 */
@Data
public class ToolCallDTO implements Serializable {

    private String toolName;
    private String action;
    private String status;
    private String message;
    private Map<String, String> arguments = new LinkedHashMap<>();
    private Long latencyMillis;
    private String toolCallId;
    private String toolVersion;
    private String riskLevel;
    private Boolean resultCitationRequired;
    private Integer retryCount;
    private String resultDigest;
    private List<String> citationIds = new ArrayList<>();
}
