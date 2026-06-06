package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class QuotaSummaryResponse implements Serializable {

    private QuotaAccountResponse account;
    private List<QuotaFlowDTO> flows = new ArrayList<>();
    private UserMembershipDTO membership;
    private BillingPolicyDTO billingPolicy;
}
