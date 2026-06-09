package com.linrun.infrastructure.account.repository;

import com.linrun.domain.account.adapter.UserQuotaRepository;
import com.linrun.domain.account.model.ModelUsageRecord;
import com.linrun.domain.account.model.UserMembershipAccount;
import com.linrun.domain.account.model.UserModelConfig;
import com.linrun.domain.account.model.UserQuotaAccount;
import com.linrun.domain.account.model.UserQuotaFlow;
import com.linrun.infrastructure.account.converter.AccountPOConverter;
import com.linrun.infrastructure.dao.IUserQuotaDao;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisUserQuotaRepository implements UserQuotaRepository {

    private final IUserQuotaDao userQuotaDao;

    public MyBatisUserQuotaRepository(IUserQuotaDao userQuotaDao) {
        this.userQuotaDao = userQuotaDao;
    }

    @Override
    public void createAccountIfAbsent(String userId) {
        userQuotaDao.createAccountIfAbsent(userId);
    }

    @Override
    public Optional<UserQuotaAccount> queryAccount(String userId) {
        return Optional.ofNullable(AccountPOConverter.toEntity(userQuotaDao.queryAccount(userId)));
    }

    @Override
    public int increaseQuota(String userId, BigDecimal amount) {
        return userQuotaDao.increaseQuota(userId, amount);
    }

    @Override
    public int decreaseQuota(String userId, BigDecimal amount) {
        return userQuotaDao.decreaseQuota(userId, amount);
    }

    @Override
    public int decreaseQuotaAllowNegative(String userId, BigDecimal amount) {
        return userQuotaDao.decreaseQuotaAllowNegative(userId, amount);
    }

    @Override
    public void saveFlow(UserQuotaFlow flow) {
        userQuotaDao.insertFlow(AccountPOConverter.toPO(flow));
    }

    @Override
    public Optional<UserQuotaFlow> queryFlow(String userId, String flowType, String bizId) {
        return Optional.ofNullable(AccountPOConverter.toEntity(userQuotaDao.queryFlow(userId, flowType, bizId)));
    }

    @Override
    public List<UserQuotaFlow> queryRecentFlows(String userId, int limit) {
        return AccountPOConverter.toQuotaFlows(userQuotaDao.queryRecentFlows(userId, limit));
    }

    @Override
    public void saveUsage(ModelUsageRecord usageRecord) {
        userQuotaDao.insertUsage(AccountPOConverter.toPO(usageRecord));
    }

    @Override
    public Optional<UserMembershipAccount> queryMembership(String userId) {
        return Optional.ofNullable(AccountPOConverter.toEntity(userQuotaDao.queryMembership(userId)));
    }

    @Override
    public void upsertMembership(UserMembershipAccount membership) {
        userQuotaDao.upsertMembership(AccountPOConverter.toPO(membership));
    }

    @Override
    public int decreaseMembershipQuota(String userId, BigDecimal amount) {
        return userQuotaDao.decreaseMembershipQuota(userId, amount);
    }

    @Override
    public Optional<UserModelConfig> queryModelConfig(String userId) {
        return Optional.ofNullable(AccountPOConverter.toEntity(userQuotaDao.queryModelConfig(userId)));
    }

    @Override
    public void upsertModelConfig(UserModelConfig modelConfig) {
        userQuotaDao.upsertModelConfig(AccountPOConverter.toPO(modelConfig));
    }
}















