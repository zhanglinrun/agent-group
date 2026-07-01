package com.linrun.domain.account.service;

import com.linrun.api.dto.LoginRequest;
import com.linrun.api.dto.LoginResponse;
import com.linrun.api.dto.RegisterRequest;
import com.linrun.domain.account.adapter.UserAccountRepository;
import com.linrun.domain.quota.adapter.UserQuotaRepository;
import com.linrun.domain.account.model.ModelUsageRecord;
import com.linrun.domain.account.model.UserAccount;
import com.linrun.domain.account.model.UserLoginSession;
import com.linrun.domain.account.model.UserMembershipAccount;
import com.linrun.domain.account.model.UserModelConfig;
import com.linrun.domain.quota.model.UserQuotaAccount;
import com.linrun.domain.quota.model.UserQuotaFlow;
import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserAccountServiceTest {

    @Test
    void demoLoginCreatesDefaultUserAndReturnsDocAiCompatiblePayload() {
        InMemoryUserAccountRepository accountRepository = new InMemoryUserAccountRepository();
        InMemoryUserQuotaRepository quotaRepository = new InMemoryUserQuotaRepository();
        UserAccountService service = new UserAccountService(accountRepository, quotaRepository, true);

        LoginResponse response = service.login(loginRequest("DEMO", "123456"));
        UserAccount tokenUser = service.requireUserByToken("Bearer " + response.getToken());

        assertNotNull(response.getToken());
        assertEquals(response.getToken(), response.getAccessToken());
        assertEquals("", response.getRefreshToken());
        assertEquals("demo", response.getUsername());
        assertEquals("演示用户", response.getNickname());
        assertEquals("USER", response.getRole());
        assertNotNull(response.getUser());
        assertEquals(response.getUserId(), response.getUser().getUserId());
        assertEquals("demo", response.getUser().getUsername());
        assertEquals(BigDecimal.ZERO, response.getQuotaBalance());
        assertEquals(response.getUserId(), tokenUser.getUserId());
        assertTrue(quotaRepository.hasAccount(response.getUserId()));
        assertEquals(1, accountRepository.sessionCount());
        assertTrue(accountRepository.lastSessionToken().startsWith("sha256:"));
        assertNotEquals(response.getToken(), accountRepository.lastSessionToken());
    }

    @Test
    void demoLoginDisabledDoesNotCreateDefaultUser() {
        InMemoryUserAccountRepository accountRepository = new InMemoryUserAccountRepository();
        UserAccountService service = new UserAccountService(accountRepository, new InMemoryUserQuotaRepository(), false);

        AppException exception = assertThrows(AppException.class, () -> service.login(loginRequest("demo", "123456")));

        assertEquals("AUTH_0005", exception.getCode());
        assertFalse(accountRepository.queryByUsername("demo").isPresent());
        assertEquals(0, accountRepository.sessionCount());
    }

    @Test
    void registerRejectsInvalidEmailWhenProvided() {
        UserAccountService service = new UserAccountService(
                new InMemoryUserAccountRepository(),
                new InMemoryUserQuotaRepository(),
                true);
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setPassword("123456");
        request.setEmail("not-an-email");

        AppException exception = assertThrows(AppException.class, () -> service.register(request));

        assertEquals("0001", exception.getCode());
        assertEquals("邮箱格式不正确", exception.getMessage());
    }

    private static LoginRequest loginRequest(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }

    private static class InMemoryUserAccountRepository implements UserAccountRepository {
        private final Map<String, UserAccount> usersByUsername = new HashMap<>();
        private final Map<String, UserAccount> usersByUserId = new HashMap<>();
        private final Map<String, UserLoginSession> sessionsByToken = new HashMap<>();
        private String lastSessionToken = "";

        @Override
        public void saveUser(UserAccount userAccount) {
            usersByUsername.put(usernameKey(userAccount.getUsername()), userAccount);
            usersByUserId.put(userAccount.getUserId(), userAccount);
        }

        @Override
        public Optional<UserAccount> queryByUsername(String username) {
            return Optional.ofNullable(usersByUsername.get(usernameKey(username)));
        }

        @Override
        public Optional<UserAccount> queryByUserId(String userId) {
            return Optional.ofNullable(usersByUserId.get(userId));
        }

        @Override
        public void saveSession(UserLoginSession session) {
            sessionsByToken.put(session.getToken(), session);
            lastSessionToken = session.getToken();
        }

        @Override
        public Optional<UserLoginSession> querySessionByToken(String token) {
            return Optional.ofNullable(sessionsByToken.get(token));
        }

        @Override
        public void invalidSession(String token) {
            Optional.ofNullable(sessionsByToken.get(token)).ifPresent(session -> session.setStatus("INVALID"));
        }

        int sessionCount() {
            return sessionsByToken.size();
        }

        String lastSessionToken() {
            return lastSessionToken;
        }

        private String usernameKey(String username) {
            return String.valueOf(username).trim().toLowerCase(Locale.ROOT);
        }
    }

    private static class InMemoryUserQuotaRepository implements UserQuotaRepository {
        private final Map<String, UserQuotaAccount> accounts = new HashMap<>();
        private UserMembershipAccount membership;
        private UserModelConfig modelConfig;

        @Override
        public void createAccountIfAbsent(String userId) {
            accounts.computeIfAbsent(userId, key -> {
                UserQuotaAccount account = new UserQuotaAccount();
                account.setUserId(key);
                return account;
            });
        }

        @Override
        public Optional<UserQuotaAccount> queryAccount(String userId) {
            return Optional.ofNullable(accounts.get(userId));
        }

        @Override
        public int increaseQuota(String userId, BigDecimal amount) {
            createAccountIfAbsent(userId);
            UserQuotaAccount account = accounts.get(userId);
            account.setQuotaBalance(account.getQuotaBalance().add(amount));
            return 1;
        }

        @Override
        public int decreaseQuota(String userId, BigDecimal amount) {
            createAccountIfAbsent(userId);
            UserQuotaAccount account = accounts.get(userId);
            if (account.getQuotaBalance().compareTo(amount) < 0) {
                return 0;
            }
            account.setQuotaBalance(account.getQuotaBalance().subtract(amount));
            account.setUsedQuota(account.getUsedQuota().add(amount));
            return 1;
        }

        @Override
        public int decreaseQuotaAllowNegative(String userId, BigDecimal amount) {
            createAccountIfAbsent(userId);
            UserQuotaAccount account = accounts.get(userId);
            account.setQuotaBalance(account.getQuotaBalance().subtract(amount));
            account.setUsedQuota(account.getUsedQuota().add(amount));
            return 1;
        }

        @Override
        public void saveFlow(UserQuotaFlow flow) {
        }

        @Override
        public Optional<UserQuotaFlow> queryFlow(String userId, String flowType, String bizId) {
            return Optional.empty();
        }

        @Override
        public List<UserQuotaFlow> queryRecentFlows(String userId, int limit) {
            return List.of();
        }

        @Override
        public void saveUsage(ModelUsageRecord usageRecord) {
        }

        @Override
        public Optional<UserMembershipAccount> queryMembership(String userId) {
            return Optional.ofNullable(membership);
        }

        @Override
        public void upsertMembership(UserMembershipAccount membership) {
            this.membership = membership;
        }

        @Override
        public int decreaseMembershipQuota(String userId, BigDecimal amount) {
            return 0;
        }

        @Override
        public Optional<UserModelConfig> queryModelConfig(String userId) {
            return Optional.ofNullable(modelConfig);
        }

        @Override
        public void upsertModelConfig(UserModelConfig modelConfig) {
            this.modelConfig = modelConfig;
        }

        boolean hasAccount(String userId) {
            return accounts.containsKey(userId);
        }
    }
}
