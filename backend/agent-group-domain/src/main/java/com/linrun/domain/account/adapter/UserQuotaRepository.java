package com.linrun.domain.account.adapter;

import com.linrun.domain.account.model.ModelUsageRecord;
import com.linrun.domain.account.model.UserMembershipAccount;
import com.linrun.domain.account.model.UserModelConfig;
import com.linrun.domain.account.model.UserQuotaAccount;
import com.linrun.domain.account.model.UserQuotaFlow;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface UserQuotaRepository {

    void createAccountIfAbsent(String userId);

    Optional<UserQuotaAccount> queryAccount(String userId);

    int increaseQuota(String userId, BigDecimal amount);
    int decreaseQuota(String userId, BigDecimal amount);
    int decreaseQuotaAllowNegative(String userId, BigDecimal amount);

    void saveFlow(UserQuotaFlow flow);
    Optional<UserQuotaFlow> queryFlow(String userId, String flowType, String bizId);
    List<UserQuotaFlow> queryRecentFlows(String userId, int limit);

    void saveUsage(ModelUsageRecord usageRecord);

    Optional<UserMembershipAccount> queryMembership(String userId);
    void upsertMembership(UserMembershipAccount membership);
    int decreaseMembershipQuota(String userId, BigDecimal amount);

    Optional<UserModelConfig> queryModelConfig(String userId);
    void upsertModelConfig(UserModelConfig modelConfig);
}
