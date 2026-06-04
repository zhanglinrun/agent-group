package com.linrun.domain.account.service;

import com.linrun.api.dto.LoginRequest;
import com.linrun.api.dto.LoginResponse;
import com.linrun.api.dto.RegisterRequest;
import com.linrun.api.dto.UserProfileResponse;
import com.linrun.domain.account.adapter.UserAccountRepository;
import com.linrun.domain.account.adapter.UserQuotaRepository;
import com.linrun.domain.account.model.UserAccount;
import com.linrun.domain.account.model.UserLoginSession;
import com.linrun.domain.account.model.UserQuotaAccount;
import com.linrun.types.exception.AppException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserAccountService {

    private static final String DEFAULT_ROLE = "USER";
    private static final String ENABLED = "ENABLED";
    private static final String SESSION_ACTIVE = "ACTIVE";
    private static final int SESSION_EXPIRE_DAYS = 7;
    private static final String BCRYPT_HASH_PREFIX = "$2";
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private final UserAccountRepository userAccountRepository;
    private final UserQuotaRepository userQuotaRepository;
    private final boolean demoUserEnabled;

    public UserAccountService(UserAccountRepository userAccountRepository,
                              UserQuotaRepository userQuotaRepository,
                              @Value("${agent.group.security.demo-user-enabled:false}") boolean demoUserEnabled) {
        this.userAccountRepository = userAccountRepository;
        this.userQuotaRepository = userQuotaRepository;
        this.demoUserEnabled = demoUserEnabled;
    }

    @Transactional(rollbackFor = Exception.class)
    public LoginResponse register(RegisterRequest request) {
        validateRegister(request);
        String username = request.getUsername().trim();
        userAccountRepository.queryByUsername(username).ifPresent(user -> {
            throw new AppException("AUTH_0003", "账号已存在");
        });

        UserAccount user = new UserAccount();
        user.setUserId(nextNo("U"));
        user.setUsername(username);
        user.setPasswordSalt("");
        user.setPasswordHash(hashPassword(request.getPassword()));
        user.setNickname(StringUtils.hasText(request.getNickname()) ? request.getNickname().trim() : username);
        user.setEmail(StringUtils.hasText(request.getEmail()) ? request.getEmail().trim() : "");
        user.setRole(DEFAULT_ROLE);
        user.setStatus(ENABLED);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(user.getCreateTime());
        userAccountRepository.saveUser(user);
        userQuotaRepository.createAccountIfAbsent(user.getUserId());
        return createSessionResponse(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public LoginResponse login(LoginRequest request) {
        validateLogin(request);
        UserAccount user = userAccountRepository.queryByUsername(request.getUsername().trim())
                .orElseGet(() -> createDefaultDemoUserIfNeeded(request));
        if (!ENABLED.equals(user.getStatus())) {
            throw new AppException("AUTH_0004", "账号已被禁用");
        }
        if (!matchesPassword(request.getPassword(), user)) {
            throw new AppException("AUTH_0005", "账号或密码不正确");
        }
        userQuotaRepository.createAccountIfAbsent(user.getUserId());
        return createSessionResponse(user);
    }

    public UserProfileResponse profile(String token) {
        UserAccount user = requireUserByToken(token);
        UserQuotaAccount account = userQuotaRepository.queryAccount(user.getUserId())
                .orElseGet(() -> emptyQuota(user.getUserId()));
        UserProfileResponse response = new UserProfileResponse();
        response.setUserId(user.getUserId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole());
        response.setStatus(user.getStatus());
        response.setQuotaBalance(account.getQuotaBalance());
        response.setFrozenQuota(account.getFrozenQuota());
        response.setUsedQuota(account.getUsedQuota());
        return response;
    }

    public UserAccount requireUserByToken(String token) {
        if (!StringUtils.hasText(token)) {
            throw new AppException("AUTH_0001", "请先登录");
        }
        UserLoginSession session = userAccountRepository.querySessionByToken(cleanToken(token))
                .orElseThrow(() -> new AppException("AUTH_0001", "登录已失效，请重新登录"));
        if (!SESSION_ACTIVE.equals(session.getStatus()) || session.getExpireTime() == null
                || session.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new AppException("AUTH_0001", "登录已失效，请重新登录");
        }
        return userAccountRepository.queryByUserId(session.getUserId())
                .orElseThrow(() -> new AppException("AUTH_0006", "用户不存在"));
    }

    public String resolveUserId(String token, String fallbackUserId) {
        if (StringUtils.hasText(token)) {
            return requireUserByToken(token).getUserId();
        }
        if (StringUtils.hasText(fallbackUserId)) {
            return fallbackUserId;
        }
        throw new AppException("AUTH_0001", "请先登录");
    }

    @Transactional(rollbackFor = Exception.class)
    public void logout(String token) {
        if (StringUtils.hasText(token)) {
            userAccountRepository.invalidSession(cleanToken(token));
        }
    }

    private LoginResponse createSessionResponse(UserAccount user) {
        UserLoginSession session = new UserLoginSession();
        session.setToken(randomToken());
        session.setUserId(user.getUserId());
        session.setStatus(SESSION_ACTIVE);
        session.setCreateTime(LocalDateTime.now());
        session.setExpireTime(session.getCreateTime().plusDays(SESSION_EXPIRE_DAYS));
        userAccountRepository.saveSession(session);

        UserQuotaAccount account = userQuotaRepository.queryAccount(user.getUserId())
                .orElseGet(() -> emptyQuota(user.getUserId()));
        LoginResponse response = new LoginResponse();
        response.setToken(session.getToken());
        response.setExpireTime(session.getExpireTime());
        response.setUserId(user.getUserId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setRole(user.getRole());
        response.setQuotaBalance(account.getQuotaBalance());
        return response;
    }

    private UserAccount createDefaultDemoUserIfNeeded(LoginRequest request) {
        String username = request.getUsername().trim();
        if (!demoUserEnabled || !"demo".equalsIgnoreCase(username) || !"123456".equals(request.getPassword())) {
            throw new AppException("AUTH_0005", "账号或密码不正确");
        }
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername("demo");
        registerRequest.setPassword("123456");
        registerRequest.setNickname("演示用户");
        registerRequest.setEmail("demo@example.com");
        register(registerRequest);
        return userAccountRepository.queryByUsername("demo")
                .orElseThrow(() -> new AppException("AUTH_0006", "演示用户初始化失败"));
    }

    private UserQuotaAccount emptyQuota(String userId) {
        UserQuotaAccount account = new UserQuotaAccount();
        account.setUserId(userId);
        return account;
    }

    private void validateRegister(RegisterRequest request) {
        if (request == null || !StringUtils.hasText(request.getUsername())) {
            throw new AppException("0001", "账号不能为空");
        }
        if (!StringUtils.hasText(request.getPassword()) || request.getPassword().length() < 6) {
            throw new AppException("0001", "密码长度不能少于 6 位");
        }
    }

    private void validateLogin(LoginRequest request) {
        if (request == null || !StringUtils.hasText(request.getUsername()) || !StringUtils.hasText(request.getPassword())) {
            throw new AppException("0001", "账号和密码不能为空");
        }
    }

    private String hashPassword(String password) {
        return PASSWORD_ENCODER.encode(safe(password));
    }

    private boolean matchesPassword(String password, UserAccount user) {
        String passwordHash = safe(user.getPasswordHash());
        if (passwordHash.startsWith(BCRYPT_HASH_PREFIX)) {
            return PASSWORD_ENCODER.matches(safe(password), passwordHash);
        }
        return legacyHashPassword(password, user.getPasswordSalt()).equals(passwordHash);
    }

    private String legacyHashPassword(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((safe(salt) + ":" + safe(password)).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new AppException("AUTH_0007", "密码摘要算法不可用");
        }
    }

    private String cleanToken(String token) {
        String value = token.trim();
        return value.regionMatches(true, 0, "Bearer ", 0, 7) ? value.substring(7).trim() : value;
    }

    private String nextNo(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private String randomToken() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }

    private String safe(String value) {
        return Optional.ofNullable(value).orElse("");
    }
}
