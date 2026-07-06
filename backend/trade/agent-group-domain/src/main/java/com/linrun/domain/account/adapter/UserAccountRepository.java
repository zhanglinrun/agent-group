package com.linrun.domain.account.adapter;

import com.linrun.domain.account.model.UserAccount;
import com.linrun.domain.account.model.UserLoginSession;

import java.util.Optional;

public interface UserAccountRepository {

    void saveUser(UserAccount userAccount);

    Optional<UserAccount> queryByUsername(String username);
    Optional<UserAccount> queryByUserId(String userId);

    void saveSession(UserLoginSession session);
    Optional<UserLoginSession> querySessionByToken(String token);
    void invalidSession(String token);
}















