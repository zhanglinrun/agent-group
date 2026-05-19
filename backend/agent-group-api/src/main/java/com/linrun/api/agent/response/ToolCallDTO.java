package com.linrun.api.agent.response;

import lombok.Data;

import java.io.Serializable;

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
    private Long latencyMillis;
}
