package com.linrun.infrastructure.account.repository;

import com.linrun.domain.account.adapter.UserAccountRepository;
import com.linrun.domain.account.model.UserAccount;
import com.linrun.domain.account.model.UserLoginSession;
import com.linrun.infrastructure.account.converter.AccountPOConverter;
import com.linrun.infrastructure.dao.IUserAccountDao;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MyBatisUserAccountRepository implements UserAccountRepository {

    private final IUserAccountDao userAccountDao;

    public MyBatisUserAccountRepository(IUserAccountDao userAccountDao) {
        this.userAccountDao = userAccountDao;
    }

    @Override
    public void saveUser(UserAccount userAccount) {
        userAccountDao.insertUser(AccountPOConverter.toPO(userAccount));
    }

    @Override
    public Optional<UserAccount> queryByUsername(String username) {
        return Optional.ofNullable(AccountPOConverter.toEntity(userAccountDao.queryByUsername(username)));
    }

    @Override
    public Optional<UserAccount> queryByUserId(String userId) {
        return Optional.ofNullable(AccountPOConverter.toEntity(userAccountDao.queryByUserId(userId)));
    }

    @Override
    public void saveSession(UserLoginSession session) {
        userAccountDao.insertSession(AccountPOConverter.toPO(session));
    }

    @Override
    public Optional<UserLoginSession> querySessionByToken(String token) {
        return Optional.ofNullable(AccountPOConverter.toEntity(userAccountDao.querySessionByToken(token)));
    }

    @Override
    public void invalidSession(String token) {
        userAccountDao.invalidSession(token);
    }
}















