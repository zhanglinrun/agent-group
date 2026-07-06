package com.linrun.infrastructure.dao;

import com.linrun.infrastructure.po.ModelUsageRecordPO;
import com.linrun.infrastructure.po.UserMembershipAccountPO;
import com.linrun.infrastructure.po.UserModelConfigPO;
import com.linrun.infrastructure.po.UserQuotaAccountPO;
import com.linrun.infrastructure.po.UserQuotaFlowPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface IUserQuotaDao {

    void createAccountIfAbsent(@Param("userId") String userId);

    UserQuotaAccountPO queryAccount(@Param("userId") String userId);

    UserQuotaAccountPO queryAccountForUpdate(@Param("userId") String userId);

    int increaseQuota(@Param("userId") String userId, @Param("amount") BigDecimal amount);

    int decreaseQuota(@Param("userId") String userId, @Param("amount") BigDecimal amount);

    int decreaseQuotaAllowNegative(@Param("userId") String userId, @Param("amount") BigDecimal amount);

    void insertFlow(UserQuotaFlowPO flow);

    UserQuotaFlowPO queryFlow(@Param("userId") String userId,
                              @Param("flowType") String flowType,
                              @Param("bizId") String bizId);

    List<UserQuotaFlowPO> queryRecentFlows(@Param("userId") String userId, @Param("limit") int limit);

    void insertUsage(ModelUsageRecordPO usageRecord);

    UserMembershipAccountPO queryMembership(@Param("userId") String userId);

    void upsertMembership(UserMembershipAccountPO membership);

    int decreaseMembershipQuota(@Param("userId") String userId, @Param("amount") BigDecimal amount);

    UserModelConfigPO queryModelConfig(@Param("userId") String userId);

    void upsertModelConfig(UserModelConfigPO modelConfig);
}















