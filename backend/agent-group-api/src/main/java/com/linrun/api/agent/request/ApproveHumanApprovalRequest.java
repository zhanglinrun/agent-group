package com.linrun.api.agent.request;

import lombok.Data;

import java.io.Serializable;

@Data
public class ApproveHumanApprovalRequest implements Serializable {

    private String approvalId;
    private String userId;
    private Boolean approved;
    private String reason;
}
